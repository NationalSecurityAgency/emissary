package emissary.place;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.Factory;

import com.google.common.base.Preconditions;
import jakarta.annotation.Nullable;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ServiceProviderPlace that supports on-demand refresh of its configuration
 */
public abstract class ServiceProviderRefreshablePlace extends ServiceProviderPlace {

    private final Object allocatorLock = new Object();
    private final AtomicBoolean invalidated = new AtomicBoolean(false);
    private final AtomicBoolean defunct = new AtomicBoolean(false);

    private Monitor monitor;

    public ServiceProviderRefreshablePlace() throws IOException {
        super();
    }

    public ServiceProviderRefreshablePlace(final String thePlaceLocation) throws IOException {
        super(thePlaceLocation);
    }

    protected ServiceProviderRefreshablePlace(final String configFile, @Nullable final String theDir, final String thePlaceLocation)
            throws IOException {
        super(configFile, theDir, thePlaceLocation);
    }

    protected ServiceProviderRefreshablePlace(final InputStream configStream, @Nullable final String theDir, final String thePlaceLocation)
            throws IOException {
        super(configStream, theDir, thePlaceLocation);
    }

    protected ServiceProviderRefreshablePlace(final InputStream configStream) throws IOException {
        super(configStream);
    }

    protected ServiceProviderRefreshablePlace(final String configFile, final String placeLocation) throws IOException {
        super(configFile, placeLocation);
    }

    protected ServiceProviderRefreshablePlace(final InputStream configStream, final String placeLocation) throws IOException {
        super(configStream, placeLocation);
    }

    /**
     * Refresh specific constructor, basically clones a place and swaps out the Namespace ref
     *
     * @param place the ServiceProviderRefreshablePlace to clone
     * @param register set true to register with directory place, false otherwise
     * @throws IOException if there is an issue trying to create the place
     */
    public ServiceProviderRefreshablePlace(final ServiceProviderRefreshablePlace place, final boolean register) throws IOException {
        super(place);

        if (!register) {
            // not registering so reuse some stuff
            this.placeLocation = place.placeLocation;
            this.serviceDescription = place.serviceDescription;
            this.keys.addAll(place.keys);
            this.denyList.addAll(place.denyList);
        }

        // reload the config files and get the place updates
        this.configLocs.addAll(place.configLocs);
        this.configG = loadConfigurator(configLocs, place.placeLocation);
        setupPlace(null, place.placeLocation, register);
    }

    @Override
    protected void setupPlace(@Nullable final String theDir, final String placeLocation, final boolean register) throws IOException {
        super.setupPlace(theDir, placeLocation, register);

        try {
            Preconditions.checkNotNull(this.configG, "The configurator is null");
            final var path = configG.findStringEntry("MONITORING_PATH");
            if (StringUtils.isNotBlank(path)) {
                final var intervalMinutes = configG.findLongEntry("MONITORING_INTERVAL_MINUTES", 15);
                this.monitor = new Monitor(this, path, intervalMinutes);
                logger.info("Monitoring [{}] for changes every {} minutes", path, intervalMinutes);
            }
        } catch (RuntimeException e) {
            logger.warn("Could not create file monitor, skipping!", e);
        }
    }

    /**
     * Get the invalid flag of the place. An invalidated place may indicate that the place has changes, such as new
     * configuration, and may trigger a follow-on process to reconfigure, reinitialize, or re-create the place.
     *
     * @return true if the place has been invalidated, false otherwise
     */
    public final boolean isInvalidated() {
        if (!this.invalidated.get() && this.monitor != null) {
            this.monitor.run();
        }
        return this.invalidated.get();
    }

    /**
     * Invalidate a place that need to be refreshed.
     */
    public final void invalidate() {
        invalidate("");
    }

    /**
     * Invalidate a place that need to be refreshed.
     *
     * @param reason an optional reason for place invalidation
     */
    public final void invalidate(final String reason) {
        logger.info("Place marked as invalidated {}", reason);
        this.invalidated.set(true);
    }

    /**
     * Reinitialize the place by reloading the configurator and reconfiguring the place. Must call {@link #invalidate()}
     * before attempting to refresh the place.
     */
    public final void refresh() {
        refresh(false);
    }

    /**
     * Reinitialize the place by reloading the configurator and reconfiguring the place. Must call {@link #invalidate()}
     * before attempting to refresh the place.
     *
     * @param full true unbind from the namespace and rebuild all directory keys, false to reuse existing keys and just
     *        refresh place configs
     */
    public final void refresh(final boolean full) {
        refresh(full, true);
    }

    /**
     * Reinitialize the place by reloading the configurator and reconfiguring the place. Must call {@link #invalidate()}
     * before attempting to refresh the place.
     *
     * @param full true unbind from the namespace and rebuild all directory keys, false to reuse existing keys and just
     *        refresh place configs
     * @param silent true to just log any issues that arise, false to throw runtime exceptions
     */
    public final void refresh(final boolean full, final boolean silent) {
        logger.trace("Waiting for lock in refresh()");
        synchronized (this.allocatorLock) {
            logger.debug("Attempting to refresh place...");
            if (!this.defunct.get()) {
                if (isInvalidated()) {
                    if (full) {
                        unbindFromNamespace();
                        new ArrayList<>(this.keys).forEach(this::removeKey);
                    }
                    Factory.create(this.getClass().getName(), this, full);
                    this.defunct.set(true);
                    logger.info("Place refresh performed successfully");
                } else {
                    if (!silent) {
                        throw new IllegalStateException("Cannot refresh place without first calling invalidate; no refresh performed");
                    }
                    logger.warn("Cannot refresh place without first calling invalidate; no refresh performed");
                }
            } else {
                if (!silent) {
                    throw new IllegalStateException("Error refreshing defunct place");
                }
                logger.warn("Error refreshing defunct place");
            }
        }
    }

    /**
     * Reload the {@link Configurator}
     *
     * @param configLocations the list of configuration files to load
     * @throws IOException if there is an issue loading the config
     */
    private Configurator loadConfigurator(@Nullable final List<String> configLocations, final String placeLocation) throws IOException {
        slogger.info("Reloading configurator using locations {}", configLocations);
        if (CollectionUtils.isNotEmpty(configLocations)) {
            return ConfigUtil.getConfigInfo(configLocations);
        }
        return loadConfigurator(placeLocation);
    }

    /**
     * Similar logic to a {@link org.apache.commons.io.monitor.FileAlterationMonitor} to trigger any registered
     * {@link FileAlterationObserver} at a specified interval, except that this does not spawn a monitoring thread
     */
    class Monitor {

        private final List<FileAlterationObserver> observers = new CopyOnWriteArrayList<>();
        private final long intervalMinutes;
        private Instant lastCheck;

        protected Monitor(final ServiceProviderRefreshablePlace place, final String path, final long intervalMinutes) throws IOException {
            Preconditions.checkNotNull(place, "Refreshable place cannot be null");
            Preconditions.checkArgument(StringUtils.isNotBlank(path), "Path cannot be blank");
            Preconditions.checkArgument(intervalMinutes > 0, "Monitoring interval is not greater than 0");

            final Path file = Paths.get(path);
            Preconditions.checkArgument(Files.exists(file), "Path does not exist");

            final FileAlterationObserver observer;
            if (Files.isDirectory(file)) {
                logger.debug("Monitoring directory {} for changes", file);
                observer = FileAlterationObserver.builder().setFile(file.toFile()).get();
            } else {
                final Path parent = file.getParent();
                final String fileName = file.getFileName().toString();
                logger.debug("Monitoring file {} in directory {} for changes", fileName, parent);
                observer = FileAlterationObserver.builder().setFile(parent.toFile()).setFileFilter(new NameFileFilter(fileName)).get();
            }

            final var listener = new RefreshListener(place);
            observer.addListener(listener);
            this.observers.add(observer);

            this.intervalMinutes = intervalMinutes;
            this.lastCheck = Instant.now();
        }

        synchronized void run() {
            if (Duration.between(this.lastCheck, Instant.now()).toMinutes() >= this.intervalMinutes) {
                logger.debug("Last file check was at {}, checking for changed files.", this.lastCheck);
                this.observers.forEach(FileAlterationObserver::checkAndNotify);
                this.lastCheck = Instant.now();
            }
        }

        class RefreshListener extends FileAlterationListenerAdaptor {

            private final ServiceProviderRefreshablePlace place;
            private long lastModified = -1L;

            RefreshListener(final ServiceProviderRefreshablePlace place) {
                this.place = place;
            }

            @Override
            public void onFileChange(final File file) {
                handleChangeEvent(file);
            }

            @Override
            public void onFileCreate(final File file) {
                handleChangeEvent(file);
            }

            @Override
            public void onFileDelete(final File file) {
                handleChangeEvent(file);
            }

            @Override
            public void onStop(final FileAlterationObserver observer) {
                if (hasChanges()) {
                    this.place.invalidate("due to a config change");
                }
            }

            private void handleChangeEvent(final File file) {
                logger.debug("Change event observed for {}", file);
                this.lastModified = Long.max(this.lastModified, file.lastModified());
            }

            private boolean hasChanges() {
                if (this.lastModified > 0L) {
                    final var lastMod = Instant.ofEpochMilli(this.lastModified);

                    // make sure no edits have occurred recently
                    final boolean aboveThreshold = Duration.between(lastMod, Instant.now()).toMinutes() > 2;
                    if (!aboveThreshold) {
                        logger.debug("Files have been recently modified, waiting till next cycle.");
                    }
                    return aboveThreshold;
                }
                return false;
            }
        }
    }
}
