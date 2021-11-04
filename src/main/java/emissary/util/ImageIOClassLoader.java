package emissary.util;

import java.io.IOException;
import java.util.ArrayList;

import emissary.config.ConfigEntry;

/**
 * Load the ImageIOClass implementation from the file system using the emissary.config.Configurator interface.
 */
public class ImageIOClassLoader {

    /**
     * Read the ImageIO and Surplus ImageIO classes that will be registered/ deregistered from the config file to initialize
     * the ImageIOClass class
     */
    public static synchronized void initialize() {
        if (ImageIOClass.isInitialized()) {
            return;
        }
        try {
            final emissary.config.Configurator config = emissary.config.ConfigUtil.getConfigInfo(ImageIOClass.class);
            ImageIOClass.initialize(config.findStringMatchEntries("IMAGEIO_"), config.findStringMatchEntries("SURPLUS_IMAGEIO_"));
        } catch (IOException e) {
            System.err.println("ImageIOClassLoader: " + e);
            ImageIOClass.initialize(new ArrayList<ConfigEntry>(), new ArrayList<ConfigEntry>());
        }
    }
}
