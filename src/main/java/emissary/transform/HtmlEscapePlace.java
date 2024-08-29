/***********************************************************
 * This place transforms &#xxxx; formatted HTML Escape
 * stuff into normal unicode (utf-8 characters)
 **/

package emissary.transform;

import emissary.core.Form;
import emissary.core.IBaseDataObject;
import emissary.place.ServiceProviderPlace;
import emissary.transform.decode.HtmlEscape;
import emissary.util.CharacterCounterSet;
import emissary.util.DataUtil;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nullable;

import static emissary.core.Form.HTML;
import static emissary.core.Form.PREFIXES_LANG;
import static emissary.core.Form.SUFFIXES_HTMLESC;
import static emissary.core.Form.TEXT;
import static emissary.core.constants.Configurations.OUTPUT_FORM;
import static emissary.core.constants.Parameters.DOCUMENT_TITLE;
import static emissary.core.constants.Parameters.SUFFIXES_HTML_ESCAPE;
import static emissary.core.constants.Parameters.SUMMARY;

@Deprecated(forRemoval = true)
public class HtmlEscapePlace extends ServiceProviderPlace {

    /**
     * Can be overridden from config file
     */
    @Nullable
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
        outputForm = configG.findStringEntry(OUTPUT_FORM, null);

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

        logger.debug("Just got a payload with form {}", incomingForm);

        byte[] newData = HtmlEscape.unescapeHtml(d.data(), counters);

        if (newData != null && newData.length > 0) {
            newData = HtmlEscape.unescapeEntities(newData, counters);
            if (outputForm != null) {
                d.setCurrentForm(outputForm);
            }
            // Track how much change in size there was
            int variance = d.dataLength() - newData.length;
            if (variance < 0) {
                variance *= -1;
            }
            d.setParameter("HTML_Entity_Decode_Variance", Integer.toString(variance));
            d.setData(newData);
            d.setFileTypeIfEmpty(HTML);

            for (String key : counters.getKeys()) {
                d.putParameter(key + SUFFIXES_HTML_ESCAPE, Integer.toString(counters.get(key)));
            }

        } else {
            logger.warn("error doing HtmlEscape, unable to decode");
            d.pushCurrentForm(Form.ERROR);
        }

        unescapeAltViews(d);
        unescapeSummary(d);
        unescapeDocTitle(d);
        processEncoding(d);
        processCurrentForms(d);
        nukeMyProxies(d);
    }

    protected void unescapeAltViews(IBaseDataObject d) {
        // Unescape any TEXT alt views we may have
        d.getAlternateViewNames().stream().filter(v -> v.startsWith(TEXT)).forEach(viewName -> {
            byte[] textView = d.getAlternateView(viewName);
            if (ArrayUtils.isNotEmpty(textView)) {
                byte[] s = HtmlEscape.unescapeHtml(textView);
                if (ArrayUtils.isNotEmpty(s)) {
                    s = HtmlEscape.unescapeEntities(s);
                    if (ArrayUtils.isNotEmpty(s)) {
                        d.addAlternateView(viewName, s);
                    }
                }
            }
        });
    }

    protected void unescapeSummary(IBaseDataObject d) {
        // Unescape the Summary if present
        String summary = d.getStringParameter(SUMMARY);
        if (StringUtils.contains(summary, "&#")) {
            logger.debug("Working on summary "/* + summary */);
            String s = makeString(HtmlEscape.unescapeHtml(summary.getBytes()));
            if (StringUtils.isNotBlank(s)) {
                s = HtmlEscape.unescapeEntities(s);
                d.deleteParameter(SUMMARY);
                d.putParameter(SUMMARY, s);
            }
        }
    }

    protected void unescapeDocTitle(IBaseDataObject d) {
        // Unescape the Document Title
        String title = d.getStringParameter(DOCUMENT_TITLE);
        if (StringUtils.contains(title, "&#")) {
            logger.debug("Working on title "/* + title */);
            String s = makeString(HtmlEscape.unescapeHtml(title.getBytes()));
            if (StringUtils.isNotBlank(s)) {
                d.deleteParameter(DOCUMENT_TITLE);
                s = HtmlEscape.unescapeEntities(s);
                d.putParameter(DOCUMENT_TITLE, s);
            }
        }
        logger.debug("Retrieved new title ");
    }

    protected void processEncoding(IBaseDataObject d) {
        // If the encoding or the LANG- form has -HTMLESC remove it
        String enc = d.getFontEncoding();
        if (StringUtils.contains(enc, SUFFIXES_HTMLESC)) {
            d.setFontEncoding(enc.replaceFirst(SUFFIXES_HTMLESC, ""));
        }
    }

    protected void processCurrentForms(IBaseDataObject d) {
        for (String cf : d.getAllCurrentForms()) {
            if (cf.contains(PREFIXES_LANG) && cf.contains(SUFFIXES_HTMLESC)) {
                // Get the old pos
                int pos = d.searchCurrentForm(cf);
                d.deleteCurrentForm(cf);
                cf = cf.replaceFirst(SUFFIXES_HTMLESC, "");
                d.addCurrentFormAt(pos, cf);
                break;
            }
        }
    }

    public static String makeString(byte[] s) {
        return new String(s, StandardCharsets.UTF_8);
    }


}
