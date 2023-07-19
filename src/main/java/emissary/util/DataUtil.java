package emissary.util;

import emissary.core.Form;
import emissary.core.IBaseDataObject;
import emissary.id.WorkUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

import static emissary.core.constants.Parameters.EVENT_DATE;
import static emissary.core.constants.Parameters.FILE_DATE;

public class DataUtil {
    private static final Logger logger = LoggerFactory.getLogger(DataUtil.class);
    private static final Pattern NL_REPL = Pattern.compile("[\n\r]+");

    /**
     * As {@link IBaseDataObject#pushCurrentForm(String)} but makes sure each form is not already on the stack first.
     * 
     * @param d data object to have form pushed into
     * @param forms that are pushed into current forms if not already present
     */
    public static void pushDedupedForms(IBaseDataObject d, Collection<String> forms) {
        for (String f : forms) {
            pushDedupedForm(d, f);
        }
    }

    /**
     * As {@link IBaseDataObject#pushCurrentForm(String)} but makes sure each form is not already on the stack first.
     * 
     * @param d data object to have form pushed into
     * @param form that are pushed into current forms if not already present
     */
    public static void pushDedupedForm(IBaseDataObject d, String form) {
        if (d.searchCurrentForm(form) == -1) {
            d.pushCurrentForm(form);
        }
    }

    /**
     * Check that a data object is neither null nor empty.
     *
     * @param d the data object
     * @return true if d is not null and not empty
     */
    public static boolean isNotEmpty(@Nullable IBaseDataObject d) {
        return (d != null) && !isEmpty(d);
    }

    /**
     * Return true if the data slot is empty or just whitespace and/or control chars
     */
    public static boolean isEmpty(IBaseDataObject d) {
        byte[] data = d.data();
        return isEmpty(data);
    }

    /**
     * Return true if the data is empty or consists only of whitespace or control characters
     * 
     * @param data array to check
     * @return true if data is null or devoid of real characters
     */
    public static boolean isEmpty(@Nullable byte[] data) {
        return (data == null) || (data.length == 0);
    }

    /**
     * Return true if the data slot is empty or just one whitespace character
     */
    public static boolean isEmpty(WorkUnit u) {
        byte[] data = u.getData();
        return isEmpty(data);
    }

    /**
     * Convenience method to assign the current form and file type of a {@link IBaseDataObject} to "EMPTY_SESSION".
     * 
     * @param d the object to set as empty
     */
    public static void setEmptySession(IBaseDataObject d) {
        d.setCurrentForm(Form.EMPTY);
        d.setFileTypeIfEmpty(Form.EMPTY);
    }

    /**
     * Escape a string so it is suitable for use in a CSV record
     */
    public static String csvescape(String field) {
        String SEP = ",";
        String s = field;
        if ((s != null) && (s.contains("\n") || s.contains("\r"))) {
            s = NL_REPL.matcher(s).replaceAll(" ");
        }

        if ((s != null) && (s.contains(SEP) || s.indexOf('"') > -1)) {
            s = "\"" + s.replace("\"", "\"\"") + "\""; // escape quotes
        }

        return s;
    }

    /**
     * Get the event or collection date from a data object by checking the EventDate and FILE_DATE parameters. If both are
     * missing default to today.
     * 
     * @param payload data object to examine
     * @return event date
     */
    @Deprecated
    public static Calendar getEventDate(IBaseDataObject payload) {
        String evDate = payload.getStringParameter(EVENT_DATE);
        if (evDate == null) {
            evDate = payload.getStringParameter(FILE_DATE);
        }
        Date eventDate = null;
        if (evDate != null) {
            try {
                eventDate = emissary.util.TimeUtil.getDateFromISO8601(evDate);
            } catch (DateTimeParseException e) {
                logger.debug("Could not parse event date from payload, default to curren time", e);
                eventDate = new Date();
            }
        } else {
            eventDate = new Date();
        }
        return getCal(eventDate);
    }

    /**
     * Get a Calendar object from a Date Object with the TZ set to UTC
     * 
     * @param eventDate the date object to convert
     * @return specified time UTC calendar
     */
    @Deprecated
    public static Calendar getCal(Date eventDate) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTime(eventDate);
        return cal;
    }

    /**
     * Merge two maps with semicolon delimited lists as values
     */
    @Deprecated
    public static void merge(Map<String, String> info, Map<String, String> recordInfo) {
        for (Map.Entry<String, String> entry : recordInfo.entrySet()) {
            addInfo(info, entry.getKey(), entry.getValue());
        }
    }

    /**
     * Merge a key-value pair into a map, where the values are semicolon delimited lists
     * 
     * @param info the map to merge into
     * @param key the newly arriving key
     * @param value the newly arriving value
     */
    @Deprecated
    public static void addInfo(Map<String, String> info, String key, String value) {
        if (info.containsKey(key)) {
            Set<String> existingValues = split(info.get(key));
            existingValues.addAll(split(value));
            info.put(key, join(existingValues));
        } else {
            info.put(key, value);
        }
    }

    private static final Pattern SEMICOLON_SPLIT = Pattern.compile(IBaseDataObject.DEFAULT_PARAM_SEPARATOR);

    /**
     * Convert a semicolon delmited list as a set of strings
     * 
     * @param values semicolon delimited list
     * @return strings in list
     */
    @Deprecated
    public static Set<String> split(String values) {
        Set<String> valuesSet = new HashSet<>();
        String[] items = SEMICOLON_SPLIT.split(values);
        valuesSet.addAll(Arrays.asList(items));
        return valuesSet;
    }

    /**
     * Join a collection with a delimiter
     * 
     * @param values the collection to join
     * @param delim the string delimiter to use
     * @param sort true if the output should be sorted
     */
    @Deprecated
    public static String join(Collection<String> values, String delim, boolean sort) {
        Collection<String> tojoin;
        if (sort) {
            tojoin = new TreeSet<>();
            tojoin.addAll(values);
        } else {
            tojoin = values;
        }

        StringBuilder sb = new StringBuilder();
        for (String v : tojoin) {
            if (sb.length() > 0) {
                sb.append(delim);
            }
            sb.append(v);
        }
        return sb.toString();
    }

    /**
     * Join a collection with a delimiter, output in sorted order
     * 
     * @param values the collection to join
     * @param delim the string delimiter to use
     */
    @Deprecated
    public static String join(Collection<String> values, String delim) {
        return join(values, delim, true);
    }

    /**
     * Join a collection with a semi-colon delimiter, output in sorted order
     * 
     * @param values the collection to join
     */
    @Deprecated
    public static String join(Collection<String> values) {
        return join(values, IBaseDataObject.DEFAULT_PARAM_SEPARATOR, true);
    }

    /**
     * Given a map of key-values, merge each into {@link IBaseDataObject} checking for duplication
     * 
     * @param parent IBaseDataObject to merge String parameter with
     * @param map key-value pairs to merge
     */
    @Deprecated
    public static void mergeMap(IBaseDataObject parent, Map<String, String> map) {
        for (Map.Entry<String, String> e : map.entrySet()) {
            mergeParameter(parent, e.getKey(), e.getValue());
        }
    }

    /**
     * Assume key is name of String value parameter. Check wither parent already has string value in a delim-separated
     * string. If so do nothing, otherwise, add delim + value
     * 
     * @param parent IBaseDataObject to mere String parameter with
     * @param key name of String parameter
     * @param val String to append if not already in parents values
     */
    @Deprecated
    public static void mergeParameter(IBaseDataObject parent, String key, String val) {
        String param;
        if (parent.hasParameter(key)) {
            param = parent.getStringParameter(key);
            Set<String> oldvals = split(param);
            Set<String> newvals = split(val);
            newvals.addAll(oldvals);
            param = join(newvals);
        } else {
            param = val;
        }
        parent.setParameter(key, param);
    }

    /**
     * Copy an array of metadata parameters from one data object to another
     * 
     * @param source to copy from
     * @param target to copy to
     * @param keys array of metadata keys to copy
     */
    public static void copyParams(IBaseDataObject source, IBaseDataObject target, String[] keys) {
        for (String k : keys) {
            copyParam(source, target, k);
        }
    }

    /**
     * Copy an collection of metadata parameters from one data object to another
     * 
     * @param source to copy from
     * @param target to copy to
     * @param keys collection of metadata keys to copy
     */
    public static void copyParams(IBaseDataObject source, IBaseDataObject target, Collection<String> keys) {
        for (String k : keys) {
            copyParam(source, target, k);
        }
    }

    /**
     * Copy a metadata item from one payload to another
     * 
     * @param source to copy from
     * @param target to copy to
     * @param key metadata key to copy
     */
    public static void copyParam(IBaseDataObject source, IBaseDataObject target, String key) {
        Object value = source.getParameter(key);
        if (value != null) {
            target.setParameter(key, value);
        }
    }

    /**
     * Convenience method for using same value as currentForm (pushed) and filetype for a {@link IBaseDataObject}
     * 
     * @param dataobj to be set
     * @param form added as current form and filetype for dataobj
     */
    public static void setCurrentFormAndFiletype(IBaseDataObject dataobj, String form) {
        dataobj.pushCurrentForm(form);
        dataobj.setFileType(form);
    }

    /** This class is not meant to be instantiated. */
    private DataUtil() {}
}
