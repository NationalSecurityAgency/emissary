package emissary.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Convert any charset to a Java Charset name that a JDK 1.1 and up will understand. Just returns ones that need
 * remapping. Callers should be advised that getting a null back means the code set passed might be valid *as is* or it
 * might be completely bogus. We currently cannot tell.
 */
public class JavaCharSet {

    private static Map<String, String> charsets = new HashMap<String, String>();

    private static boolean initialized = false;

    /**
     * Load hash from config file
     */
    public static synchronized void initialize(final Map<String, String> mappings) {
        charsets.putAll(mappings);
        initialized = true;
    }

    /**
     * Return initialization status
     */
    public static synchronized boolean isInitialized() {
        return initialized;
    }

    /**
     * Look up the encoding and return the Java CharSet for it if different from the string passed in
     */
    public static String get(final String cs) {
        if (cs == null) {
            return null;
        }

        // Look up in the hash
        String s = cs.toUpperCase();
        String charSet = charsets.get(s);

        String enc = null;

        // If nothing look for an encoding inside a set of parens
        if (charSet == null) {
            final int start = s.indexOf("(");
            final int stop = s.indexOf(")");
            if (start > -1 && stop > start) {
                enc = s.substring(start + 1, stop);
                charSet = charsets.get(enc);
            }
        }

        // If nothing, clean the encoding tag and use it
        // ID phase can add -<TAG> things to just about
        // any encoding. It doesn't change the base value
        // of the characters to strip it out since we should
        // be processing those out anyway
        while (s.indexOf("-") != -1 && charSet == null) {
            s = s.substring(0, s.lastIndexOf("-"));
            charSet = charsets.get(s);
        }

        // Finally, try just the encoding stripped of -<TAG>, etc.
        while ((charSet == null) && (enc != null) && enc.contains("-")) {
            enc = enc.substring(0, enc.lastIndexOf("-"));
            charSet = charsets.get(enc);
        }

        // Use the supplied encoding as a last resort, may be null
        if (charSet == null) {
            charSet = enc;
        }

        return charSet;
    }

    /** This class is not meant to be instantiated. */
    private JavaCharSet() {}
}
