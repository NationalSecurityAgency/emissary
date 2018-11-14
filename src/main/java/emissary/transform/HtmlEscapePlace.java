/***********************************************************
 * This place transforms &#xxxx; formatted HTML Escape
 * stuff into normal unicode (utf-8 characters)
 **/

package emissary.transform;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import emissary.core.IBaseDataObject;
import emissary.place.ServiceProviderPlace;
import emissary.transform.decode.HtmlEscape;
import emissary.util.CharacterCounterSet;
import emissary.util.DataUtil;

public class HtmlEscapePlace extends ServiceProviderPlace {
    /**
     * Can be overridden from config file
     */
    private String outputForm = null;

    /**
     * The remote constructor
     */
    public HtmlEscapePlace(String cfgInfo, String dir, String placeLoc) throws IOException {
        super(cfgInfo, dir, placeLoc);
        configurePlace();
    }

    /**
     * The test constructor
     */
    public HtmlEscapePlace(String cfgInfo) throws IOException {
        super(cfgInfo, "TestHtmlEscapePlace.example.com:8001");
        configurePlace();
    }

    /**
     * Create with the default configuration
     */
    public HtmlEscapePlace() throws IOException {
        super();
        configurePlace();
    }

    /**
     * Take care of special place configuration
     */
    protected void configurePlace() {
        outputForm = configG.findStringEntry("OUTPUT_FORM", null);

        // Force statics to load
        HtmlEscape.unescapeHtml(new byte[0]);
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
        CharacterCounterSet counters = new CharacterCounterSet();

        logger.debug("HtmlEscapePlace just got a " + incomingForm);

        byte[] newData = HtmlEscape.unescapeHtml(d.data(), counters);

        if (newData != null && newData.length > 0) {
            newData = HtmlEscape.unescapeEntities(newData, counters);
            if (outputForm != null) {
                d.setCurrentForm(outputForm);
            }
            // Track how much change in size there was
            int variance = d.dataLength() - newData.length;
            if (variance < 0)
                variance *= -1;
            d.setParameter("HTML_Entity_Decode_Variance", Integer.toString(variance));
            d.setData(newData);
            d.setFileTypeIfEmpty("HTML");

            for (String key : counters.getKeys()) {
                d.putParameter(key + "_HTML_ESCAPE", Integer.toString(counters.get(key)));
            }

        } else {
            logger.warn("error doing HtmlEscape, unable to decode");
            d.pushCurrentForm(emissary.core.Form.ERROR);
        }

        // Unescape any TEXT alt views we may have
        for (String viewName : d.getAlternateViewNames()) {
            if (viewName.startsWith("TEXT")) {
                byte[] textView = d.getAlternateView(viewName);
                if (textView != null && textView.length > 0) {
                    byte[] s = HtmlEscape.unescapeHtml(textView);
                    if (s != null && s.length > 0) {
                        s = HtmlEscape.unescapeEntities(s);
                        if (s != null) {
                            d.addAlternateView(viewName, s);
                        }
                    }
                }
            }
        }

        // Unescape the Summary if present
        String summary = d.getStringParameter("Summary");
        if (summary != null && summary.indexOf("&#") != -1) {
            logger.debug("Working on summary " + summary);
            String s = makeString(HtmlEscape.unescapeHtml(summary.getBytes()));
            if (s != null && s.length() > 0) {
                s = HtmlEscape.unescapeEntities(s);
                d.deleteParameter("Summary");
                d.putParameter("Summary", s);
            }
        }

        // Unescape the Document Title
        String title = d.getStringParameter("DocumentTitle");
        if (title != null && title.indexOf("&#") != -1) {
            logger.debug("Working on title " + title);
            String s = makeString(HtmlEscape.unescapeHtml(title.getBytes()));
            if (s != null && s.length() > 0) {
                d.deleteParameter("DocumentTitle");
                s = HtmlEscape.unescapeEntities(s);
                d.putParameter("DocumentTitle", s);
            }
        }
        logger.debug("New retrieved title is " + d.getParameter("DocumentTitle"));

        // If the encoding or the LANG- form has -HTMLESC from hotspot remove it
        String enc = d.getFontEncoding();
        if (enc != null && enc.indexOf("-HTMLESC") > -1) {
            d.setFontEncoding(enc.replaceFirst("-HTMLESC", ""));
        }

        for (String cf : d.getAllCurrentForms()) {
            if (cf.indexOf("LANG-") > -1 && cf.indexOf("-HTMLESC") > -1) {
                // Get the old pos
                int pos = d.searchCurrentForm(cf);
                d.deleteCurrentForm(cf);
                cf = cf.replaceFirst("-HTMLESC", "");
                d.addCurrentFormAt(pos, cf);
                break;
            }
        }

        nukeMyProxies(d);
    }

    public static String makeString(byte[] s) {
        try {
            return new String(s, "UTF-8");
        } catch (UnsupportedEncodingException uee) {
            return new String(s);
        }
    }


    /**
     * Test standalone main
     */
    public static void main(String[] argv) throws Exception {
        mainRunner(HtmlEscapePlace.class.getName(), argv);
    }
}
