package emissary.util;

import emissary.core.Form;
import emissary.core.IBaseDataObject;
import emissary.id.WorkUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

public class DataUtil {
    private static final Logger logger = LoggerFactory.getLogger(DataUtil.class);
    private static final Pattern NL_REPL = Pattern.compile("[\n\r]+");

    /**
     * As {@link IBaseDataObject#pushCurrentForm(String)} but makes sure each form is not already on the stack first.
     * 
     * @param d data object to have form pushed into
     * @param forms that are pushed into current forms if not already present
     */
    public static void pushDedupedForms(final IBaseDataObject d, final Collection<String> forms) {
        for (final String f : forms) {
            pushDedupedForm(d, f);
        }
    }

    /**
     * As {@link IBaseDataObject#pushCurrentForm(String)} but makes sure each form is not already on the stack first.
     * 
     * @param d data object to have form pushed into
     * @param form that are pushed into current forms if not already present
     */
    public static void pushDedupedForm(final IBaseDataObject d, final String form) {
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
    public static boolean isNotEmpty(@Nullable final IBaseDataObject d) {
        return (d != null) && !isEmpty(d);
    }

    /**
     * Return true if the data slot is empty
     */
    public static boolean isEmpty(final IBaseDataObject d) {
        final byte[] data = d.data();
        return isEmpty(data);
    }

    /**
     * Return true if the data is null or empty
     * 
     * @param data array to check
     * @return true if data is null or devoid of real characters
     */
    public static boolean isEmpty(@Nullable final byte[] data) {
        return (data == null) || (data.length == 0);
    }

    /**
     * Return true if the data slot is empty
     */
    public static boolean isEmpty(final WorkUnit u) {
        final byte[] data = u.getData();
        return isEmpty(data);
    }

    /**
     * Convenience method to assign the current form and file type of a {@link IBaseDataObject} to "EMPTY_SESSION".
     * 
     * @param d the object to set as empty
     */
    public static void setEmptySession(final IBaseDataObject d) {
        d.setCurrentForm(Form.EMPTY);
        d.setFileTypeIfEmpty(Form.EMPTY);
    }

    /**
     * Escape a string so it is suitable for use in a CSV record
     */
    public static String csvescape(final String field) {
        final String SEP = ",";
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
     * Copy an array of metadata parameters from one data object to another
     * 
     * @param source to copy from
     * @param target to copy to
     * @param keys array of metadata keys to copy
     */
    public static void copyParams(final IBaseDataObject source, final IBaseDataObject target, final String[] keys) {
        for (final String k : keys) {
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
    public static void copyParams(final IBaseDataObject source, final IBaseDataObject target, final Collection<String> keys) {
        for (final String k : keys) {
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
    public static void copyParam(final IBaseDataObject source, final IBaseDataObject target, final String key) {
        final Object value = source.getParameter(key);
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
    public static void setCurrentFormAndFiletype(final IBaseDataObject dataobj, final String form) {
        dataobj.pushCurrentForm(form);
        dataobj.setFileType(form);
    }

    /** This class is not meant to be instantiated. */
    private DataUtil() {}
}
