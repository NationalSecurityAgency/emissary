package emissary.util.magic;

import emissary.core.EmissaryRuntimeException;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses file-type configuration rules (magic style) into ready-to-use {@link MagicNumber} objects.
 *
 * <p>
 * Each rule line is made up of four columns separated by spaces or tabs: the file location/nesting depth, the data type
 * and optional mask, the comparison check and value, and the description text. Rules with a depth greater than zero act
 * as sub-rules (continuations) linked to a parent rule.
 * </p>
 */
public class MagicNumberFactory {

    private static final Logger log = LoggerFactory.getLogger(MagicNumberFactory.class);

    public static final String EMPTYSTRING = "";
    public static final String ENTRY_NOT_NULL_RULE = "Entry cannot be null";
    public static final String ENTRY_4COLUMN_RULE = "Entry must have four tab separated columns";

    public static final String UNSUPPORTED_DATATYPE_MSG_SEARCH = "Data Type 'search/N' not supported - e.g. search/1";
    public static final String UNSUPPORTED_DATATYPE_MSG_REGEX = "Data Type 'regex' not supported";
    public static final String UNSUPPORTED_DATATYPE_MSG_UNSIGNED = "Signed Data Types unsupported - e.g. UBELONG";
    protected static final List<String> IGNORABLE_DATATYPE_MSGS = Arrays.asList(UNSUPPORTED_DATATYPE_MSG_UNSIGNED,
            UNSUPPORTED_DATATYPE_MSG_REGEX, UNSUPPORTED_DATATYPE_MSG_SEARCH);

    private MagicNumberFactory() {}


    /**
     * Reads a byte array of configuration data and turns it into a list of organized, nested rule objects.
     *
     * @param configData the raw bytes of the config file
     * @param zeroDepthErrorList collects error messages for main rules that fail to parse
     * @param continuationErrorMap collects error messages for sub-rules that fail to parse
     * @return a list of top-level magic rules
     */
    public static List<MagicNumber> buildMagicNumberList(byte[] configData, List<String> zeroDepthErrorList,
            Map<String, List<String>> continuationErrorMap) {
        return buildMagicNumberList(configData, zeroDepthErrorList, continuationErrorMap, false);
    }

    /**
     * Reads a byte array of configuration data and turns it into a list of organized, nested rule objects, with an option
     * to ignore unsupported data types.
     *
     * @param configData the raw bytes of the config file
     * @param zeroDepthErrorList collects error messages for main rules that fail to parse
     * @param continuationErrorMap collects error messages for sub-rules that fail to parse
     * @param swallowParseException true to quietly skip unsupported data type errors instead of throwing them
     * @return a list of top-level magic rules
     */
    public static List<MagicNumber> buildMagicNumberList(byte[] configData, @Nullable List<String> zeroDepthErrorList,
            @Nullable Map<String, List<String>> continuationErrorMap, boolean swallowParseException) {

        List<MagicNumber> magicNumberList = new ArrayList<>();
        MagicNumber finger = null;
        int currentDepth = -1;
        List<MagicNumber> extensions = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(configData), MagicNumber.DEFAULT_CHARSET))) {
            String s;
            int counter = 0;
            while ((s = reader.readLine()) != null) {
                counter++;
                if (s.isEmpty() || s.charAt(0) == '#') {
                    continue;
                }
                int depth = getEntryDepth(s);
                if (depth < 0) {
                    continue;
                }
                try {
                    if (depth == 0 && !extensions.isEmpty()) {
                        if (finger == null) {
                            extensions = new ArrayList<>();
                        } else {
                            addExtensionsLayer(extensions, finger);
                            extensions = new ArrayList<>();
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

                        if (finger == null) {
                            continue;
                        }
                        if (currentDepth < 0) {
                            currentDepth = depth;
                        }
                        if (currentDepth == depth) {
                            parseAndStore(extensions, s, swallowParseException);
                        } else if (currentDepth < depth) {
                            if (extensions.isEmpty()) {
                                finger = null;
                                currentDepth = -1;
                                continue;
                            }
                            currentDepth = depth;
                            addExtensionsLayer(extensions, finger);
                            extensions = new ArrayList<>();
                            parseAndStore(extensions, s, swallowParseException);
                        }
                    }
                } catch (Exception e) {

                    if (continuationErrorMap == null || zeroDepthErrorList == null) {
                        continue;
                    }
                    if (swallowParseException &&
                            (e.getClass() == ParseException.class) &&
                            IGNORABLE_DATATYPE_MSGS.contains(e.getMessage())) {
                        continue;
                    }


                    if (depth > 0) {
                        MagicNumber mItem = magicNumberList.get(magicNumberList.size() - 1);
                        String signature = mItem.toString();
                        List<String> failedExtensions = continuationErrorMap.computeIfAbsent(signature, k -> new ArrayList<>());
                        failedExtensions.add("[MAGIC LINE# " + counter + "] " + s);
                    } else {
                        zeroDepthErrorList.add("[MAGIC LINE# " + counter + "] " + s);
                    }
                }
            }
            if (finger != null && !extensions.isEmpty()) {
                addExtensionsLayer(extensions, finger);
            }
        } catch (IOException ioe) {
            log.error("Caught IOException on buildMagicNumberList (throwing a runtime exception): {}", ioe.getMessage(), ioe);
            throw new EmissaryRuntimeException(ioe);
        }
        return magicNumberList;
    }

    /**
     * Parses a single rule line and adds it to the target storage list.
     *
     * @param storage the list to add the rule to
     * @param entry the raw text line of the rule
     * @param swallowParseException whether to ignore unsupported type errors
     * @return the created rule object, or null if skipped
     * @throws Exception if parsing fails
     */
    private static MagicNumber parseAndStore(List<MagicNumber> storage, String entry, boolean swallowParseException) throws Exception {
        MagicNumber item = buildMagicNumber(entry, swallowParseException);
        if (item != null) {
            storage.add(item);
        }
        return item;
    }

    /**
     * Attaches a group of sub-rules as a new evaluation layer onto a parent rule.
     *
     * @param extensions the list of child rule objects
     * @param target the parent rule object
     */
    private static void addExtensionsLayer(List<MagicNumber> extensions, MagicNumber target) {
        target.addDependencyLayer(extensions.toArray(new MagicNumber[0]));
    }

    /**
     * Parses a single configuration line into a rule object.
     *
     * @param entry the rule text line
     * @return the parsed rule object
     * @throws ParseException if the line format is invalid
     */
    public static MagicNumber buildMagicNumber(String entry) throws ParseException {
        return buildMagicNumber(entry, false);
    }

    /**
     * Parses a single configuration line into a rule object, with an option to ignore unsupported data types.
     *
     * @param entry the rule text line
     * @param swallowParseException whether to ignore unsupported types
     * @return the parsed rule object
     * @throws ParseException if the line format is invalid
     */
    public static MagicNumber buildMagicNumber(String entry, boolean swallowParseException) throws ParseException {

        String[] columns = prepareEntry(entry);

        int depth;
        int offset;
        char offsetUnary;
        try {
            depth = getEntryDepth(columns[0]);
            offsetUnary = resolveOffsetUnary(columns);
            offset = resolveOffset(columns, depth);
        } catch (Exception e) {
            throw new ParseException("Error on column 0:" + columns[0] + ". " + e.getMessage());
        }

        MagicDataType dataType;
        int dataTypeLength;
        byte[] mask;
        try {
            dataType = resolveDataType(columns);
            dataTypeLength = dataType.getFixedByteLength();
            mask = resolveMask(columns, dataTypeLength);
        } catch (Exception e) {
            if (swallowParseException) {
                log.debug("Warning unable to read column 1\t: {} - {}", columns[1], e.getMessage());
            } else {
                log.error("original entry   \t: {}", entry);
                log.error("Error on column 1\t: {}", columns[1], e);
            }
            throw new ParseException("Parse Error on column 1:" + columns[1] + ". " + e.getMessage());
        }

        char unaryOperator;
        byte[] value;
        boolean substitute;
        try {
            unaryOperator = resolveUnary(columns, dataType);
            value = resolveValue(columns, dataType, dataTypeLength);
            substitute = isAnyValuePlaceholder(columns[2]);

            if (dataType == MagicDataType.STRING && value != null) {
                dataTypeLength = value.length;
            }
        } catch (Exception e) {
            throw new ParseException("Error on column 2:" + columns[2] + ". " + e.getMessage());
        }

        return new MagicNumber(depth, offset, offsetUnary, dataType, dataTypeLength, mask, unaryOperator, value,
                substitute, columns[3]);
    }

    /**
     * Splits a configuration line into four distinct columns by spaces or tabs, taking escape characters into account.
     */
    private static String[] tokenizeEntry(String entry) {
        int index = 0;
        String[] columns = new String[4];
        Arrays.fill(columns, EMPTYSTRING);

        for (int i = 0; i < entry.length(); i++) {
            char c = entry.charAt(i);
            if (c == '\\' && i != (entry.length() - 1) && entry.charAt(i + 1) == ' ') {
                columns[index] += ' ';
                i++;
            } else if (c == ' ' || c == '\t') {
                while (entry.length() > (i + 1)) {
                    if (entry.charAt(i + 1) == ' ' || entry.charAt(i + 1) == '\t') {
                        i++;
                    } else {
                        break;
                    }
                }
                index++;
            } else {
                columns[index] += c;
            }
            if (index == 3) {
                if (entry.length() > (i + 1)) {
                    columns[index] = entry.substring(i + 1);
                }
                break;
            }
        }
        return columns;
    }

    /**
     * Cleans up common syntax mistakes or formatting issues in a raw rule line before parsing.
     */
    private static String[] prepareEntry(String entry) throws ParseException {
        if (entry == null) {
            throw new ParseException(ENTRY_NOT_NULL_RULE);
        }
        String subject = entry;
        int invalidOperatorIndex = subject.indexOf(" = ");
        if (invalidOperatorIndex > 0) {
            String tail = subject.length() > (invalidOperatorIndex + 3) ? ' ' + subject.substring(invalidOperatorIndex + 3) : "";
            subject = subject.substring(0, invalidOperatorIndex) + tail;
        }

        String[] columns = tokenizeEntry(subject);
        for (int count = 0; count < columns.length; count++) {
            if (columns[count].isEmpty() && count < 3) {
                throw new ParseException(ENTRY_4COLUMN_RULE);
            }
        }
        return columns;
    }

    private static int resolveOffset(String[] columns, int depth) throws ParseException {
        String entry = columns[0];
        if (depth > 0) {
            entry = entry.substring(depth);
        }
        if (entry.charAt(0) == '&') {
            entry = entry.substring(1);
        } else if (entry.charAt(0) == '(' && entry.charAt(entry.length() - 1) == ')') {
            entry = entry.substring(1, entry.length() - 1);
        }
        try {
            return MagicMath.stringToInt(entry);
        } catch (NumberFormatException e) {
            throw new ParseException(e + ": Malformed offset value");
        }
    }

    private static char resolveOffsetUnary(String[] columns) {
        if (!columns[0].isEmpty() && columns[0].charAt(0) == '&') {
            return '&';
        }
        return (char) 0;
    }

    /**
     * Determines how deeply nested a rule line is based on how many greater-than symbols (&gt;) start the line.
     *
     * @param entry the rule line text
     * @return the nesting depth number, or -1 if invalid
     */
    public static int getEntryDepth(String entry) {
        if (entry.isEmpty() || (entry.charAt(0) != '>' && !Character.isDigit(entry.charAt(0)))) {
            return -1;
        }
        int depth = 0;
        for (; depth < entry.length(); depth++) {
            if (entry.charAt(depth) != '>') {
                break;
            }
        }
        return depth;
    }

    private static MagicDataType resolveDataType(String[] columns) throws ParseException {
        String subject = columns[1];
        if (subject.startsWith("search")) {
            throw new ParseException(UNSUPPORTED_DATATYPE_MSG_SEARCH);
        }
        if (subject.equals("regex")) {
            throw new ParseException(UNSUPPORTED_DATATYPE_MSG_REGEX);
        }
        if (subject.charAt(0) == 'u' || subject.charAt(0) == 'U') {
            throw new ParseException(UNSUPPORTED_DATATYPE_MSG_UNSIGNED);
        }

        int ix = subject.indexOf("&") > 0 ? subject.indexOf("&") : subject.indexOf("/");
        String typeName = ix > 0 ? columns[1].substring(0, ix) : subject;

        return MagicDataType.fromKey(typeName)
                .filter(MagicDataType::isSupported)
                .orElseThrow(() -> new ParseException("Unsupported Data Type: " + typeName));
    }

    @Nullable
    private static byte[] resolveMask(String[] columns, int dataTypeLength) {
        int ix = columns[1].indexOf("&");
        if (ix > 0) {
            byte[] maskValues = MagicMath.stringToByteArray(columns[1].substring(ix + 1));
            return MagicMath.setLength(maskValues, dataTypeLength);
        }
        return null;
    }

    private static byte[] resolveValue(String[] columns, MagicDataType dataType, int dataTypeLength) {
        String subject = columns[2];

        if (dataType == MagicDataType.STRING && !isAnyValuePlaceholder(subject)) {
            return MagicMath.parseEscapedString(subject);
        } else if (isAnyValuePlaceholder(subject)) {
            return new byte[0];
        }

        int unaryLen = unaryPrefixLength(subject);
        if (unaryLen > 0) {
            subject = subject.substring(unaryLen);
        }
        if (subject.toUpperCase(Locale.ROOT).endsWith("L")) {
            subject = subject.substring(0, subject.length() - 1);
        }
        byte[] valueArray = MagicMath.stringToByteArray(subject);
        valueArray = MagicMath.setLength(valueArray, dataTypeLength);

        if (dataType == MagicDataType.LELONG) {
            MagicMath.longEndianSwap(valueArray, 0);
        } else if (dataType == MagicDataType.LESHORT) {
            MagicMath.shortEndianSwap(valueArray, 0);
        }
        return valueArray;
    }

    private static boolean isAnyValuePlaceholder(String subject) {
        return subject.length() == 1 && subject.charAt(0) == 'x';
    }

    private static int unaryPrefixLength(@Nullable String s) {

        if (s == null || s.isEmpty()) {
            return 0;
        }

        char op = s.charAt(0);
        int len = s.length();
        if (!Character.isDigit(op)) {
            switch (op) {
                case MagicNumber.MAGICOPERATOR_AND:
                case MagicNumber.MAGICOPERATOR_OR:
                case MagicNumber.MAGICOPERATOR_BWAND:
                case MagicNumber.MAGICOPERATOR_BWNOT:
                case MagicNumber.MAGICOPERATOR_NOT:
                    return 1;
                case MagicNumber.MAGICOPERATOR_GTHAN:
                case MagicNumber.MAGICOPERATOR_LTHAN:
                    return len > 1 && s.charAt(1) == MagicNumber.MAGICOPERATOR_AND ? 2 : 1;
                default:
                    return 0;
            }
        }
        return 0;
    }

    private static char resolveUnary(String[] columns, MagicDataType dataType) throws ParseException {
        int unaryLen = unaryPrefixLength(columns[2]);
        if (dataType == MagicDataType.STRING || unaryLen == 0) {
            return MagicNumber.MAGICOPERATOR_DEFAULT;
        } else if (unaryLen == 1) {
            return columns[2].charAt(0);
        } else if (unaryLen == 2 && columns[2].charAt(0) == MagicNumber.MAGICOPERATOR_LTHAN) {
            return MagicNumber.MAGICOPERATOR_EQUAL_LTHAN;
        } else if (unaryLen == 2 && columns[2].charAt(0) == MagicNumber.MAGICOPERATOR_GTHAN) {
            return MagicNumber.MAGICOPERATOR_EQUAL_GTHAN;
        } else {
            throw new ParseException("Unrecognized unary prefix");
        }
    }

    /**
     * Finds the text name of a data type using its old legacy numeric ID.
     *
     * @param legacyTypeId the historical numeric type ID
     * @return the text key name, or null if unknown
     */
    @Nullable
    public static String resolveReverseDataType(int legacyTypeId) {
        return MagicDataType.fromLegacyId(legacyTypeId).map(MagicDataType::getKey).orElse(null);
    }
}
