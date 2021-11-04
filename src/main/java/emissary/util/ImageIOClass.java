package emissary.util;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.spi.IIORegistry;

import emissary.config.ConfigEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to support loading the imported ImageIO classes. This behavior was moved here due to the timing issues seen
 * when attempting to initialize these classes in the "Places" classes.
 *
 * The collection of classes in imageIOClasses are used in the application, and are registered here to be used later on.
 *
 * The collection of classes in surplusImageIOClasses are not needed for the application, but are loaded by dependencies
 * or elsewhere. We attempt to deregister these classes if they are currently loaded in order to prevent issues with
 * having multiple classes loaded with the same name.
 */
public class ImageIOClass {

    private static List<ConfigEntry> imageIOClasses = new ArrayList<ConfigEntry>();
    private static List<ConfigEntry> surplusImageIOClasses = new ArrayList<ConfigEntry>();

    private static boolean initialized = false;
    private static Logger logger = LoggerFactory.getLogger(ImageIOClass.class);

    /**
     * Load classes from config file
     */
    public static synchronized void initialize(final List<ConfigEntry> imageIOClass, final List<ConfigEntry> surplusImageIOClass) {
        imageIOClasses.addAll(imageIOClass);
        surplusImageIOClasses.addAll(surplusImageIOClass);
        registerImageProcessingClasses();
        initialized = true;
    }

    /**
     * Return initialization status
     */
    public static synchronized boolean isInitialized() {
        return initialized;
    }

    /**
     * For each class in the config file, attempt to register or deregister it as appropriate.
     */
    private static void registerImageProcessingClasses() {
        if (!imageIOClasses.isEmpty()) {
            IIORegistry registry = IIORegistry.getDefaultInstance();
            for (ConfigEntry configEntry : imageIOClasses) {
                try {
                    String klass = configEntry.getValue();
                    Class<?> clazz = Class.forName((klass));
                    Constructor<?> ctor = clazz.getConstructor();
                    Object object = ctor.newInstance();
                    registry.registerServiceProvider(object);
                    logger.debug("registered {}", klass);
                } catch (Exception e) {
                    logger.warn("Unable to find class {}", configEntry.getValue());
                }
            }
        }
        if (!surplusImageIOClasses.isEmpty()) {
            IIORegistry registry = IIORegistry.getDefaultInstance();
            for (ConfigEntry configEntry : surplusImageIOClasses) {
                try {
                    String klass = configEntry.getValue();
                    Object provider = registry.getServiceProviderByClass(Class.forName((klass)));
                    registry.deregisterServiceProvider(provider);
                    logger.debug("deregistered {}", klass);
                } catch (Exception e) {
                    logger.warn("Unable to delete class {}", configEntry.getValue());
                }
            }
        }
    }
}
