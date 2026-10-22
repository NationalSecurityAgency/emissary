package emissary.output.formatter.filter;

/**
 * Shared configuration parameter names for the formatter output filters.
 */
public final class ConfigKeys {

    /** Output filter classes, applied in declared order */
    public static final String OUTPUT_FILTER = "OUTPUT_FILTER";

    /** How a filter reads its lists: {@code deny} (drop listed) or {@code allow} (whitelist) */
    public static final String FILTER_MODE = "FILTER_MODE";

    /** View list entries, read per {@code FILTER_MODE} polarity */
    public static final String VIEWS = "VIEWS";

    /** Parameter list entries, read per {@code FILTER_MODE} polarity */
    public static final String PARAMS = "PARAMS";

    /** Parameter value entries: {@code PARAM_VALUE_<KEY> = comma separated values, honoring FILTER_MODE polarity} */
    public static final String PARAM_VALUE = "PARAM_VALUE_";

    /** View list entry format parameters */
    public static final String VIEW_NAME_CHARS = "VIEW_NAME_CHARS";
    public static final String FILETYPE_FORMAT = "FILETYPE_FORMAT";
    public static final String VIEW_NAME_FORMAT = "VIEW_NAME_FORMAT";

    /** Metadata parameter name prefixes to strip */
    public static final String STRIP_PARAM_PREFIX = "STRIP_PARAM_PREFIX";

    private ConfigKeys() {}
}
