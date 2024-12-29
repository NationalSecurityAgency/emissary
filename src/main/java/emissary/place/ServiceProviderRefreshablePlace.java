package emissary.place;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;

import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

/**
 * ServiceProviderPlace that supports on-demand refresh of its configuration
 */
public abstract class ServiceProviderRefreshablePlace extends ServiceProviderPlace {

    private static final Logger logger = LoggerFactory.getLogger(ServiceProviderRefreshablePlace.class);

    private final Object allocatorLock = new Object();
    private final AtomicBoolean invalidated = new AtomicBoolean(false);

    public ServiceProviderRefreshablePlace() throws IOException {}

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
     * Get the invalid flag of the place. An invalidated place may indicate that the place has changes, such as new
     * configuration, and may trigger a follow-on process to reconfigure, reinitialize, or re-create the place.
     *
     * @return true if the place has been invalidated, false otherwise
     */
    public final boolean isInvalidated() {
        return this.invalidated.get();
    }

    /**
     * Invalidate a place that need to be refreshed.
     */
    public final void invalidate() {
        logger.info("Place[{}] being marked as invalidated", getClass().getName());
        setInvalidated(true);
    }

    /**
     * Set the invalid flag of the place
     *
     * @param invalid true if place is invalid, false otherwise
     */
    private void setInvalidated(final boolean invalid) {
        this.invalidated.set(invalid);
    }

    /**
     * Reinitialize the place by reloading the configurator and reconfiguring the place. Must call {@link #invalidate()}
     * before attempting to refresh the place.
     */
    public final void refresh() {
        logger.trace("Waiting for lock in refresh()");
        synchronized (allocatorLock) {
            logger.debug("Attempting to refresh place using config locations");
            try {
                if (isInvalidated()) {
                    this.configG = reloadConfigurator(this.configLocs);
                    reconfigurePlace();
                    setInvalidated(false);
                    logger.info("Place refresh performed successfully");
                } else {
                    logger.warn("Cannot refresh place configuration without first calling invalidate; no reconfiguration performed");
                }
            } catch (IOException e) {
                logger.error("Failed to refresh configurator");
            }
        }
    }

    /**
     * Reinitialize the place by reloading the configurator and reconfiguring the place. Must call {@link #invalidate()}
     * before attempting to refresh the place.
     *
     * @param configStream the config data as an {@link InputStream}
     */
    public final void refresh(final InputStream configStream) {
        logger.trace("Waiting for lock in refresh(configStream)");
        synchronized (allocatorLock) {
            logger.debug("Attempting to refresh place using configStream");
            try {
                if (isInvalidated()) {
                    this.configG = reloadConfigurator(configStream);
                    reconfigurePlace();
                    setInvalidated(false);
                    logger.info("Place refresh performed successfully using configStream");
                } else {
                    logger.warn(
                            "Cannot refresh place configuration with configStream without first calling invalidate; no reconfiguration performed");
                }
            } catch (IOException e) {
                logger.error("Failed to refresh configurator using configStream");
            }
        }
    }

    protected abstract void reconfigurePlace() throws IOException;

    /**
     * Reload the {@link Configurator}
     *
     * @param configLocations the list of configuration files to load
     * @throws IOException if there is an issue loading the config
     */
    private static Configurator reloadConfigurator(@Nullable final List<String> configLocations) throws IOException {
        logger.debug("Reloading configurator using locations {}", configLocations);
        if (CollectionUtils.isNotEmpty(configLocations)) {
            return ConfigUtil.getConfigInfo(configLocations);
        }
        throw new IOException("No config locations specified");
    }

    /**
     * Reload the {@link Configurator}
     *
     * @param configStream the stream of configuration data
     * @throws IOException if there is an issue loading the config
     */
    private static Configurator reloadConfigurator(@Nullable final InputStream configStream) throws IOException {
        logger.debug("Reloading configurator using configStream");
        if (configStream != null) {
            return ConfigUtil.getConfigInfo(configStream);
        }
        throw new IOException("Null config stream supplied");
    }

}
