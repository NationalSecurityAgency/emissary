package emissary.util.magic;

import java.util.Locale;
import java.util.Optional;

/**
 * Represents the different data types you can find in a "magic number" configuration file (like BYTE, SHORT, STRING,
 * etc.). Each type defines what it's called in config files, how many bytes it takes up, and whether it reads numbers
 * from left-to-right (big-endian) or right-to-left (little-endian). Date types are kept so old files can still load,
 * but they aren't actively parsed.
 */
public enum MagicDataType {

    /** A single regular byte of data. */
    BYTE("BYTE", 0, 1),

    /** A 2-byte number, read with the biggest part first. */
    SHORT("SHORT", 1, 2),

    /** A 4-byte number, read with the biggest part first. */
    LONG("LONG", 2, 4),

    /** A chunk of text whose size depends on what is written in the file. */
    STRING("STRING", 3, -1),

    /** A 4-byte date stamp, stored little-endian, but skipped by the parser. */
    DATE("DATE", 4, 4, false, false),

    /** A 2-byte big-endian number. */
    BESHORT("BESHORT", 5, 2),

    /** A 4-byte big-endian number. */
    BELONG("BELONG", 6, 4),

    /** A 4-byte big-endian date stamp, skipped by the parser. */
    BEDATE("BEDATE", 7, 4, true, false),

    /** A 2-byte little-endian number (smallest part first). */
    LESHORT("LESHORT", 8, 2, false, true),

    /** A 4-byte little-endian number (smallest part first). */
    LELONG("LELONG", 9, 4, false, true),

    /** A 4-byte little-endian date stamp, skipped by the parser. */
    LEDATE("LEDATE", 10, 4, false, false);

    /** The name of the type as written in the config file (e.g., BESHORT). */
    private final String key;

    /**
     * An old numeric ID kept around so legacy code doesn't break. New code should use the enum names instead of these
     * numbers.
     */
    private final int legacyId;

    /** How many bytes this type reads at once, or -1 if the size is variable. */
    private final int fixedByteLength;

    /** True if multi-byte numbers are read with the most significant byte first. */
    private final boolean bigEndian;

    /** True if the parser knows how to handle this type. */
    private final boolean supported;

    /**
     * Sets up a standard data type, automatically assuming it reads big-endian numbers and is fully supported by the
     * parser.
     *
     * @param key the text name used in configuration files
     * @param legacyId the old-school identification number for backward compatibility
     * @param fixedByteLength the exact number of bytes this type occupies, or -1 if it varies
     */
    MagicDataType(String key, int legacyId, int fixedByteLength) {
        this(key, legacyId, fixedByteLength, true, true);
    }

    /**
     * Sets up a data type with full custom controls over its byte-reading order and whether the parser currently supports
     * it.
     *
     * @param key the text name used in configuration files
     * @param legacyId the old-school identification number for backward compatibility
     * @param fixedByteLength the exact number of bytes this type occupies, or -1 if it varies
     * @param bigEndian true if numbers are read biggest-part-first, or false for smallest-part-first
     * @param supported true if the parser is ready to evaluate this type, or false if it should be skipped
     */
    MagicDataType(String key, int legacyId, int fixedByteLength, boolean bigEndian, boolean supported) {
        this.key = key;
        this.legacyId = legacyId;
        this.fixedByteLength = fixedByteLength;
        this.bigEndian = bigEndian;
        this.supported = supported;
    }

    /** Returns the configuration text name for this type (e.g., "BESHORT"). */
    public String getKey() {
        return key;
    }

    /** Returns the old-school numeric ID for backwards compatibility. */
    public int getLegacyId() {
        return legacyId;
    }

    /** Returns how many bytes this type takes up, or -1 if it's variable. */
    public int getFixedByteLength() {
        return fixedByteLength;
    }

    /** Checks if this type reads bytes biggest-first. */
    public boolean isBigEndian() {
        return bigEndian;
    }

    /** Checks if the parser actually supports handling this type right now. */
    public boolean isSupported() {
        return supported;
    }

    /**
     * Finds a data type using its text name (case-insensitive).
     *
     * @param key the name of the type (e.g., "leshort")
     * @return the matching type, if found
     */
    public static Optional<MagicDataType> fromKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String upperKey = key.toUpperCase(Locale.ROOT);
        for (MagicDataType type : values()) {
            if (type.key.equals(upperKey)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /**
     * Finds a data type using its old numeric ID.
     *
     * @param legacyId the old ID number
     * @return the matching type, if found
     */
    public static Optional<MagicDataType> fromLegacyId(int legacyId) {
        for (MagicDataType type : values()) {
            if (type.legacyId == legacyId) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
