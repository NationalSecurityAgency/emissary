package emissary.place.sample;

import emissary.core.IBaseDataObject;
import emissary.place.ServiceProviderPlace;

import java.io.IOException;

/**
 * This place performs no action other than holding everything it processes for a configurable amount of milliseconds
 */
public class DelayPlace extends ServiceProviderPlace {

    protected long delayTimeMillis = 2000L;

    /**
     * Create and register
     */
    public DelayPlace(String configInfo, String dir, String placeLoc) throws IOException {
        super(configInfo, dir, placeLoc);
        configurePlace();
    }

    /**
     * Create for test
     */
    public DelayPlace(String configInfo) throws IOException {
        super(configInfo, "DelayPlace.www.example.com:8001");
        configurePlace();
    }

    protected void configurePlace() {
        delayTimeMillis = configG.findLongEntry("DELAY_TIME_MILLIS", delayTimeMillis);
    }

    /**
     * Consume the data object
     */
    @Override
    public void process(IBaseDataObject tData) {
        if (logger.isDebugEnabled()) {
            logger.debug("Delay starting {}", tData.getAllCurrentForms());
        }
        try {
            Thread.sleep(delayTimeMillis);
        } catch (InterruptedException e) {
            logger.warn("Interrupted before delay expired", e);
            Thread.currentThread().interrupt();
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Delay ended {}", tData.getAllCurrentForms());
        }
    }
}
