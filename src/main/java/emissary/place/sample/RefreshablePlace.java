package emissary.place.sample;

import emissary.core.IBaseDataObject;
import emissary.place.ServiceProviderRefreshablePlace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * This place performs no action other than holding everything it processes for a configurable amount of milliseconds
 */
public class RefreshablePlace extends ServiceProviderRefreshablePlace {

    private static final Logger logger = LoggerFactory.getLogger(RefreshablePlace.class);

    public static final long DEFAULT_CONFIG = 2_000L;

    private long sampleConfig = DEFAULT_CONFIG;

    /**
     * Create and register
     */
    public RefreshablePlace(final String configInfo, final String dir, final String placeLoc) throws IOException {
        super(configInfo, dir, placeLoc);
        configurePlace();
    }

    /**
     * Create for test
     */
    public RefreshablePlace(final String configInfo) throws IOException {
        super(configInfo, "RefreshablePlace.www.example.com:8001");
        configurePlace();
    }

    public RefreshablePlace(final ServiceProviderRefreshablePlace place) throws IOException {
        super(place);
        configurePlace();
    }

    protected void configurePlace() {
        if (configG == null) {
            logger.warn("Configurator is null, unexpected behavior may occur!");
            return;
        }
        this.sampleConfig = configG.findLongEntry("SAMPLE_CONFIG", DEFAULT_CONFIG);
    }

    /**
     * Consume the data object
     */
    @Override
    public void process(final IBaseDataObject tData) {
        logger.info("SAMPLE_CONFIG {}", this.sampleConfig);
    }
}
