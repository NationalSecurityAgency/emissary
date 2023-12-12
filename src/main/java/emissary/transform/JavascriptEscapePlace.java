/***********************************************************
 * This place transforms \\uxxxx Javascript escape 
 * stuff into normal unicode (utf-8 characters)
 **/

package emissary.transform;

import emissary.core.Form;
import emissary.core.IBaseDataObject;
import emissary.place.ServiceProviderPlace;
import emissary.transform.decode.JavascriptEscape;
import emissary.util.DataUtil;

import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;

import static emissary.core.constants.Configurations.OUTPUT_FORM;

public class JavascriptEscapePlace extends ServiceProviderPlace {

    /**
     * Can be overridden from config file
     */
    private String outputForm = Form.UNKNOWN;

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
        outputForm = configG.findStringEntry(OUTPUT_FORM, outputForm);
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

        logger.debug("JavascriptEscapePlace just got a {}", incomingForm);

        byte[] newData = JavascriptEscape.unescape(d.data());

        if (ArrayUtils.isNotEmpty(newData)) {
            d.setData(newData);

            if (outputForm != null) {
                d.setCurrentForm(outputForm);
            }
        } else {
            logger.warn("error doing JavascriptEscape, unable to decode");
            d.pushCurrentForm(Form.ERROR);
        }
    }


}
