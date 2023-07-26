package emissary.util;

public class SqlStringUtil {

    /**
     * This function strips away any '`' characters
     *
     * @param string String the raw string
     * @return Stripped string
     */
    public static String stripTicks(String string) {
        return string.replaceAll("`", "");
    }

    /**
     * This function strips away characters '`', '[', and ']'
     *
     * @param string String the raw string
     * @return Stripped string
     */
    public static String stripIdentifierMarks(String string) {
        return string.replace("`", "").replace("[", "").replace("]", "");
    }

    /**
     * This function trims all whitespace from the String, and then, if the remaining String starts with N', it then strips
     * that leading N. Then it checks for single quotes that wrap the remaining string and remove those.
     *
     * @param string String the raw string
     * @return Stripped string
     */
    public static String stripEdgeQuotes(String string) {
        string = fixUnicodeString(string);
        if (string.charAt(0) == '\'' && string.charAt(string.length() - 1) == '\'') {
            return string.substring(1, string.length() - 1);
        } else {
            return string;
        }
    }

    /**
     * This function trims all whitespace from the String, and then, if the remaining String starts with N', it then strips
     * that leading N.
     *
     * @param strIn String the raw string
     * @return Corrected string
     */
    public static String fixUnicodeString(String strIn) {
        if (strIn != null) {
            strIn = strIn.trim();
        } else {
            strIn = "";
        }
        if (strIn != null && strIn.length() > 0) {
            if (strIn.charAt(0) == 'N') {
                if (strIn.charAt(1) == '\'') {
                    strIn = strIn.substring(1, strIn.length());
                }
            }
        }

        return strIn;
    }

    /**
     * This function splits the String by '.' and trims the whitespace between each '.'
     *
     * @param strIn String the raw string
     * @return String array of split string
     */
    public static String[] splitObjectNames(String strIn) {
        // The \\s* will trim the trim results.
        // String[] vals = strIn.split("\\s*\\.\\s*");
        String[] vals = strIn.split("\\.");
        for (int i = 0; i < vals.length; i++) {
            if (vals[i] != null) {
                vals[i] = vals[i].trim();
            } else {
                vals[i] = "";
            }
        }
        return vals;
    }
}
