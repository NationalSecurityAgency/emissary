package emissary.util.magic;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

public class MagicNumber {

    private static final Logger log = LoggerFactory.getLogger(MagicNumber.class);

    /** The default charset used when loading the config file and when sampling data */
    public static final Charset DEFAULT_CHARSET = StandardCharsets.ISO_8859_1;
    /** Byte data type */
    public static final String TYPE_KEY_BYTE = "BYTE"; //
    /** Short data type */
    public static final String TYPE_KEY_SHORT = "SHORT"; //
    /** Long data type */
    public static final String TYPE_KEY_LONG = "LONG"; //
    /** String data type */
    public static final String TYPE_KEY_STRING = "STRING"; //
    /** Date data type */
    public static final String TYPE_KEY_DATE = "DATE"; // long integer - seconds since epoch
    /** Big endian short data type */
    public static final String TYPE_KEY_BESHORT = "BESHORT"; // big-endian 16-bit
    /** Big endian long data type */
    public static final String TYPE_KEY_BELONG = "BELONG"; // big-endian 32-bit
    /** Big endian date data type */
    public static final String TYPE_KEY_BEDATE = "BEDATE"; // big-endian 32-bit date
    /** Little endian short data type */
    public static final String TYPE_KEY_LESHORT = "LESHORT"; // little-end 16-bit
    /** Little endian long data type */
    public static final String TYPE_KEY_LELONG = "LELONG"; // little-end 32-bit
    /** Little endian long data type */
    public static final String TYPE_KEY_LEDATE = "LEDATE"; // little-end 32-bit date

    /** Unknown data type id */
    public static final int TYPE_UNKNOWN = -1;
    /** Byte data type id */
    public static final int TYPE_BYTE = 0;
    /** Short data type id */
    public static final int TYPE_SHORT = 1;
    /** Long data type id */
    public static final int TYPE_LONG = 2;
    /** String data type id */
    public static final int TYPE_STRING = 3;
    /** Date data type id */
    public static final int TYPE_DATE = 4;
    /** Big endian short data type id */
    public static final int TYPE_BESHORT = 5;
    /** Big endian long data type id */
    public static final int TYPE_BELONG = 6;
    /** Big endian date data type id */
    public static final int TYPE_BEDATE = 7;
    /** Little endian short data type id */
    public static final int TYPE_LESHORT = 8;
    /** Little endian long data type id */
    public static final int TYPE_LELONG = 9;
    /** Little endian date data type id */
    public static final int TYPE_LEDATE = 10;
    /** Empty string constant */
    public static final String EMPTYSTRING = "";

    /** Unary Operator: Equals */
    public static final char MAGICOPERATOR_AND = '=';
    /** Unary Operator: Greater than */
    public static final char MAGICOPERATOR_GTHAN = '>';
    /** Unary Operator: Less than */
    public static final char MAGICOPERATOR_LTHAN = '<';
    /** Unary Operator: At least one bit matches */
    public static final char MAGICOPERATOR_OR = 'x';
    /** Unary Operator: All bits match */
    public static final char MAGICOPERATOR_BWAND = '&';
    /** Unary Operator: None or some bits match */
    public static final char MAGICOPERATOR_BWNOT = '^';
    /** Unary Operator: Default Operator (AND) */
    public static final char MAGICOPERATOR_NOT = '!';
    /** Unary Operator: Greater than or equal to */
    public static final char MAGICOPERATOR_EQUAL_GTHAN = ']';
    /** Unary operator: Less than or equal to */
    public static final char MAGICOPERATOR_EQUAL_LTHAN = '[';
    /** Default Unary Operator - and */
    public static final char MAGICOPERATOR_DEFAULT = MAGICOPERATOR_AND;


    // Column A Properties
    protected int depth;
    protected int offset = -1;
    protected char offsetUnary = 0;

    // Column B Properties
    protected int dataType = -1;
    protected int dataTypeLength = 0;
    protected byte[] mask;
    protected boolean signedValue;

    // Column C Properties
    protected char unaryOperator;
    @Nullable
    protected byte[] value = null;
    protected boolean substitute = false;

    // Column D Properties
    @Nullable
    protected String description = null;

    // Magic Number Properties
    protected List<MagicNumber[]> dependencies;

    /**
     * Recreates the string entry for this magic number plus its child continuations under new lines preceded by a '&gt;'
     * character at the appropriate depth.
     *
     * @return String
     */
    public String toStringAll() {
        return toString(null, 0);
    }

    /**
     * Tests the sample and if successful provides the description
     */
    @Nullable
    public String describe(byte[] data) {
        String desc = describeSelf(data);
        if (desc == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(desc);
        return escapeBackspace(describeDependents(data, sb, 0));
    }

    /**
     * Private formatting method for escaping backspaces
     */
    private static String escapeBackspace(String desc) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < desc.length(); i++) {
            if (desc.charAt(i) == '\\' && (i + 1) < desc.length() && desc.charAt(i + 1) == 'b') {
                s = new StringBuilder(s.substring(0, s.length() - 1));
                i++;
                continue;
            }
            s.append(desc.charAt(i));
        }
        return s.toString();
    }

    /**
     * Describe this instance only
     */
    @Nullable
    private String describeSelf(byte[] data) {
        if (!test(data)) {
            return null;
        }
        return format(description, data);
    }

    /**
     * Private method to format output - mainly for description substitutions
     */
    private String format(String desc, byte[] data) {

        if (!substitute) {
            return desc;
        }
        Deque<Character> chars = new ArrayDeque<>();
        for (int i = desc.length() - 1; i >= 0; --i) {
            chars.push(desc.charAt(i));
        }
        StringBuilder sb = new StringBuilder();

        while (!chars.isEmpty()) {
            Character next = chars.pop();
            if (!chars.isEmpty() && next == '%') {
                char subType = chars.pop();
                if (dataType == TYPE_STRING) {
                    if (offset < (data.length - 2)) {
                        String sub = new String(Objects.requireNonNull(getElement(data, offset, 1)), DEFAULT_CHARSET);
                        sb.append(sub);
                    }
                } else if (subType == 'c' || subType == 's') {

                    byte[] subData = getElement(data, offset, dataTypeLength);
                    if (subData != null) {
                        String sub = new String(subData, DEFAULT_CHARSET);
                        sb.append(sub);
                    }

                } else {

                    byte[] subData = getElement(data, offset, dataTypeLength);
                    if (subData != null) {
                        String sub = MagicMath.byteArrayToString(subData, 10);
                        sb.append(sub);
                    }
                }

                if (subType == 'l' && !chars.isEmpty() && chars.peek() == 'd') {
                    chars.pop();
                }
                continue;
            }
            sb.append(next.charValue());
        }
        return sb.toString();
    }

    /**
     * Tests dependent children
     */
    private String describeDependents(byte[] data, StringBuilder sb, int layer) {
        log.debug("DESCRIBING DEPENDENTS at layer {}", layer);
        if (dependencies == null || layer >= dependencies.size()) {
            log.debug("Not enough dependents for layer {}", layer);
            return sb.toString();
        }

        boolean shouldContinue = false;
        MagicNumber[] dependentItems = dependencies.get(layer);
        log.debug("Found {} items at layer {}", dependentItems.length, layer);
        for (MagicNumber dependentItem : dependentItems) {
            String s = dependentItem.describeSelf(data);

            if (s != null) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(s);
                shouldContinue = true;
            }
        }

        if (!shouldContinue) {
            return sb.toString();
        }
        return describeDependents(data, sb, layer + 1);
    }

    /**
     * Tests this magic number against the given data
     */
    public boolean test(byte[] data) {
        byte[] subject = getElement(data, offset, dataTypeLength);
        if (subject == null) {
            return false;
        }
        return testNumeric(subject);
    }

    /**
     * Tests numeric byte data only
     */
    private boolean testNumeric(byte[] data) {
        if (substitute) {
            return true;
        }
        byte[] mValues = value;

        log.debug("Unary Operator: {}", unaryOperator);

        int end = mValues.length;
        switch (unaryOperator) {
            case MAGICOPERATOR_AND:
            case MAGICOPERATOR_BWAND:
                for (int i = 0; i < end; i++) {
                    if (data[i] != mValues[i]) {
                        return false;
                    }
                }
                return true;
            case MAGICOPERATOR_GTHAN:
                for (int i = 0; i < end; i++) {
                    if ((data[i] & 0xFF) < (mValues[i] & 0xFF)) {
                        return false;
                    }
                    if (i == end - 1 && data[i] == mValues[i]) {
                        return false;
                    }
                }
                return true;
            case MAGICOPERATOR_LTHAN:
                for (int i = 0; i < end; i++) {
                    if ((data[i] & 0xFF) > (mValues[i] & 0xFF)) {
                        return false;
                    }
                    if (i == end - 1 && data[i] == mValues[i]) {
                        return false;
                    }
                }
                return true;
            case MAGICOPERATOR_OR:
                for (int i = 0; i < end; i++) {
                    if (data[i] == mValues[i]) {
                        return true;
                    }
                }
                return false;
            case MAGICOPERATOR_BWNOT:
            case MAGICOPERATOR_NOT:
                for (int i = 0; i < end; i++) {
                    if (data[i] != mValues[i]) {
                        return true;
                    }
                }
                return false;
            case MAGICOPERATOR_EQUAL_GTHAN:
                for (int i = 0; i < end; i++) {
                    if ((data[i] & 0xFF) < (mValues[i] & 0xFF)) {
                        return false;
                    }
                }
                return true;
            case MAGICOPERATOR_EQUAL_LTHAN:
                for (int i = 0; i < end; i++) {
                    if ((data[i] & 0xFF) > (mValues[i] & 0xFF)) {
                        return false;
                    }
                }
                return true;
            default:
                throw new IllegalStateException(
                        "This MagicNumber instance is configured incorrectly. The unary operator is set to an unknown or unconfigured value.");

        }
    }

    /**
     * Retrieves the data sample
     */
    @Nullable
    private static byte[] getElement(@Nullable byte[] data, int offset, int length) {
        if (data == null) {
            return null;
        }
        if (data.length < (offset + length)) {
            return null;
        }
        byte[] subject = new byte[length];
        System.arraycopy(data, offset, subject, 0, subject.length);
        return subject;
    }

    /**
     * Add child continuations
     */
    @SuppressWarnings("AvoidObjectArrays")
    public void addDependencyLayer(MagicNumber[] dependencyLayer) {
        if (dependencies == null) {
            dependencies = new ArrayList<>();
        }
        dependencies.add(dependencyLayer);
    }

    /**
     * Re-creates the string magic number entry for this number only
     *
     * @return a String represention of the entry
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder();
        sb.append(">".repeat(Math.max(0, depth)));
        if (offsetUnary > 0) {
            sb.append(offsetUnary);
        }

        if (offset == 0) {
            sb.append("0");
        } else {
            sb.append(MagicMath.HEX_PREFIX);
            sb.append(Integer.toHexString(offset));
        }

        sb.append('\t');
        sb.append(MagicNumberFactory.resolveReverseDataType(dataType));
        if (mask != null && mask.length > 0) {
            sb.append('&');
            sb.append(MagicMath.byteArrayToHexString(mask));
        }

        sb.append('\t');
        if (unaryOperator == MAGICOPERATOR_EQUAL_LTHAN) {
            sb.append("<=");
        } else if (unaryOperator == MAGICOPERATOR_EQUAL_GTHAN) {
            sb.append(">=");
        } else {
            sb.append(unaryOperator);
        }
        sb.append(MagicMath.byteArrayToHexString(value));

        sb.append('\t');
        sb.append(description);
        return sb.toString();
    }

    /**
     * Private method to create the string plus continuations
     */
    private String toString(@Nullable StringBuilder sbuf, int depth) {
        StringBuilder sb = sbuf;
        int d = depth;
        if (sb == null) {
            sb = new StringBuilder(description);
        }
        if (dependencies == null || d >= dependencies.size()) {
            return sb.toString();
        }
        MagicNumber[] dependentItems = dependencies.get(d);
        for (MagicNumber dependentItem : dependentItems) {
            sb.append('\n');
            sb.append(dependentItem.toString());
        }
        return toString(sb, d + 1);
    }


}
