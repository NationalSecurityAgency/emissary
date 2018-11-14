package emissary.kff;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import emissary.core.Factory;
import emissary.kff.KffFilter.FilterType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads a chain of file filter specified by the configuration subsystem Expects to find a configuration file with a
 * list containing class names that implement known or dupe type filter working either against file stores of hashes or
 * database tables of hashes, plus a list of the algorithms that are desired to use. This should be a superset of the
 * algorithms required by all of the filter in the chain and those desired as end product in their own right. This is a
 * singleton implementation.
 */
public class KffChainLoader {

    private static final Logger logger = LoggerFactory.getLogger(KffChainLoader.class);

    private static int FILE_TYPE = 1;
    private static int DB_TYPE = 2;

    private static KffChain theInstance = null;
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
                emissary.config.Configurator configG = emissary.config.ConfigUtil.getConfigInfo(KffChain.class);
                classes = configG.findStringMatchMap("KFF_IMPL_");
                loadFrom(chain, configG.findStringMatchMap("KFF_FILE_KNOWN_"), FILE_TYPE, FilterType.Ignore);
                loadFrom(chain, configG.findStringMatchMap("KFF_DB_KNOWN_"), DB_TYPE, FilterType.Ignore);
                loadFrom(chain, configG.findStringMatchMap("KFF_FILE_DUPE_"), FILE_TYPE, FilterType.Duplicate);
                loadFrom(chain, configG.findStringMatchMap("KFF_DB_DUPE_"), DB_TYPE, FilterType.Duplicate);

                chain.setMinDataSize(configG.findIntEntry("KFF_MIN_SIZE", 0));
                Set<String> algs = configG.findEntriesAsSet("KFF_ALG");
                chain.setAlgorithms(algs);
            } catch (IOException iox) {
                logger.debug("No configuration for Known File Filter. Continuing...");
            }
            theInstance = chain;
            logger.debug("KFF Chain loaded with " + theInstance.size() + " filter using algorithms " + theInstance.getAlgorithms());
        }
        return theInstance;
    }


    /**
     * Load a set from one of the keys into the chain
     * 
     * @param chain the chain we are loading
     * @param m map of config entries items
     * @param kffType either FILE or DB type
     * @param filterType either IGNORE, KNOWN, or DUPE filter
     * @return number of filter loaded onto chain
     */
    private static int loadFrom(KffChain chain, Map<String, String> m, int kffType, KffFilter.FilterType filterType) {
        int countLoaded = 0;

        // Load KFF File filter
        for (Map.Entry<String, String> entry : m.entrySet()) {
            String key = entry.getKey();
            String name = entry.getValue();
            try {
                KffFilter k = null;
                String clazz = classes.get(key);
                if (clazz == null || clazz.length() == 0) {
                    logger.warn("no way I can construct a null class for " + key);
                    continue;
                }

                if (kffType == FILE_TYPE) {
                    try {
                        k = (KffFilter) Factory.create(clazz, new Object[] {name, key, filterType});
                    } catch (Exception x) {
                        logger.warn("Cannot create KffFilter, using default", x);
                        k = new KffFile(name, key, filterType);
                    }
                } else {
                    logger.error("Unknown kff type " + kffType);
                }

                if (k != null) {
                    chain.addFilter(k);
                    logger.debug("KFF Chain element " + name + " for " + key);
                    countLoaded++;
                }
            } catch (IOException e) {
                logger.error("Exception creating KFF chain element", e);
            }
        }
        return countLoaded;
    }

    /**
     * Load the configured chain and run some data
     */
    public static void main(String[] args) throws Exception {
        KffChain kff = getChainInstance();

        for (int i = 0; i < args.length; i++) {
            FileInputStream is = new FileInputStream(args[i]);
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();

            KffResult r = kff.check(args[i], buffer);
            System.out.println(args[i] + ": known=" + r.isKnown());
            System.out.println("   CRC32: " + r.getCrc32());
            for (String s : r.getResultNames()) {
                System.out.println("   " + s + ": " + r.getResultString(s));
            }
        }
    }
}
