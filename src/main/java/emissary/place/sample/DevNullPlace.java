package emissary.place.sample;

import emissary.core.IBaseDataObject;
import emissary.place.EmptyFormPlace;
import emissary.place.ServiceProviderPlace;

import java.io.IOException;

/**
 * This place is a sink hole for everything it registers for
 */
public class DevNullPlace extends ServiceProviderPlace implements EmptyFormPlace {
    /**
     * Create and register
     */
    public DevNullPlace(String configInfo, String dir, String placeLoc) throws IOException {
        super(configInfo, dir, placeLoc);
    }

    /**
     * Create for test
     */
    public DevNullPlace(String configInfo) throws IOException {
        super(configInfo, "DevNullPlace.www.example.com:8001");
    }

    public DevNullPlace() throws IOException {}

    /**
     * Consume the data object
     */
    @Override
    public void process(IBaseDataObject tData) {

        // The form that got us here
        tData.currentForm();
        int before = tData.currentFormSize();
        int after = nukeMyProxies(tData);
        logger.debug("Nuked {} of {} current form values leaving {}", (after - before), before, tData.getAllCurrentForms());
    }

}
