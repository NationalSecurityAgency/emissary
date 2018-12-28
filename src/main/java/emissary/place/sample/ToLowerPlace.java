package emissary.place.sample;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;

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

        // Process the data. Note for an inline transformation, newContainer is required to allow clean closure
        long len = d.getDataContainer().length();
        try (InputStream oldData = Channels.newInputStream(d.getDataContainer().channel());
                OutputStream newData = Channels.newOutputStream(d.newDataContainer().newChannel(len))) {
            int read = oldData.read();
            while (read != -1) {
                char theChar = (char) read;
                if (Character.isUpperCase(theChar)) {
                    theChar = Character.toLowerCase(theChar);
                }
                newData.write((byte) theChar);
                read = oldData.read();
            }
        } catch (IOException e) {
            logger.warn("error doing transform, unable to decode", e);
            d.pushCurrentForm(emissary.core.Form.ERROR);
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
