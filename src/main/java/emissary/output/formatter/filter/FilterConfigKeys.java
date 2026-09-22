package emissary.output.formatter.filter;

/**
 * Shared configuration parameter names for the formatter content and metadata allow/deny filters.
 */
public final class FilterConfigKeys {

    /** content (view) list parameters */
    public static final String DENYLIST = "DENYLIST";
    public static final String ALLOWLIST = "ALLOWLIST";

    /** filter classes layered onto the chains, in declared order */
    public static final String CONTENT_FILTER = "CONTENT_FILTER";
    public static final String METADATA_FILTER = "METADATA_FILTER";

    /** content list entry format parameters */
    public static final String ALLOWED_NAME_CHARS = "DENYLIST_ALLOWED_NAME_CHARS";
    public static final String FILETYPE_FORMAT = "DENYLIST_FILETYPE_FORMAT";
    public static final String VIEW_NAME_FORMAT = "DENYLIST_VIEW_NAME_FORMAT";

    /** metadata (parameter) list parameters */
    public static final String DENYLIST_FIELD = "DENYLIST_FIELD";
    public static final String DENYLIST_PREFIX = "DENYLIST_PREFIX";
    public static final String DENYLIST_VALUE = "DENYLIST_VALUE_";
    public static final String ALLOWLIST_FIELD = "ALLOWLIST_FIELD";
    public static final String ALLOWLIST_PREFIX = "ALLOWLIST_PREFIX";
    public static final String ALLOWLIST_VALUE = "ALLOWLIST_VALUE_";

    /** metadata parameter prefix stripping */
    public static final String STRIP_PARAM_PREFIX = "STRIP_PARAM_PREFIX";

    private FilterConfigKeys() {}
}
