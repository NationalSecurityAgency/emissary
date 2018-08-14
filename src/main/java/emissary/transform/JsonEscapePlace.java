/***********************************************************
 * This place transforms \\uxxxx Json escape
 * stuff into normal unicode (utf-8 characters)
 **/

package emissary.transform;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;

import emissary.core.IBaseDataObject;
import emissary.place.ServiceProviderPlace;
import emissary.transform.decode.JsonEscape;
import emissary.util.DataUtil;

public class JsonEscapePlace extends ServiceProviderPlace {

    /**
     * Can be overridden from config file
     */
    private String outputForm = emissary.core.Form.UNKNOWN;

    /**
     * Configure one with specified location
     *
     * @param cfgInfo the name of the config file or resource
     * @param dir the name of the controlling directory
     * @param placeLoc the string name for this place
     */
    public JsonEscapePlace(String cfgInfo, String dir, String placeLoc) throws IOException {
        super(cfgInfo, dir, placeLoc);
        configurePlace();
    }

    /**
     * Configure one with default location
     *
     * @param cfgInfo the name of the config file or resource
     */
    public JsonEscapePlace(String cfgInfo) throws IOException {
        super(cfgInfo, "TestJsonEscapePlace.example.com:8001");
        configurePlace();
    }

    /**
     * Create with all defaults
     */
    public JsonEscapePlace() throws IOException {
        super();
        configurePlace();
    }

    /**
     * Take care of special place configuration
     */
    protected void configurePlace() {
        outputForm = configG.findStringEntry("OUTPUT_FORM", outputForm);
    }

    /**
     * Consume a dataObject and return a modified one.
     */
    @Override
    public void process(IBaseDataObject d) {
        if (DataUtil.isEmpty(d)) {
            logger.debug("empty data");
            return;
        }
        String incomingForm = d.currentForm();

        logger.debug("JsonEscapePlace just got a " + incomingForm);

        long len = d.getDataContainer().length();
        try (InputStream oldData = Channels.newInputStream(d.getDataContainer().channel());
                OutputStream newData = Channels.newOutputStream(d.newDataContainer().newChannel(len))) {
            JsonEscape.unescape(oldData, newData);
        } catch (IOException e) {
            logger.warn("error doing JsonEscape, unable to decode", e);
            d.pushCurrentForm(emissary.core.Form.ERROR);
        }
        /*
         * due to emissary commit 72d9383 outputForm gets set to UNKNOWN which causes looping. This is a transform
         * place, but it's only changing data, not currentForm. if (outputForm != null) { d.setCurrentForm(outputForm);
         * }
         */
    }


    /**
     * Test standalone main
     */
    public static void main(String[] argv) throws Exception {
        mainRunner(JsonEscapePlace.class.getName(), argv);
    }
}
