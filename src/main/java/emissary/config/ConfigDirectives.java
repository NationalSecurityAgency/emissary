package emissary.config;

/**
 * Names and operators shared by the legacy and structured config parsers.
 */
final class ConfigDirectives {

    static final String ASSIGN = "=";
    static final String REMOVE = "!=";
    static final String REMOVE_ALL = "*";

    static final String IMPORT_FILE = "IMPORT_FILE";
    static final String OPT_IMPORT_FILE = "OPT_IMPORT_FILE";
    static final String CREATE_DIRECTORY = "CREATE_DIRECTORY";
    static final String CREATE_FILE = "CREATE_FILE";

    static final String STRUCTURED_REMOVE = "!remove";
    static final String STRUCTURED_IMPORT = "!import";
    static final String STRUCTURED_OPT_IMPORT = "!opt-import";

    static final String NULL_VALUE = "<null>";
    static final String NULL_SUBSTITUTION = "NULL";

    private ConfigDirectives() {}
}
