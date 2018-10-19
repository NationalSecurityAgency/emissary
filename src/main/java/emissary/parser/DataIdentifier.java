package emissary.parser;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.util.shell.Executrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple base class for doing data type identification This simple implementation can only match constant strings
 * against data. The things to match are read from a config file
 */
public class DataIdentifier {
    // Logger
    private static final Logger logger = LoggerFactory.getLogger(DataIdentifier.class);

    // Default value
    public static final String UNKNOWN_TYPE = "simple";

    // Size of string to test
    protected int DATA_ID_STR_SZ = 100;

    // Things we know how to identify
    protected Map<String, String> TYPES = new HashMap<String, String>();

    /**
     * Create the id engine
     */
    public DataIdentifier() {
        configure(null);
    }

    /**
     * Create the id engine with the specified config info
     */
    public DataIdentifier(Configurator config) {
        configure(config);
    }

    protected void configure(Configurator config) {
        try {
            if (config == null) {
                config = ConfigUtil.getConfigInfo(this.getClass());
            }
            TYPES = config.findStringMatchMap("TYPE_", Configurator.PRESERVE_CASE);
            logger.debug("Configured with " + TYPES.size() + " identifiction types");
        } catch (IOException iox) {
            logger.debug("No configuration info found");
        }
    }

    /**
     * Return a slice as string for testing
     * 
     * @param data the bytes to slice
     * @param limit max length to use for testing
     */
    protected String getTestString(byte[] data, int limit) {
        if (data.length < limit) {
            return new String(data);
        }
        return new String(data, 0, limit);
    }

    /**
     * Return a slice as string for testing
     * 
     * @param data the bytes to slice
     * @return the slice
     */
    protected String getTestString(byte[] data) {
        return getTestString(data, DATA_ID_STR_SZ);
    }

    /**
     * Identify the data in the array
     * 
     * @param data array of data to identify
     */
    public String identify(byte[] data) {
        for (Map.Entry<String, String> entry : TYPES.entrySet()) {
            byte[] pattern = entry.getValue().getBytes();
            if (data.length < pattern.length) {
                continue;
            }
            boolean match = true;
            for (int i = 0; i < pattern.length; i++) {
                if (data[i] != pattern[i]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                logger.debug("Data identified as " + entry.getKey());
                return entry.getKey();
            }
        }
        logger.debug("No identification possible, returning UNKNOWN_TYPE");
        return UNKNOWN_TYPE;
    }

    /**
     * Get the size of data that is required for an id This is the maximum amount of data that the id algorithm will use,
     * more or less can be sent,
     * 
     * @see #getTestString(byte[])
     */
    public int getTestStringMaxSize() {
        return DATA_ID_STR_SZ;
    }

    public static void main(String[] args) throws Exception {
        DataIdentifier dataIdentifier = new DataIdentifier();

        for (String filename : args) {
            RandomAccessFile raf = new RandomAccessFile(filename, "r");
            byte[] data = Executrix.readDataFromFile(raf, 0, dataIdentifier.getTestStringMaxSize());
            String result = dataIdentifier.identify(data);
            System.out.println(filename + " : " + result);
        }
    }
}
