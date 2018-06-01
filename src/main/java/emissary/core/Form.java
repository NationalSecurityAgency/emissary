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

    /** This class is not meant to be instantiated. */
    private Form() {}
}
