package emissary.place.sample;

import java.io.IOException;

import emissary.core.IBaseDataObject;
import emissary.place.ServiceProviderPlace;

/**
 * This is the main ToLower program.
 *
 * Creation Date: 10/06/2003
 * 
 **/
public class ToLowerPlace extends ServiceProviderPlace {

    private String newForm = "LOWER_CASE";
    private String endForm = "FINI";

    /**
     * The remote constructor
     */
    public ToLowerPlace(String cfgInfo, String dir, String placeLoc) throws IOException {
        super(cfgInfo, dir, placeLoc);
        configurePlace();
    }

    /**
     * The static standalone (test) constructor
     */
    public ToLowerPlace(String cfgInfo) throws IOException {
        super(cfgInfo, "TestToLowerPlace.foo.com:8003");
        configurePlace();
    }

    /**
     * Set up place specific information from the config file, load JNI, etc.
     */
    private void configurePlace() {

        // Set configuration items from ToLowerPlace.cfg
        newForm = configG.findStringEntry("NEW_FORM", newForm);
        endForm = configG.findStringEntry("END_FORM", endForm);
    }

    /**
     * Consume a DataObject, and return a transformed one.
     */
    @Override
    public void process(IBaseDataObject d) {

        // Process the data. Get it with d.data().
        byte[] data = d.data();

        for (int i = 0; i < data.length; i++) {
            if (Character.isUpperCase((char) data[i])) {
                data[i] = (byte) Character.toLowerCase((char) data[i]);
            }
        }

        if (d.transformHistory().size() < 10) {
            d.setCurrentForm(newForm);
        } else {
            d.setCurrentForm(endForm);
        }
    }

    /**
     * Test standalone main
     */
    public static void main(String[] argv) {
        mainRunner(ToLowerPlace.class.getName(), argv);
    }
}
