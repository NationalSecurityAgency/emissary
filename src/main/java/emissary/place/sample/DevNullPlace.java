package emissary.place.sample;

import java.io.IOException;

import emissary.core.IBaseDataObject;
import emissary.place.ServiceProviderPlace;

/**
 * This place is a sink hole for everything it registers for
 */
public class DevNullPlace extends ServiceProviderPlace implements emissary.place.EmptyFormPlace {
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
        logger.debug("Nuked " + (after - before) + " of " + before + " current form values leaving " + tData.getAllCurrentForms());
    }

    /**
     * Test run
     */
    public static void main(String[] argv) {
        mainRunner(DevNullPlace.class.getName(), argv);
    }
}
