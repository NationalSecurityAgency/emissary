package emissary.util.magic;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class MagicNumberFactory {

    private static Log log = LogFactory.getLog(MagicNumberFactory.class);

    private static Map<String, Integer> typeMap = null;
    public static final String EMPTYSTRING = "";
    public static final String ENTRY_NOT_NULL_RULE = "Entry cannot be null";
    public static final String ENTRY_4COLUMN_RULE = "Entry must have four tab separated columns";

    public static final String UNSUPPORTED_DATATYPE_MSG_SEARCH = "Data Type 'search/N' not supported - e.g. search/1";
    public static final String UNSUPPORTED_DATATYPE_MSG_REGEX = "Data Type 'regex' not supported";
    public static final String UNSUPORTED_DATATYPE_MSG_UNSIGNED = "Signed Data Types unsupported - e.g. UBELONG";
    public static final List<String> IGNORABLE_DATATYPE_MSGS = Arrays.asList(UNSUPORTED_DATATYPE_MSG_UNSIGNED,
            UNSUPPORTED_DATATYPE_MSG_REGEX, UNSUPPORTED_DATATYPE_MSG_SEARCH);

    private MagicNumberFactory() {}


    /**
     * Public method to parse a byte array representing the magic file into a list containing MagicNumber objects which are
     * also nested with continuations as child MagicNumber instances.
     *
     * @param configData the byte[] representing the magic file
     * @param zeroDepthErrorList logs errors with zero depth entries
     * @param continuationErrorMap logs errors with continuations - these are entries with depths &gt; 0
     * @return a {@link List}.
     */
    public static List<MagicNumber> buildMagicNumberList(byte[] configData, List<String> zeroDepthErrorList,
            Map<String, List<String>> continuationErrorMap) {
        // preserve the old way
        return buildMagicNumberList(configData, zeroDepthErrorList, continuationErrorMap, false);
    }

    /**
     * Public method to parse a byte array representing the magic file into a list containing MagicNumber objects which are
     * also nested with continuations as child MagicNumber instances.
     *
     * @param configData the byte[] representing the magic file
     * @param zeroDepthErrorList logs errors with zero depth entries
     * @param continuationErrorMap logs errors with continuations - these are entries with depths &gt; 0
     * @param swallowParseException boolean whether to swallow or propogate ParseExceptions that are IGNORABLE_DATATYPE_MSGS
     * @return a {@link List}.
     */
    public static List<MagicNumber> buildMagicNumberList(byte[] configData, List<String> zeroDepthErrorList,
            Map<String, List<String>> continuationErrorMap, boolean swallowParseException) {

        List<MagicNumber> magicNumberList = new ArrayList<MagicNumber>();
        BufferedReader reader = null;
        MagicNumber finger = null;
        int currentDepth = -1;
        List<MagicNumber> extensions = new ArrayList<MagicNumber>();
        try {
            byte[] magicFileData = configData;
            reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(magicFileData), MagicNumber.DEFAULT_CHARSET));
            String s;
            int counter = 0;
            while ((s = reader.readLine()) != null) {
                counter++;
                if (s == null || s.length() == 0 || s.charAt(0) == '#')
                    continue;
                int depth = getEntryDepth(s);
                if (depth < 0)
                    continue;
                try {
                    if (depth == 0 && extensions.size() > 0) {
                        if (finger == null) {
                            extensions = null;
                            extensions = new ArrayList<MagicNumber>();
                        } else {
                            addExtensionsLayer(extensions, finger);
                            extensions = null;
                            extensions = new ArrayList<MagicNumber>();
                            finger = null;
                        }
                    }
                    if (depth == 0) {
                        MagicNumber item = parseAndStore(magicNumberList, s, swallowParseException);
                        if (item != null) {
                            finger = item;
                            currentDepth = -1;
                        } else {
                            finger = null;
                        }
                    } else if (depth > 0) {

                        if (finger == null)
                            continue;
                        if (currentDepth < 0)
                            currentDepth = depth;
                        if (currentDepth == depth) {
                            parseAndStore(extensions, s, swallowParseException);
                        } else if (currentDepth < depth) {
                            if (extensions.size() == 0) {
                                finger = null;
                                currentDepth = -1;
                                continue;
                            }
                            currentDepth = depth;
                            addExtensionsLayer(extensions, finger);
                            extensions = null;
                            extensions = new ArrayList<MagicNumber>();
                            parseAndStore(extensions, s, swallowParseException);
                        }
                    }
                } catch (Exception e) {

                    if (continuationErrorMap == null || zeroDepthErrorList == null)
                        continue;

                    if (swallowParseException &&
                            (e.getClass() == ParseException.class) &&
                            IGNORABLE_DATATYPE_MSGS.contains(e.getMessage())) {
                        // swallow this cause said we don't care
                        continue;
                    }


                    if (depth > 0) {
                        MagicNumber mItem = magicNumberList.get(magicNumberList.size() - 1);
                        String signature = mItem.toString();
                        List<String> failedExtensions = continuationErrorMap.get(signature);
                        if (failedExtensions == null) {
                            failedExtensions = new ArrayList<String>();
                            continuationErrorMap.put(mItem.toString(), failedExtensions);
                        }
                        failedExtensions.add("[MAGIC LINE# " + counter + "] " + s);
                    } else if (depth == 0) {
                        zeroDepthErrorList.add("[MAGIC LINE# " + counter + "] " + s);
                    }
                }
            }
            if (finger != null && extensions.size() > 0)
                addExtensionsLayer(extensions, finger);
        } catch (IOException ioe) {
            log.error("Caught IOException on buildMagicNumberList (throwing a runtime exception): " + ioe.getMessage(), ioe);
            /** Doing all of this in memory - yes, one could erroneously use one of the IO objects but ... */
            throw new RuntimeException(ioe);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                    reader = null;
                }
            } catch (Exception e) {
            }
        }
        return magicNumberList;
    }

    /**
     * Private method for parsing entries and storing them into the target storage data structure which is a list.
     *
     *
     * @param entry the magic number String entry
     * @param storage a {@link List} where the MagicNumber instances will be placed
     * @param swallowParseException should we swallow Ignorable ParseException or bubble them up
     * @return the MagicNumber instance created
     * @throws Exception if one occurs while parsing the entry
     * @see #buildMagicNumber(java.lang.String, boolean)
     */
    private static MagicNumber parseAndStore(List<MagicNumber> storage, String entry, boolean swallowParseException) throws Exception {
        MagicNumber item = buildMagicNumber(entry, swallowParseException);
        if (item != null)
            storage.add(item);
        return item;
    }

    /**
     * Private method to store the list of MagicNumber instances and adds them a new layer of continuations in the target
     * MagicNumber
     *
     * @param target the MagicNumber instance acting as the parent for the continuations
     * @param extensions a {@link List} of continuations which are MagicNumber instances
     */
    private static void addExtensionsLayer(List<MagicNumber> extensions, MagicNumber target) {
        MagicNumber[] extensionArray = new MagicNumber[extensions.size()];
        int index = 0;
        for (MagicNumber m : extensions) {
            extensionArray[index++] = m;
        }
        target.addDependencyLayer(extensionArray);
    }

    /**
     * Parses a magic number entry and prepares a magic number item
     * 
     * @param entry line to parse
     */
    public static MagicNumber buildMagicNumber(String entry) throws ParseException {
        return buildMagicNumber(entry, false);
    }

    /**
     * Parses a magic number entry and prepares a magic number item
     * 
     * @param entry line to parse
     * @param swallowParseException should we swallow Ignorable ParseException or bubble them up
     */
    public static MagicNumber buildMagicNumber(String entry, boolean swallowParseException) throws ParseException {

        String[] columns = prepareEntry(entry);
        MagicNumber item = new MagicNumber();

        try {
            // column A parsing
            item.depth = getEntryDepth(columns[0]);
            item.offsetUnary = resolveOffsetUnary(columns, item);
            item.offset = resolveOffset(columns, item);
        } catch (Exception e) {
            // log.error ("original entry \t: " + entry);
            // log.error ("Error on column 0\t: " + columns[0], e);
            throw new ParseException("Error on column 0:" + columns[0] + ". " + e.getMessage());
        }
        try {
            // columb B parsing
            item.dataType = resolveDataType(columns, item);
            item.dataTypeLength = getDataTypeByteLength(columns, item);
            item.mask = resolveMask(columns, item);
        } catch (Exception e) {
            if (swallowParseException) {
                // This means you put TRUE in SWALLOW_IGNORABLE_EXCEPTIONS in a UnixFilePlace.cfg file
                // so let's log at debug level so you can hide these message easily
                log.debug("Warning unable to read column 1\t: " + columns[1] + " - " + e.getMessage());
            } else {
                log.error("original entry   \t: " + entry);
                log.error("Error on column 1\t: " + columns[1], e);
            }
            throw new ParseException("Parse Error on column 1:" + columns[1] + ". " + e.getMessage());
        }
        try {
            // column C parsing
            item.unaryOperator = resolveUnary(columns, item);
            item.value = resolveValue(columns, item);
            item.dataTypeLength = item.value.length;
        } catch (Exception e) {
            // log.error ("original entry \t: " + entry);
            // log.error ("Error on column 2\t: " + columns[2], e);
            throw new ParseException("Error on column 2:" + columns[2] + ". " + e.getMessage());
        }
        // column D parsing
        item.description = columns[3];
        return item;
    }

    /**
     * Tokenizes an entry into four columns by tab or space while recognizing escape sequences.
     */
    private static String[] tokenizeEntry(String entry) {
        int index = 0;
        String[] columns = new String[4];
        columns[0] = EMPTYSTRING;
        columns[1] = EMPTYSTRING;
        columns[2] = EMPTYSTRING;
        columns[3] = EMPTYSTRING;
        for (int i = 0; i < entry.length(); i++) {
            char c = entry.charAt(i);
            if (c == '\\' && i != (entry.length() - 1) && entry.charAt(i + 1) == ' ') {
                columns[index] += ' ';
                i++;
            } else if (c == ' ' || c == '\t') {
                while (entry.length() > (i + 1)) {
                    if (entry.charAt(i + 1) == ' ' || entry.charAt(i + 1) == '\t')
                        i++;
                    else
                        break;
                }
                index++;
            } else {
                columns[index] += c;
            }
            if (index == 3) {
                if (entry.length() > (i + 1))
                    columns[index] = entry.substring(i + 1);
                break;
            }
        }
        return columns;
    }

    /**
     * Corrects some known/common erroneous syntax errors
     */
    private static String[] prepareEntry(String entry) throws ParseException {
        if (entry == null)
            throw new ParseException(ENTRY_NOT_NULL_RULE);
        String subject = entry;
        int invalidOperatorIndex = subject.indexOf(" = ");
        if (invalidOperatorIndex > 0) {
            String tail = subject.length() > (invalidOperatorIndex + 3) ? ' ' + subject.substring(invalidOperatorIndex + 3) : "";
            subject = subject.substring(0, invalidOperatorIndex) + tail;
        }

        String[] columns = tokenizeEntry(subject);
        for (int count = 0; count < columns.length; count++) {
            if (count == 3 && columns[count].length() == 0 && !(columns[0].charAt(0) == '>')) {
                // columns[count] = NULL_DESCRIPTION;

            } else if (columns[count].length() == 0 && count < 3) {
                throw new ParseException(ENTRY_4COLUMN_RULE);
            }
        }
        return columns;
    }

    // -----------------------------------------------------------------------
    // COLUMN A: >&offsetValue
    // -----------------------------------------------------------------------
    private static int resolveOffset(String[] columns, MagicNumber item) throws ParseException {
        String entry = columns[0];
        if (item.depth > 0)
            entry = entry.substring(item.depth);
        if (entry.charAt(0) == '&')
            entry = entry.substring(1);
        else if (entry.charAt(0) == '(' && entry.charAt(entry.length() - 1) == ')')
            entry = entry.substring(1, entry.length() - 1);
        try {
            return MagicMath.stringToInt(entry);
        } catch (NumberFormatException e) {
            throw new ParseException("Malformatted offset value: " + entry);
        }
    }

    private static char resolveOffsetUnary(String[] columns, MagicNumber item) {
        if (columns[0].charAt(0) == '&')
            return '&';
        return (char) 0;
    }

    public static int getEntryDepth(String entry) {
        if (entry.length() == 0 || (entry.charAt(0) != '>' && !Character.isDigit(entry.charAt(0)))) {
            return -1;
        }
        int depth = 0;
        for (; depth < entry.length(); depth++) {
            if (entry.charAt(depth) != '>')
                break;
        }
        return depth;
    }

    // -----------------------------------------------------------------------
    // COLUMN B: BYTE&maskValue
    // -----------------------------------------------------------------------
    private static int resolveDataType(String[] columns, MagicNumber item) throws ParseException {
        initTypeMap();

        String subject = columns[1];
        if (subject.startsWith("search")) {
            throw new ParseException(UNSUPPORTED_DATATYPE_MSG_SEARCH);
        }
        if (subject.equals("regex")) {
            throw new ParseException(UNSUPPORTED_DATATYPE_MSG_REGEX);
        }
        if (subject.charAt(0) == 'u' || subject.charAt(0) == 'U') {
            throw new ParseException(UNSUPORTED_DATATYPE_MSG_UNSIGNED);
        }

        // parse out any masking
        int ix = subject.indexOf("&") > 0 ? subject.indexOf("&") : subject.indexOf("/");
        if (ix > 0)
            subject = columns[1].substring(0, ix);

        int dataTypeId = lookupDataType(subject);
        if (dataTypeId < 0)
            throw new ParseException("Unsupported Data Type: " + subject);
        return dataTypeId;
    }

    private static int lookupDataType(String arg) {
        int dataTypeIdInt = typeMap.get(arg.toUpperCase());
        switch (dataTypeIdInt) {
            case MagicNumber.TYPE_DATE:
                return -1;
            case MagicNumber.TYPE_BEDATE:
                return -1;
            case MagicNumber.TYPE_LEDATE:
                return -1;
            default:
                return dataTypeIdInt;
        }
    }

    private static byte[] resolveMask(String[] columns, MagicNumber item) {
        int ix = columns[1].indexOf("&");
        if (ix > 0) {
            byte[] maskValues = MagicMath.stringToByteArray(columns[1].substring(ix + 1));
            maskValues = MagicMath.setLength(maskValues, item.dataTypeLength);
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // COLUMN C: [UNARY_OPERATOR][Some value like 0x00]
    // -----------------------------------------------------------------------
    private static byte[] resolveValue(String[] columns, MagicNumber item) throws ParseException {
        String subject = columns[2];

        if (item.dataType == MagicNumber.TYPE_STRING && !(subject.length() == 1 && subject.charAt(0) == 'x')) {
            byte[] strVal = MagicMath.parseEscapedString(subject);
            item.dataTypeLength = strVal.length;
            return strVal;
        } else if (subject.length() == 1 && subject.charAt(0) == 'x') {
            item.substitute = true;
            return new byte[0];
        }

        int unaryLen = unaryPrefixLength(subject);
        if (unaryLen > 0)
            subject = subject.substring(unaryLen);
        if (subject.toUpperCase().endsWith("L"))
            subject = subject.substring(0, subject.length() - 1);

        byte[] valueArray = MagicMath.stringToByteArray(subject);
        valueArray = MagicMath.setLength(valueArray, item.dataTypeLength);

        if (item.mask != null)
            valueArray = MagicMath.mask(valueArray, item.mask);
        if (item.dataType == MagicNumber.TYPE_LELONG)
            MagicMath.longEndianSwap(valueArray, 0);
        else if (item.dataType == MagicNumber.TYPE_LESHORT)
            MagicMath.shortEndianSwap(valueArray, 0);
        return valueArray;
    }

    private static int unaryPrefixLength(String s) {

        if (s == null || s.length() == 0)
            return 0;

        char op = s.charAt(0);
        int len = s.length();
        if (!Character.isDigit(op)) {
            switch (op) {
                case MagicNumber.MAGICOPERATOR_AND:
                    return 1;
                case MagicNumber.MAGICOPERATOR_GTHAN:
                    return len > 1 && s.charAt(1) == MagicNumber.MAGICOPERATOR_AND ? 2 : 1;
                case MagicNumber.MAGICOPERATOR_LTHAN:
                    return len > 1 && s.charAt(1) == MagicNumber.MAGICOPERATOR_AND ? 2 : 1;
                case MagicNumber.MAGICOPERATOR_OR:
                    return 1;
                case MagicNumber.MAGICOPERATOR_BWAND:
                    return 1;
                case MagicNumber.MAGICOPERATOR_BWNOT:
                    return 1;
                case MagicNumber.MAGICOPERATOR_NOT:
                    return 1;
                default:
                    return 0;
            }
        }
        return 0;
    }

    private static int getDataTypeByteLength(String[] columns, MagicNumber item) {
        int dataTypeId = item.dataType;
        switch (dataTypeId) {
            case MagicNumber.TYPE_STRING:
                if (item.value == null)
                    return -1;
                else
                    return item.value.length;
            case MagicNumber.TYPE_BYTE:
                return 1;
            case MagicNumber.TYPE_SHORT:
                return 2;
            case MagicNumber.TYPE_BESHORT:
                return 2;
            case MagicNumber.TYPE_LESHORT:
                return 2;
            case MagicNumber.TYPE_LONG:
                return 4;
            case MagicNumber.TYPE_BELONG:
                return 4;
            case MagicNumber.TYPE_LELONG:
                return 4;
            case MagicNumber.TYPE_BEDATE:
                return 4;
            case MagicNumber.TYPE_LEDATE:
                return 4;
            default:
                return -1;
        }
    }

    private static char resolveUnary(String[] columns, MagicNumber item) throws ParseException {
        int unaryLen = unaryPrefixLength(columns[2]);
        if (item.dataType == MagicNumber.TYPE_STRING || unaryLen == 0)
            return MagicNumber.MAGICOPERATOR_DEFAULT;
        else if (unaryLen == 1)
            return columns[2].charAt(0);
        else if (unaryLen == 2 && columns[2].charAt(0) == MagicNumber.MAGICOPERATOR_LTHAN) {
            return MagicNumber.MAGICOPERATOR_EQUAL_LTHAN;
        } else if (unaryLen == 2 && columns[2].charAt(0) == MagicNumber.MAGICOPERATOR_GTHAN) {
            return MagicNumber.MAGICOPERATOR_EQUAL_GTHAN;
        } else
            throw new ParseException("Unrecognized unary prefix");
    }

    public static String resolveReverseDataType(int dataTypeId) {
        initTypeMap();
        for (Map.Entry<String, Integer> entry : typeMap.entrySet()) {
            if (entry.getValue().intValue() == dataTypeId) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static void initTypeMap() {
        if (typeMap != null) {
            return;
        }
        typeMap = new TreeMap<>();
        typeMap.put(MagicNumber.TYPE_KEY_BYTE, MagicNumber.TYPE_BYTE);
        typeMap.put(MagicNumber.TYPE_KEY_SHORT, MagicNumber.TYPE_SHORT);
        typeMap.put(MagicNumber.TYPE_KEY_LONG, MagicNumber.TYPE_LONG);
        typeMap.put(MagicNumber.TYPE_KEY_STRING, MagicNumber.TYPE_STRING);
        typeMap.put(MagicNumber.TYPE_KEY_DATE, MagicNumber.TYPE_DATE);
        typeMap.put(MagicNumber.TYPE_KEY_BESHORT, MagicNumber.TYPE_BESHORT);
        typeMap.put(MagicNumber.TYPE_KEY_BELONG, MagicNumber.TYPE_BELONG);
        typeMap.put(MagicNumber.TYPE_KEY_BEDATE, MagicNumber.TYPE_BEDATE);
        typeMap.put(MagicNumber.TYPE_KEY_LESHORT, MagicNumber.TYPE_LESHORT);
        typeMap.put(MagicNumber.TYPE_KEY_LELONG, MagicNumber.TYPE_LELONG);
        typeMap.put(MagicNumber.TYPE_KEY_LEDATE, MagicNumber.TYPE_LEDATE);
    }
}
