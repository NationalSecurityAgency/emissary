package emissary.place;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.Factory;

import jakarta.annotation.Nullable;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ServiceProviderPlace that supports on-demand refresh of its configuration
 */
public abstract class ServiceProviderRefreshablePlace extends ServiceProviderPlace {

    private static final Logger logger = LoggerFactory.getLogger(ServiceProviderRefreshablePlace.class);

    private final Object allocatorLock = new Object();
    private final AtomicBoolean invalidated = new AtomicBoolean(false);
    private final AtomicBoolean defunct = new AtomicBoolean(false);

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
        logger.info("Place[{}] being marked as invalidated", this.getPlaceName());
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
     */
    public final void refresh(final boolean force) {
        refresh(force, true);
    }

    public final void refresh(final boolean force, final boolean silent) {
        logger.trace("Waiting for lock in refresh()");
        synchronized (this.allocatorLock) {
            final String placeName = this.getPlaceName();
            logger.debug("Attempting to refresh place[{}]...", placeName);
            if (!this.defunct.get()) {
                if (isInvalidated()) {
                    if (force) {
                        unbindFromNamespace();
                        new ArrayList<>(this.keys).forEach(this::removeKey);
                    }
                    Factory.create(this.getClass().getName(), this, force);
                    this.defunct.set(true);
                    logger.info("Place[{}] refresh performed successfully", placeName);
                } else {
                    if (!silent) {
                        throw new IllegalStateException(
                                "Cannot refresh place without first calling invalidate; no refresh performed for " + placeName);
                    }
                    logger.warn("Cannot refresh place without first calling invalidate; no refresh performed for {}", placeName);
                }
            } else {
                if (!silent) {
                    throw new IllegalStateException("Error refreshing, DEFUNCT<" + placeName + ">");
                }
                logger.warn("DEFUNCT<{}>", placeName);
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
        logger.debug("Reloading configurator using locations {}", configLocations);
        if (CollectionUtils.isNotEmpty(configLocations)) {
            return ConfigUtil.getConfigInfo(configLocations);
        }
        return loadConfigurator(placeLocation);
    }
}
