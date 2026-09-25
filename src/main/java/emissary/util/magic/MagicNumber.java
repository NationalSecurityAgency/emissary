package emissary.util.magic;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Queue;

/**
 * Represents a single matching rule from a file-type configuration file (magic file), along with any sub-rules
 * (continuations) that follow it.
 *
 * <ul>
 * <li>Column A: How deep the rule is nested and where in the file to look (e.g., {@code >&4})</li>
 * <li>Column B: The data type to read, optionally with a bit mask (e.g., {@code belong&0xff000000})</li>
 * <li>Column C: The comparison check and expected value (e.g., {@code >0xCAFEBABE})</li>
 * <li>Column D: The text description to output, which can include dynamic value placeholders like {@code %d}</li>
 * </ul>
 */
public class MagicNumber {

    private static final Logger log = LoggerFactory.getLogger(MagicNumber.class);

    /** The default text encoding (charset) used when reading config files and checking data. */
    public static final Charset DEFAULT_CHARSET = StandardCharsets.ISO_8859_1;

    /** @deprecated use {@code MagicDataType.BYTE.getKey()} */
    @Deprecated
    public static final String TYPE_KEY_BYTE = MagicDataType.BYTE.getKey();
    /** @deprecated use {@code MagicDataType.SHORT.getKey()} */
    @Deprecated
    public static final String TYPE_KEY_SHORT = MagicDataType.SHORT.getKey();
    /** @deprecated use {@code MagicDataType.LONG.getKey()} */
    @Deprecated
    public static final String TYPE_KEY_LONG = MagicDataType.LONG.getKey();
    /** @deprecated use {@code MagicDataType.STRING.getKey()} */
    @Deprecated
    public static final String TYPE_KEY_STRING = MagicDataType.STRING.getKey();
    /** @deprecated use {@code MagicDataType.DATE.getKey()} */
    @Deprecated
    public static final String TYPE_KEY_DATE = MagicDataType.DATE.getKey();
    /** @deprecated use {@code MagicDataType.BESHORT.getKey()} */
    @Deprecated
    public static final String TYPE_KEY_BESHORT = MagicDataType.BESHORT.getKey();
    /** @deprecated use {@code MagicDataType.BELONG.getKey()} */
    @Deprecated
    public static final String TYPE_KEY_BELONG = MagicDataType.BELONG.getKey();
    /** @deprecated use {@code MagicDataType.BEDATE.getKey()} */
    @Deprecated
    public static final String TYPE_KEY_BEDATE = MagicDataType.BEDATE.getKey();
    /** @deprecated use {@code MagicDataType.LESHORT.getKey()} */
    @Deprecated
    public static final String TYPE_KEY_LESHORT = MagicDataType.LESHORT.getKey();
    /** @deprecated use {@code MagicDataType.LELONG.getKey()} */
    @Deprecated
    public static final String TYPE_KEY_LELONG = MagicDataType.LELONG.getKey();
    /** @deprecated use {@code MagicDataType.LEDATE.getKey()} */
    @Deprecated
    public static final String TYPE_KEY_LEDATE = MagicDataType.LEDATE.getKey();

    /** @deprecated use {@link MagicDataType#fromLegacyId(int)} */
    @Deprecated
    public static final int TYPE_UNKNOWN = -1;
    /** @deprecated use {@link MagicDataType#BYTE} */
    @Deprecated
    public static final int TYPE_BYTE = MagicDataType.BYTE.getLegacyId();
    /** @deprecated use {@link MagicDataType#SHORT} */
    @Deprecated
    public static final int TYPE_SHORT = MagicDataType.SHORT.getLegacyId();
    /** @deprecated use {@link MagicDataType#LONG} */
    @Deprecated
    public static final int TYPE_LONG = MagicDataType.LONG.getLegacyId();
    /** @deprecated use {@link MagicDataType#STRING} */
    @Deprecated
    public static final int TYPE_STRING = MagicDataType.STRING.getLegacyId();
    /** @deprecated use {@link MagicDataType#DATE} */
    @Deprecated
    public static final int TYPE_DATE = MagicDataType.DATE.getLegacyId();
    /** @deprecated use {@link MagicDataType#BESHORT} */
    @Deprecated
    public static final int TYPE_BESHORT = MagicDataType.BESHORT.getLegacyId();
    /** @deprecated use {@link MagicDataType#BELONG} */
    @Deprecated
    public static final int TYPE_BELONG = MagicDataType.BELONG.getLegacyId();
    /** @deprecated use {@link MagicDataType#BEDATE} */
    @Deprecated
    public static final int TYPE_BEDATE = MagicDataType.BEDATE.getLegacyId();
    /** @deprecated use {@link MagicDataType#LESHORT} */
    @Deprecated
    public static final int TYPE_LESHORT = MagicDataType.LESHORT.getLegacyId();
    /** @deprecated use {@link MagicDataType#LELONG} */
    @Deprecated
    public static final int TYPE_LELONG = MagicDataType.LELONG.getLegacyId();
    /** @deprecated use {@link MagicDataType#LEDATE} */
    @Deprecated
    public static final int TYPE_LEDATE = MagicDataType.LEDATE.getLegacyId();

    /** @deprecated use a plain string literal */
    @Deprecated
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
    private final int depth;
    private final int offset;
    private final char offsetUnary;

    // Column B Properties
    private final MagicDataType dataType;
    private final int dataTypeLength;
    @Nullable
    private final byte[] mask;

    // Column C Properties
    private final char unaryOperator;
    @Nullable
    private final byte[] value;
    private final boolean substitute;

    // Column D Properties
    @Nullable
    private final String description;

    private final List<List<MagicNumber>> dependencyLayers = new ArrayList<>();

    /**
     * Creates a fully specified rule. Intended to be called exclusively by the factory while parsing config files.
     *
     * @param depth how deeply nested this rule is (from column A)
     * @param offset where to look in the file bytes (from column A)
     * @param offsetUnary the relative offset symbol, or 0 if none
     * @param dataType the data type being checked (from column B)
     * @param dataTypeLength how many bytes this rule tests
     * @param mask optional bit mask applied before testing
     * @param unaryOperator the comparison sign (from column C)
     * @param value the expected value to check against (from column C)
     * @param substitute true if column C uses the wildcard 'x' to match anything and insert live values into the
     *        description
     * @param description the description text (from column D)
     */
    MagicNumber(int depth, int offset, char offsetUnary, MagicDataType dataType, int dataTypeLength, @Nullable byte[] mask,
            char unaryOperator, @Nullable byte[] value, boolean substitute, @Nullable String description) {
        this.depth = depth;
        this.offset = offset;
        this.offsetUnary = offsetUnary;
        this.dataType = dataType;
        this.dataTypeLength = dataTypeLength;
        this.mask = mask;
        this.unaryOperator = unaryOperator;
        this.value = value;
        this.substitute = substitute;
        this.description = description;
    }

    public boolean isSubstitute() {
        return substitute;
    }

    /**
     * Recreates the full config text for this rule plus all of its matching sub-rules
     *
     * @return the text representation of this rule and its children
     */
    public String toStringAll() {
        StringBuilder sb = new StringBuilder(Objects.toString(description));
        for (List<MagicNumber> layer : dependencyLayers) {
            for (MagicNumber dependentItem : layer) {
                sb.append('\n');
                sb.append(dependentItem.toString());
            }
        }
        return sb.toString();
    }

    /**
     * Tests the input data against this rule. If it matches, returns the description combined with any matching sub-rules.
     *
     * @param data the raw file bytes to check
     * @return the combined description text, or null if the rule doesn't match
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
     * Processes backspace characters (\b) in the description text, deleting the previous character for each one found.
     */
    private static String escapeBackspace(String desc) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < desc.length(); i++) {
            if (desc.charAt(i) == '\\' && (i + 1) < desc.length() && desc.charAt(i + 1) == 'b') {
                if (s.length() > 0) {
                    s.setLength(s.length() - 1);
                }
                i++;
                continue;
            }
            s.append(desc.charAt(i));
        }
        return s.toString();
    }

    /**
     * Tests this rule on its own, without checking sub-rules.
     *
     * @param data the raw file bytes
     * @return the formatted description, or null if it doesn't match
     */
    @Nullable
    private String describeSelf(byte[] data) {
        if (!test(data)) {
            return null;
        }
        return format(description, data);
    }

    /**
     * Fills in dynamic placeholders (like %d or %s) in the description text using values pulled straight from the file
     * bytes.
     */
    private String format(@Nullable String desc, byte[] data) {

        if (!substitute || desc == null) {
            return desc;
        }
        Queue<Character> chars = new ArrayDeque<>(desc.length());
        for (int i = 0; i < desc.length(); i++) {
            chars.add(desc.charAt(i));
        }
        StringBuilder sb = new StringBuilder();

        while (!chars.isEmpty()) {
            Character next = chars.poll();
            if (!chars.isEmpty() && next == '%') {
                char subType = chars.poll();
                if (dataType == MagicDataType.STRING) {
                    if (offset < (data.length - 2)) {
                        String sub = new String(Objects.requireNonNull(extractElement(data, offset, 1)), DEFAULT_CHARSET);
                        sb.append(sub);
                    }
                } else if (subType == 'c' || subType == 's') {

                    byte[] subData = extractElement(data, offset, dataTypeLength);
                    if (subData != null) {
                        String sub = new String(subData, DEFAULT_CHARSET);
                        sb.append(sub);
                    }

                } else {

                    byte[] subData = extractElement(data, offset, dataTypeLength);
                    if (subData != null) {
                        String sub = MagicMath.byteArrayToString(subData, 10);
                        sb.append(sub);
                    }
                }

                if (subType == 'l' && !chars.isEmpty() && chars.peek() == 'd') {
                    chars.poll();
                }
                continue;
            }
            sb.append(next);
        }
        return sb.toString();
    }

    /**
     * Checks sub-rule layers one level at a time, continuing down the chain as long as at least one rule in the current
     * layer matches.
     */
    private String describeDependents(byte[] data, StringBuilder sb, int layer) {
        log.debug("DESCRIBING DEPENDENTS at layer {}", layer);
        if (layer >= dependencyLayers.size()) {
            log.debug("Not enough dependents for layer {}", layer);
            return sb.toString();
        }

        boolean shouldContinue = false;
        List<MagicNumber> dependentItems = dependencyLayers.get(layer);
        log.debug("Found {} items at layer {}", dependentItems.size(), layer);
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
     * Checks if the file bytes match what this rule expects at the specified offset.
     *
     * @param data the raw file bytes
     * @return true if the bytes match the rule's criteria
     */
    public boolean test(byte[] data) {
        byte[] subject = extractElement(data, offset, dataTypeLength);
        return subject != null && matches(subject);
    }

    private boolean matches(byte[] subject) {
        if (substitute) {
            return true;
        }

        applyMask(subject);

        if (dataType == MagicDataType.STRING) {
            return Arrays.equals(subject, value);
        }

        if (value == null || subject.length != value.length) {
            return false;
        }

        log.debug("Unary Operator: {}", unaryOperator);
        return MatchOperator.forSymbol(unaryOperator).matches(subject, value, dataType.isBigEndian());
    }

    private void applyMask(byte[] subject) {
        if (mask != null && mask.length == subject.length) {
            for (int i = 0; i < subject.length; i++) {
                subject[i] &= mask[i];
            }
        }
    }

    /**
     * Grabs a chunk of bytes from the input data starting at the given offset. Returns null if there aren't enough bytes
     * available.
     */
    @Nullable
    private static byte[] extractElement(@Nullable byte[] data, int offset, int length) {
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
     * Adds a group of sub-rules that should be checked if this rule succeeds.
     *
     * @param dependencyLayer an array of child rules for the next depth level
     */
    @SuppressWarnings("AvoidObjectArrays")
    public void addDependencyLayer(MagicNumber[] dependencyLayer) {
        this.dependencyLayers.add(Arrays.asList(dependencyLayer));
    }

    /**
     * Recreates the original config file line for this rule
     *
     * @return the config line string
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
        sb.append(dataType.getKey());
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

        if (dataType == MagicDataType.STRING && value != null) {
            sb.append(new String(value, DEFAULT_CHARSET));
        } else {
            sb.append(MagicMath.byteArrayToHexString(value));
        }

        sb.append('\t');
        sb.append(description);
        return sb.toString();
    }

    /**
     * Handles the comparison logic (like equals, greater than, less than) used in rules.
     */
    private enum MatchOperator {

        EQUALS, NOT_EQUALS, ANY_BITS_SET, GREATER_THAN, LESS_THAN, GREATER_OR_EQUAL, LESS_OR_EQUAL;

        static MatchOperator forSymbol(char symbol) {
            switch (symbol) {
                case MAGICOPERATOR_AND:
                case MAGICOPERATOR_BWAND:
                    return EQUALS;
                case MAGICOPERATOR_NOT:
                case MAGICOPERATOR_BWNOT:
                    return NOT_EQUALS;
                case MAGICOPERATOR_OR:
                    return ANY_BITS_SET;
                case MAGICOPERATOR_GTHAN:
                    return GREATER_THAN;
                case MAGICOPERATOR_LTHAN:
                    return LESS_THAN;
                case MAGICOPERATOR_EQUAL_GTHAN:
                    return GREATER_OR_EQUAL;
                case MAGICOPERATOR_EQUAL_LTHAN:
                    return LESS_OR_EQUAL;
                default:
                    throw new IllegalStateException(
                            "This MagicNumber instance is configured incorrectly. The unary operator is set to an unknown or unconfigured value.");
            }
        }

        boolean matches(byte[] subject, byte[] value, boolean mostSignificantByteFirst) {
            switch (this) {
                case EQUALS:
                    return Arrays.equals(subject, value);
                case NOT_EQUALS:
                    return !Arrays.equals(subject, value);
                case ANY_BITS_SET:
                    for (int i = 0; i < subject.length; i++) {
                        if ((subject[i] & value[i]) != 0) {
                            return true;
                        }
                    }
                    return false;
                default:
                    int cmp = compare(subject, value, mostSignificantByteFirst);
                    switch (this) {
                        case GREATER_THAN:
                            return cmp > 0;
                        case GREATER_OR_EQUAL:
                            return cmp >= 0;
                        case LESS_THAN:
                            return cmp < 0;
                        case LESS_OR_EQUAL:
                            return cmp <= 0;
                        default:
                            throw new IllegalStateException("No comparison semantics for " + name());
                    }
            }
        }

        private static int compare(byte[] left, byte[] right, boolean mostSignificantByteFirst) {
            if (mostSignificantByteFirst) {
                for (int i = 0; i < left.length; i++) {
                    int l = left[i] & 0xFF;
                    int r = right[i] & 0xFF;
                    if (l != r) {
                        return Integer.compare(l, r);
                    }
                }
            } else {
                for (int i = left.length - 1; i >= 0; i--) {
                    int l = left[i] & 0xFF;
                    int r = right[i] & 0xFF;
                    if (l != r) {
                        return Integer.compare(l, r);
                    }
                }
            }
            return 0;
        }
    }
}
