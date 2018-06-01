package emissary.util;

import java.io.IOException;
import java.util.HashMap;

/**
 * Load the JavaCharSet implementation from the file system using the emissary.config.Configurator interface. This
 * separation allows the JavaCharSet implementation to be loaded other ways without requiring dependence on the
 * emissary.config package. Specifically JavaCharSet can be used in a PL/SQL Java function or procedure.
 */
public class JavaCharSetLoader {

    public static void initialize() {

        if (JavaCharSet.isInitialized()) {
            return;
        }

        try {
            final emissary.config.Configurator config = emissary.config.ConfigUtil.getConfigInfo(JavaCharSet.class);
            JavaCharSet.initialize(config.findStringMatchMap("CHARSET_", true));
        } catch (IOException e) {
            System.err.println("JavaCharSet: " + e);
            JavaCharSet.initialize(new HashMap<String, String>());
        }
    }

    /** This class is not meant to be instantiated. */
    private JavaCharSetLoader() {}

    public static void main(final String[] args) {
        JavaCharSetLoader.initialize();
        for (int i = 0; args != null && i < args.length; i++) {
            System.out.println(args[i] + " --> " + JavaCharSet.get(args[i]));
        }
    }
}
