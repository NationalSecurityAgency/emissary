package emissary.kff;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.Factory;
import emissary.kff.KffFilter.FilterType;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Loads a chain of file filter specified by the configuration subsystem Expects to find a configuration file with a
 * list containing class names that implement known or dupe type filter working either against file stores of hashes or
 * database tables of hashes, plus a list of the algorithms that are desired to use. This should be a superset of the
 * algorithms required by all of the filter in the chain and those desired as end product in their own right. This is a
 * singleton implementation.
 */
public class KffChainLoader {

    private static final Logger logger = LoggerFactory.getLogger(KffChainLoader.class);

    @Nullable
    @SuppressWarnings("NonFinalStaticField")
    private static KffChain theInstance = null;

    @SuppressWarnings("NonFinalStaticField")
    private static Map<String, String> classes;

    /**
     * Take away the public constructor
     */
    private KffChainLoader() {}

    /**
     * Construct KFF chain elements from the specified configG or return the already constructed instance.
     */
    public static synchronized KffChain getChainInstance() {
        if (theInstance == null) {
            KffChain chain = new KffChain();
            try {
                Configurator configG = ConfigUtil.getConfigInfo(KffChain.class);
                classes = configG.findStringMatchMap("KFF_IMPL_");
                loadFrom(chain, configG.findStringMatchMap("KFF_FILE_KNOWN_"), FilterType.IGNORE);
                loadFrom(chain, configG.findStringMatchMap("KFF_FILE_DUPE_"), FilterType.DUPLICATE);

                chain.setMinDataSize(configG.findIntEntry("KFF_MIN_SIZE", 0));
                Set<String> algs = configG.findEntriesAsSet("KFF_ALG");
                chain.setAlgorithms(algs);
            } catch (IOException iox) {
                logger.debug("No configuration for Known File Filter. Continuing...");
            }
            theInstance = chain;
            logger.debug("KFF Chain loaded with {} filter using algorithms {}", theInstance.size(), theInstance.getAlgorithms());
        }
        return theInstance;
    }


    /**
     * Load a set from one of the keys into the chain
     *
     * @param chain the chain we are loading
     * @param m map of config entries items
     * @param filterType either IGNORE, KNOWN, or DUPE filter
     * @return number of filter loaded onto chain
     */
    private static int loadFrom(KffChain chain, Map<String, String> m, FilterType filterType) {
        int countLoaded = 0;

        // Load KFF File filter
        for (Map.Entry<String, String> entry : m.entrySet()) {
            String key = entry.getKey();
            String name = entry.getValue();
            try {
                String clazz = classes.get(key);
                if (clazz == null || clazz.length() == 0) {
                    // cannot construct a null class for key
                    continue;
                }

                // see if known KffType
                KffFilter k;
                try {
                    k = (KffFilter) Factory.create(clazz, name, key, filterType);
                } catch (RuntimeException x) {
                    logger.warn("Cannot create KffFilter, using default", x);
                    k = new KffFile(name, key, filterType);
                }

                chain.addFilter(k);
                countLoaded++;
            } catch (IOException e) {
                logger.error("Exception creating KFF chain element", e);
            }
        }
        return countLoaded;
    }

    /**
     * Load the configured chain and run some data
     */
    @SuppressWarnings("SystemOut")
    public static void main(String[] args) throws Exception {
        KffChain kff = getChainInstance();

        for (int i = 0; i < args.length; i++) {
            try (InputStream is = Files.newInputStream(Paths.get(args[i]))) {
                byte[] buffer = IOUtils.toByteArray(is);

                KffResult r = kff.check(args[i], buffer);
                System.out.println(args[i] + ": known=" + r.isKnown());
                System.out.println("   CRC32: " + r.getCrc32());
                for (String s : r.getResultNames()) {
                    System.out.println("   " + s + ": " + r.getResultString(s));
                }
            }
        }
    }
}
