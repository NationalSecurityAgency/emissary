package emissary.util.io;

import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Supported configuration file formats.
 * <p>
 * This is the single source of truth for config suffixes. Discovery, resource-name normalization, extraction, and
 * parser selection all derive their suffix lists from here so that adding a format does not require updating several
 * unrelated tables.
 */
public enum ConfigFormat {
    /** Legacy line-oriented config. */
    CFG(".cfg", false),
    /** YAML config. */
    YAML(".yaml", true),
    /** YAML config using the short suffix. */
    YML(".yml", true),
    /** TOML config. */
    TOML(".toml", true),
    /** JSON config. */
    JSON(".json", true);

    private static final List<String> SUFFIXES;

    static {
        final List<String> suffixes = new ArrayList<>();
        for (final ConfigFormat format : values()) {
            suffixes.add(format.suffix);
        }
        SUFFIXES = Collections.unmodifiableList(suffixes);
    }

    private final String suffix;
    private final boolean structured;

    ConfigFormat(final String suffix, final boolean structured) {
        this.suffix = suffix;
        this.structured = structured;
    }

    /**
     * Get the canonical, lower-case suffix for this format.
     *
     * @return the suffix, including the leading dot
     */
    public String getSuffix() {
        return this.suffix;
    }

    /**
     * Whether this format is parsed structurally rather than with the legacy line tokenizer.
     *
     * @return true for structured formats
     */
    public boolean isStructured() {
        return this.structured;
    }

    /**
     * Recognize a config format from a filename, case-insensitively.
     *
     * @param name the filename or logical name
     * @return the matching format, or null when the name has no supported config suffix
     */
    @Nullable
    public static ConfigFormat fromName(@Nullable final String name) {
        if (name == null) {
            return null;
        }
        final String lower = name.toLowerCase(Locale.ROOT);
        for (final ConfigFormat format : values()) {
            if (lower.endsWith(format.suffix)) {
                return format;
            }
        }
        return null;
    }

    /**
     * All supported config suffixes in discovery order.
     *
     * @return an immutable list of suffixes
     */
    public static List<String> suffixes() {
        return SUFFIXES;
    }
}
