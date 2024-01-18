package emissary.core;

/**
 * Keep a list of common current form and filetype values These could be built into some ontological interrelated
 * monstrosity in the future.
 */
public class Form {
    public static final String UNKNOWN = "UNKNOWN";
    public static final String ERROR = "ERROR";
    public static final String DONE = "DONE";
    public static final String EMPTY = "EMPTY_SESSION";

    public static final String TEXT = "TEXT";
    public static final String HTML = "HTML";

    // Form prefixes
    public static final String PREFIXES_LANG = "LANG-";

    // Form suffixes
    public static final String SUFFIXES_HTMLESC = "-HTMLESC";
    public static final String SUFFIXES_ERROR = "-ERROR";
    public static final String SUFFIXES_PROCESSED = "-PROCESSED";
    public static final String SUFFIXES_UNWRAPPED = "-UNWRAPPED";
    public static final String SUFFIXES_BROKEN = "-BROKEN";
    public static final String SUFFIXES_PASSWORD_PROTECTED = "-PASSWORD-PROTECTED";

    /** This class is not meant to be instantiated. */
    private Form() {}
}
