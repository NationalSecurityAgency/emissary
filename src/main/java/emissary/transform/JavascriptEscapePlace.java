/***********************************************************
 * This place transforms \\uxxxx Javascript escape
 * stuff into normal unicode (utf-8 characters)
 **/

package emissary.transform;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;

import emissary.core.IBaseDataObject;
import emissary.place.ServiceProviderPlace;
import emissary.transform.decode.JavascriptEscape;
import emissary.util.DataUtil;

public class JavascriptEscapePlace extends ServiceProviderPlace {

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
    public JavascriptEscapePlace(String cfgInfo, String dir, String placeLoc) throws IOException {
        super(cfgInfo, dir, placeLoc);
        configurePlace();
    }

    /**
     * Configure one with default location
     *
     * @param cfgInfo the name of the config file or resource
     */
    public JavascriptEscapePlace(String cfgInfo) throws IOException {
        super(cfgInfo, "TestJavascriptEscapePlace.example.com:8001");
        configurePlace();
    }

    /**
     * Constructor with default config
     */
    public JavascriptEscapePlace() throws IOException {
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

        logger.debug("JavascriptEscapePlace just got a " + incomingForm);


        long len = d.getDataContainer().length();
        try (InputStream oldData = Channels.newInputStream(d.getDataContainer().channel());
                OutputStream newData = Channels.newOutputStream(d.newDataContainer().newChannel(len))) {
            JavascriptEscape.unescape(oldData, newData);
            d.setCurrentForm(outputForm);
        } catch (IOException e) {
            logger.warn("error doing JsonEscape, unable to decode", e);
            d.pushCurrentForm(emissary.core.Form.ERROR);
        }
    }


    /**
     * Test standalone main
     */
    public static void main(String[] argv) throws Exception {
        mainRunner(JavascriptEscapePlace.class.getName(), argv);
    }
}
