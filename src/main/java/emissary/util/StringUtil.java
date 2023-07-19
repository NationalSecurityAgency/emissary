package emissary.util;

import org.jdom2.Verifier;

public class StringUtil {

    /**
     * This function corrects the string before placing it in an XML doc (not all characters are allowed in XML)
     *
     * @param in String the raw string
     * @return String the corrected string where invalid characters become "/0x"+ascii value of character
     */
    public static String XMLcorrect(String in) {
        // this is stolen from the implementation of Verifier.checkCharacterData(in);
        for (int i = 0, len = in.length(); i < len; i++) {
            char c = in.charAt(i);
            if (!(c > 0x1F && c < 0xD800)) { // for performance
                if (!Verifier.isXMLCharacter(c)) {
                    return in.substring(0, i) + "/0x" + Integer.toHexString(c) + XMLcorrect(in.substring(i + 1));
                }
            }
        }
        return in;
    }

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
     * This function strips away single edge quotes
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
     * This function returns the correct unicode
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
     * This function splits string by '\.'
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

    /** This class is not meant to be instantiated. */
    private StringUtil() {}
}
