package emissary.util.io;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Monitors disk space for a specified path and notifies listeners when thresholds are crossed.
 * <p>
 * This monitor supports two types of thresholds:
 * <ul>
 * <li>Percentage-based: Triggers when disk usage exceeds a percentage (0-100)</li>
 * <li>Absolute size-based: Triggers when free space falls below a byte count</li>
 * </ul>
 * <p>
 * The monitor implements hysteresis with separate pause and resume thresholds to prevent rapid cycling between states.
 */
public class DiskSpaceMonitor {
    private static final Logger logger = LoggerFactory.getLogger(DiskSpaceMonitor.class);

    private final Path monitoredPath;
    private final long checkIntervalSeconds;
    private final Double pauseThresholdPercent;
    private final Double resumeThresholdPercent;
    private final Long pauseThresholdBytes;
    private final Long resumeThresholdBytes;
    private final List<DiskSpaceListener> listeners;
    private final ScheduledExecutorService scheduler;

    private volatile boolean isPaused = false;
    private volatile boolean isRunning = false;

    /**
     * Private constructor - use Builder to create instances.
     */
    private DiskSpaceMonitor(Path monitoredPath,
            long checkIntervalSeconds,
            Double pauseThresholdPercent,
            Double resumeThresholdPercent,
            Long pauseThresholdBytes,
            Long resumeThresholdBytes) {
        this.monitoredPath = monitoredPath;
        this.checkIntervalSeconds = checkIntervalSeconds;
        this.pauseThresholdPercent = pauseThresholdPercent;
        this.resumeThresholdPercent = resumeThresholdPercent;
        this.pauseThresholdBytes = pauseThresholdBytes;
        this.resumeThresholdBytes = resumeThresholdBytes;
        this.listeners = new CopyOnWriteArrayList<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> createDaemonThread(r, monitoredPath));
    }

    /**
     * Creates a daemon thread for the disk space monitor.
     *
     * @param runnable the runnable to execute
     * @param path the path being monitored (used in thread name)
     * @return a daemon thread
     */
    private static Thread createDaemonThread(Runnable runnable, Path path) {
        Thread thread = new Thread(runnable, "DiskSpaceMonitor-" + path.getFileName());
        thread.setDaemon(true);
        return thread;
    }

    /**
     * Starts disk space monitoring. Checks will be performed at the configured interval.
     */
    @SuppressWarnings("FutureReturnValueIgnored") // TODO: Ok to suppress?
    public synchronized void start() {
        if (isRunning) {
            logger.warn("DiskSpaceMonitor is already running for {}", monitoredPath);
            return;
        }

        isRunning = true;
        scheduler.scheduleAtFixedRate(
                this::checkDiskSpace,
                0, // initial delay
                checkIntervalSeconds,
                TimeUnit.SECONDS);
        logger.debug("Started DiskSpaceMonitor for {} with check interval of {} seconds",
                monitoredPath, checkIntervalSeconds);
    }

    /**
     * Stops disk space monitoring and shuts down the monitoring thread.
     */
    public synchronized void stop() {
        if (!isRunning) {
            return;
        }

        isRunning = false;
        scheduler.shutdownNow();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("DiskSpaceMonitor scheduler did not terminate within 5 seconds for {}", monitoredPath);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for DiskSpaceMonitor scheduler to terminate", e);
        }
        logger.debug("Stopped DiskSpaceMonitor for {}", monitoredPath);
    }

    /**
     * Adds a listener to be notified of disk space events.
     *
     * @param listener the listener to add
     */
    public void addListener(DiskSpaceListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a listener.
     *
     * @param listener the listener to remove
     */
    public void removeListener(DiskSpaceListener listener) {
        listeners.remove(listener);
    }

    /**
     * Checks disk space and triggers listeners if thresholds are crossed.
     */
    private void checkDiskSpace() {
        if (!isRunning) {
            return;
        }

        try {
            DiskSpaceInfo info = getDiskSpaceInfo();

            if (pauseThresholdPercent != null) {
                // Percentage-based monitoring
                checkPercentageThresholds(info);
            } else {
                // Absolute size-based monitoring
                checkAbsoluteSizeThresholds(info);
            }
        } catch (IOException e) {
            logger.error("Error checking disk space for {}", monitoredPath, e);
            // Continue monitoring despite error
        } catch (RuntimeException e) {
            logger.error("Unexpected error in disk space monitor for {}", monitoredPath, e);
            // Continue monitoring despite error
        }
    }

    /**
     * Checks percentage-based thresholds.
     */
    private void checkPercentageThresholds(DiskSpaceInfo info) {
        if (!isPaused && info.usedPercent >= pauseThresholdPercent) {
            // Threshold exceeded - transition to paused state
            isPaused = true;
            if (logger.isDebugEnabled()) {
                logger.debug("Disk space threshold exceeded: {}% used (threshold: {}%)",
                        String.format("%.2f", info.usedPercent), pauseThresholdPercent);
            }
            notifyListenersExceeded(info);
        } else if (isPaused && info.usedPercent < resumeThresholdPercent) {
            // Below resume threshold - transition to not paused state
            isPaused = false;
            if (logger.isDebugEnabled()) {
                logger.debug("Disk space recovered: {}% used (threshold: {}%)",
                        String.format("%.2f", info.usedPercent), resumeThresholdPercent);
            }
            notifyListenersRecovered(info);
        }
    }

    /**
     * Checks absolute size-based thresholds.
     */
    private void checkAbsoluteSizeThresholds(DiskSpaceInfo info) {
        if (!isPaused && info.freeBytes < pauseThresholdBytes) {
            // Free space too low - transition to paused state
            isPaused = true;
            logger.debug("Disk space threshold exceeded: {} bytes free (threshold: {} bytes)",
                    info.freeBytes, pauseThresholdBytes);
            notifyListenersExceeded(info);
        } else if (isPaused && info.freeBytes >= resumeThresholdBytes) {
            // Free space recovered - transition to not paused state
            isPaused = false;
            logger.debug("Disk space recovered: {} bytes free (threshold: {} bytes)",
                    info.freeBytes, resumeThresholdBytes);
            notifyListenersRecovered(info);
        }
    }

    /**
     * Notifies all listeners that disk space threshold has been exceeded.
     */
    private void notifyListenersExceeded(DiskSpaceInfo info) {
        for (DiskSpaceListener listener : listeners) {
            try {
                listener.onDiskSpaceExceeded(monitoredPath, info.usedPercent, info.freeBytes);
            } catch (RuntimeException e) {
                logger.error("Exception in DiskSpaceListener.onDiskSpaceExceeded for {}",
                        listener.getClass().getName(), e);
                // Continue notifying other listeners
            }
        }
    }

    /**
     * Notifies all listeners that disk space has recovered.
     */
    private void notifyListenersRecovered(DiskSpaceInfo info) {
        for (DiskSpaceListener listener : listeners) {
            try {
                listener.onDiskSpaceRecovered(monitoredPath, info.usedPercent, info.freeBytes);
            } catch (RuntimeException e) {
                logger.error("Exception in DiskSpaceListener.onDiskSpaceRecovered for {}",
                        listener.getClass().getName(), e);
                // Continue notifying other listeners
            }
        }
    }

    /**
     * Gets current disk space information for the monitored path.
     *
     * @return disk space information
     * @throws IOException if unable to access filesystem
     */
    private DiskSpaceInfo getDiskSpaceInfo() throws IOException {
        FileStore store = Files.getFileStore(monitoredPath);
        long totalBytes = store.getTotalSpace();
        long freeBytes = store.getUsableSpace();
        long usedBytes = totalBytes - freeBytes;
        double usedPercent = totalBytes > 0 ? (usedBytes * 100.0 / totalBytes) : 0.0;

        return new DiskSpaceInfo(freeBytes, usedPercent);
    }

    /**
     * Returns whether the monitor is currently in a paused state.
     *
     * @return true if paused, false otherwise
     */
    public boolean isPaused() {
        return isPaused;
    }

    /**
     * Returns whether the monitor is currently running.
     *
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Container for disk space information.
     */
    private static class DiskSpaceInfo {
        final long freeBytes;
        final double usedPercent;

        DiskSpaceInfo(long freeBytes, double usedPercent) {
            this.freeBytes = freeBytes;
            this.usedPercent = usedPercent;
        }
    }

    /**
     * Builder for creating DiskSpaceMonitor instances.
     */
    public static class Builder {
        private final Path monitoredPath;
        private long checkIntervalSeconds = 30;
        @Nullable
        private Double pauseThresholdPercent = null;
        @Nullable
        private Double resumeThresholdPercent = null;
        @Nullable
        private Long pauseThresholdBytes = null;
        @Nullable
        private Long resumeThresholdBytes = null;

        /**
         * Creates a new Builder for the specified path.
         *
         * @param monitoredPath the filesystem path to monitor
         */
        public Builder(Path monitoredPath) {
            if (monitoredPath == null) {
                throw new IllegalArgumentException("Monitored path cannot be null");
            }
            this.monitoredPath = monitoredPath;
        }

        /**
         * Sets the check interval in seconds.
         *
         * @param seconds the interval between disk space checks (must be &gt; 0)
         * @return this builder
         */
        public Builder checkInterval(long seconds) {
            if (seconds <= 0) {
                throw new IllegalArgumentException("Check interval must be positive");
            }
            this.checkIntervalSeconds = seconds;
            return this;
        }

        /**
         * Sets the percentage threshold for pausing.
         *
         * @param percent the disk usage percentage (0-100) at which to pause
         * @return this builder
         */
        public Builder pauseThresholdPercent(double percent) {
            if (percent < 0 || percent > 100) {
                throw new IllegalArgumentException("Pause threshold percent must be between 0 and 100");
            }
            this.pauseThresholdPercent = percent;
            return this;
        }

        /**
         * Sets the percentage threshold for resuming.
         *
         * @param percent the disk usage percentage (0-100) at which to resume
         * @return this builder
         */
        public Builder resumeThresholdPercent(double percent) {
            if (percent < 0 || percent > 100) {
                throw new IllegalArgumentException("Resume threshold percent must be between 0 and 100");
            }
            this.resumeThresholdPercent = percent;
            return this;
        }

        /**
         * Sets the absolute free space threshold for pausing.
         *
         * @param bytes the minimum free bytes at which to pause
         * @return this builder
         */
        public Builder pauseThresholdBytes(long bytes) {
            if (bytes < 0) {
                throw new IllegalArgumentException("Pause threshold bytes must be non-negative");
            }
            this.pauseThresholdBytes = bytes;
            return this;
        }

        /**
         * Sets the absolute free space threshold for resuming.
         *
         * @param bytes the minimum free bytes at which to resume
         * @return this builder
         */
        public Builder resumeThresholdBytes(long bytes) {
            if (bytes < 0) {
                throw new IllegalArgumentException("Resume threshold bytes must be non-negative");
            }
            this.resumeThresholdBytes = bytes;
            return this;
        }

        /**
         * Builds and returns a DiskSpaceMonitor instance.
         *
         * @return the configured DiskSpaceMonitor
         * @throws IllegalArgumentException if configuration is invalid
         */
        public DiskSpaceMonitor build() {
            // Validate that at least one threshold type is configured
            if (pauseThresholdPercent == null && pauseThresholdBytes == null) {
                throw new IllegalArgumentException(
                        "Either percentage or byte-based thresholds must be configured");
            }

            // Validate percentage thresholds
            if (pauseThresholdPercent != null) {
                if (resumeThresholdPercent == null) {
                    resumeThresholdPercent = pauseThresholdPercent;
                }
                if (resumeThresholdPercent >= pauseThresholdPercent) {
                    throw new IllegalArgumentException(
                            "Resume threshold percent (" + resumeThresholdPercent +
                                    ") must be less than pause threshold percent (" + pauseThresholdPercent + ")");
                }
                // Clear byte thresholds if percentage is configured
                pauseThresholdBytes = null;
                resumeThresholdBytes = null;
            }

            // Validate byte thresholds
            if (pauseThresholdBytes != null) {
                if (resumeThresholdBytes == null) {
                    resumeThresholdBytes = pauseThresholdBytes;
                }
                if (resumeThresholdBytes <= pauseThresholdBytes) {
                    throw new IllegalArgumentException(
                            "Resume threshold bytes (" + resumeThresholdBytes +
                                    ") must be greater than pause threshold bytes (" + pauseThresholdBytes + ")");
                }
            }

            return new DiskSpaceMonitor(
                    monitoredPath,
                    checkIntervalSeconds,
                    pauseThresholdPercent,
                    resumeThresholdPercent,
                    pauseThresholdBytes,
                    resumeThresholdBytes);
        }
    }
}
