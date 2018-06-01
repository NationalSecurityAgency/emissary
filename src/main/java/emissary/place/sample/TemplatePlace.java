package emissary.place.sample;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import emissary.core.IBaseDataObject;
import emissary.place.ServiceProviderPlace;

/**
 * This is the main Xxx program. It consists of ...
 *
 * @author Me
 *
 *         Creation Date: MM/DD/YYYY
 *
 **/
public class TemplatePlace extends ServiceProviderPlace {

    /**
     * The remote constructor
     *
     * @param cfgInfo the location of the config file or resource
     * @param dir the directory key to register with
     * @param placeLoc the key location for this instance
     */
    public TemplatePlace(String cfgInfo, String dir, String placeLoc) throws IOException {
        super(cfgInfo, dir, placeLoc);
        configurePlace();
    }

    /**
     * A stream constructor
     *
     * @param cfgInfo the configuration stream
     */
    public TemplatePlace(InputStream cfgInfo) throws IOException {
        super(cfgInfo, "TestTemplatePlace.foo.com:8003");
        configurePlace();
    }

    /**
     * A string config constructor
     *
     * @param cfgInfo the location of the config file or resource
     */
    public TemplatePlace(String cfgInfo) throws IOException {
        super(cfgInfo, "TestTemplatePlace.foo.com:8003");
        configurePlace();
    }

    /**
     * Set up place specific information from the config file, load JNI, etc.
     */
    private void configurePlace() {

        // Set configuration items from TemplatePlace.cfg
        @SuppressWarnings("unused")
        String myImportantStringVariable = configG.findStringEntry("LABEL_FROM_CFG_FILE", "some default value");

        @SuppressWarnings("unused")
        boolean myImportantBooleanVariable = configG.findBooleanEntry("LABEL_FOR_BOOLEAN", true);


        // Other code needed for constructor

    }

    /**
     * Consume a DataObject, and return a transformed one.
     *
     * @param d the incoming payload to process
     */
    @Override
    public List<IBaseDataObject> processHeavyDuty(IBaseDataObject d) {

        // Process the data. Get it with d.data().

        if (Boolean.TRUE /* it worked */) {

            d.setCurrentForm("WHAT_YOU_FOUND");

            // If you are a TRANSFORM type place replace the data
            // with d.setData()

            // The transform history is appended to automatically, don't add
            // that here but ...

            // If you are a MetaData creator you can add parameters
            // d.putParameter("NewParam","value");

        } else {

            // If it was a hard error
            // error(d.currentForm());
            d.setCurrentForm("ERROR");

            // Or maybe an error means the data is really unknown...
            // d.setCurrentForm(emissary.core.Form.UNKNOWN);

        }
        return Collections.emptyList();
    }

    /**
     * Test standalone main
     */
    public static void main(String[] argv) {
        mainRunner(TemplatePlace.class.getName(), argv);
    }
}
