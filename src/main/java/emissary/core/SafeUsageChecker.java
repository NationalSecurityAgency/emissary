package emissary.core;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.channels.SeekableByteChannelFactory;
import emissary.util.ByteUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility for validating that Places safely interact with IBDO payloads in byte array form. Specifically, this class
 * helps with validating that changes to a IBDO's payload are followed by a call to the
 * {@link IBaseDataObject#setData(byte[]) setData(byte[])}, {@link IBaseDataObject#setData(byte[], int, int)
 * setData(byte[], int, int)}, or {@link IBaseDataObject#setChannelFactory(SeekableByteChannelFactory)
 * setChannelFactory(SeekableByteChannelFactory)} method.
 */
public class SafeUsageChecker {
    protected static final Logger LOGGER = LoggerFactory.getLogger(SafeUsageChecker.class);

    public static final String ENABLED_KEY = "ENABLED";
    public static final boolean ENABLED_FROM_CONFIGURATION;
    public static final String UNSAFE_MODIFICATION_DETECTED = "Detected unsafe changes to IBDO byte array contents";

    // to minimize I/O, we only want to read the config file once regardless of the number of instances created
    static {
        boolean enabledFromConfiguration = false;

        try {
            Configurator configurator = ConfigUtil.getConfigInfo(SafeUsageChecker.class);

            enabledFromConfiguration = configurator.findBooleanEntry(ENABLED_KEY, enabledFromConfiguration);
        } catch (IOException e) {
            LOGGER.debug("Could not get configuration!", e);
        }

        ENABLED_FROM_CONFIGURATION = enabledFromConfiguration;
    }

    /**
     * Cache that records each {@literal byte[]} reference made available to IBDO clients, along with a sha256 hash of the
     * array contents. Used for determining whether the clients modify the array contents without explicitly pushing those
     * changes back to the IBDO
     */
    private final Map<byte[], String> cache = new HashMap<>();
    public final boolean enabled;

    public SafeUsageChecker() {
        enabled = ENABLED_FROM_CONFIGURATION;
    }

    public SafeUsageChecker(Configurator configurator) {
        enabled = configurator.findBooleanEntry(ENABLED_KEY, ENABLED_FROM_CONFIGURATION);
    }

    /**
     * Resets the snapshot cache
     */
    public void reset() {
        if (enabled) {
            cache.clear();
        }
    }

    /**
     * Stores a new integrity snapshot
     * 
     * @param bytes byte[] for which a snapshot should be captured
     */
    public void recordSnapshot(final byte[] bytes) {
        if (enabled) {
            cache.put(bytes, ByteUtil.sha256Bytes(bytes));
        }
    }


    /**
     * Resets the cache and stores a new integrity snapshot
     * 
     * @param bytes byte[] for which a snapshot should be captured
     */
    public void resetCacheThenRecordSnapshot(final byte[] bytes) {
        if (enabled) {
            reset();
            recordSnapshot(bytes);
        }
    }

    /**
     * Uses the snapshot cache to determine whether any of the byte arrays have unsaved changes
     */
    public void checkForUnsafeDataChanges() {
        if (enabled) {
            boolean isUnsafe = cache.entrySet().stream().anyMatch(e -> !ByteUtil.sha256Bytes(e.getKey()).equals(e.getValue()));
            if (isUnsafe) {
                LOGGER.warn(UNSAFE_MODIFICATION_DETECTED);
            }
            reset();
        }
    }
}
