package emissary.util.io;

import java.nio.file.Path;

/**
 * Listener interface for disk space monitoring events. Implementations receive callbacks when disk space thresholds are
 * crossed.
 */
public interface DiskSpaceListener {

    /**
     * Called when disk space usage exceeds the configured pause threshold.
     *
     * @param path the filesystem path being monitored
     * @param usedPercent the current disk usage as a percentage (0-100)
     * @param freeBytes the current number of free bytes available
     */
    void onDiskSpaceExceeded(Path path, double usedPercent, long freeBytes);

    /**
     * Called when disk space usage falls below the configured resume threshold.
     *
     * @param path the filesystem path being monitored
     * @param usedPercent the current disk usage as a percentage (0-100)
     * @param freeBytes the current number of free bytes available
     */
    void onDiskSpaceRecovered(Path path, double usedPercent, long freeBytes);
}
