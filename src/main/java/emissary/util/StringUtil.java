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
                    if (i == len) {
                        return in.substring(0, i) + "/0x" + Integer.toHexString(c);
                    }
                    return in.substring(0, i) + "/0x" + Integer.toHexString(c) + XMLcorrect(in.substring(i + 1));
                }
            }
        }
        return in;
    }

    /** This class is not meant to be instantiated. */
    private StringUtil() {}
}
