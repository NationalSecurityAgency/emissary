package emissary.util;

import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import emissary.core.IBaseDataObject;
import emissary.util.xml.JDOMUtil;
import org.jdom2.Document;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilities for dealing with IBaseDataObject and Lists thereof
 */
public class PayloadUtil {
    public static final Logger logger = LoggerFactory.getLogger(PayloadUtil.class);

    private static final String LS = System.getProperty("line.separator");

    /**
     * Try really hard to get a meaningful name for a payload object
     */
    public static String getName(final Object o) {
        String payloadName = o.getClass().getName();
        if (o instanceof IBaseDataObject) {
            payloadName = ((IBaseDataObject) o).shortName();
        } else if (o instanceof Collection) {
            final Iterator<?> pi = ((Collection<?>) o).iterator();
            if (pi.hasNext()) {
                payloadName = ((IBaseDataObject) pi.next()).shortName() + "(" + ((Collection<?>) o).size() + ")";
            }
        }
        return payloadName;
    }

    /**
     * Generate a string about the payload object
     * 
     * @param payload the payload to describe
     * @param oneLine true for a condensed one-line string
     */
    public static String getPayloadDisplayString(final IBaseDataObject payload, final boolean oneLine) {
        return oneLine ? getPayloadOneLineString(payload) : getPayloadDisplayString(payload);
    }

    /**
     * Generate a string about the payload object
     * 
     * @param payload the payload to describe
     */
    public static String getPayloadDisplayString(final IBaseDataObject payload) {
        final StringBuilder sb = new StringBuilder();
        final List<String> th = payload.transformHistory();
        final String fileName = payload.getFilename();
        final List<String> currentForms = payload.getAllCurrentForms();
        final Date creationTimestamp = payload.getCreationTimestamp();

        sb.append("\n").append("filename: ").append(fileName).append("\n").append("   creationTimestamp: ").append(creationTimestamp).append("\n")
                .append("   currentForms: ").append(currentForms).append("\n").append("   filetype: ").append(payload.getFileType()).append("\n")
                .append("   transform history (").append(th.size()).append(") :").append("\n");
        for (final String h : th) {
            sb.append("     ").append(h).append("\n");
        }
        return sb.toString();
    }

    /**
     * Generate a one-line string about the payload object
     * 
     * @param payload the payload to describe
     */
    public static String getPayloadOneLineString(final IBaseDataObject payload) {
        final StringBuilder sb = new StringBuilder();
        final String fn = payload.getFilename();
        final int attPos = fn.indexOf(emissary.core.Family.SEP);
        if (attPos != -1) {
            sb.append(fn.substring(attPos + 1)).append(" ");
        }
        final List<String> th = payload.transformHistory();
        String prev = "";
        for (final String h : th) {
            final int pos = h.indexOf(".");
            if (pos > 0) {
                final String prefix = h.substring(0, pos);
                if (!prev.equals(prefix)) {
                    if (prev.length() != 0) {
                        sb.append(",");
                    }
                    sb.append(prefix);
                    prev = prefix;
                }
            }
        }
        sb.append(">>").append(payload.getAllCurrentForms());
        sb.append("//").append(payload.getFileType());
        sb.append("//").append(payload.getCreationTimestamp());
        return sb.toString();
    }

    /**
     * Turn the payload into an xml jdom document
     * 
     * @param d the payload
     */
    public static Document toXml(final IBaseDataObject d) {
        final Element root = new Element("payload");
        root.addContent(JDOMUtil.protectedElement("name", d.getFilename()));
        final Element cf = new Element("current-forms");
        for (final String c : d.getAllCurrentForms()) {
            cf.addContent(JDOMUtil.simpleElement("current-form", c));
        }
        root.addContent(cf);
        root.addContent(JDOMUtil.simpleElement("encoding", d.getFontEncoding()));
        root.addContent(JDOMUtil.simpleElement("filetype", d.getFileType()));
        root.addContent(JDOMUtil.simpleElement("classification", d.getClassification()));
        final Element th = new Element("transform-history");
        for (final String s : d.transformHistory()) {
            th.addContent(JDOMUtil.simpleElement("itinerary-step", s));
        }
        root.addContent(th);
        if (d.getProcessingError() != null) {
            root.addContent(JDOMUtil.simpleElement("processing-error", d.getProcessingError()));
        }
        final Element meta = new Element("metadata");
        for (final String key : d.getParameters().keySet()) {
            final Element m = JDOMUtil.protectedElement("param", d.getStringParameter(key));
            m.setAttribute("name", key);
            meta.addContent(m);
        }
        root.addContent(meta);

        if (d.header() != null) {
            root.addContent(JDOMUtil.protectedElement("header", d.header()));
        }
        if (d.dataLength() > 0) {
            root.addContent(JDOMUtil.protectedElement("data", d.data()));
        }
        if (d.footer() != null) {
            root.addContent(JDOMUtil.protectedElement("footer", d.footer()));
        }

        // Alt views
        if (d.getNumAlternateViews() > 0) {
            final Element views = new Element("views");
            for (final String av : d.getAlternateViewNames()) {
                final Element v = JDOMUtil.protectedElement("view", d.getAlternateView(av));
                v.setAttribute("name", av);
                views.addContent(v);
            }
            root.addContent(views);
        }

        logger.debug("Produced xml document for " + d.shortName());
        return new Document(root);
    }

    /**
     * Turn the payload into an xml string
     * 
     * @param d the payload
     */
    public static String toXmlString(final IBaseDataObject d) {
        return JDOMUtil.toString(toXml(d));
    }

    /**
     * Turn a list of payload into an xml jdom ocument
     * 
     * @param list the payload list
     */
    public static Document toXml(final List<IBaseDataObject> list) {
        final Element root = new Element("payload-list");
        for (final IBaseDataObject d : list) {
            final Document doc = toXml(d);
            root.addContent(doc.detachRootElement());
            logger.debug("Adding xml content for " + d.shortName() + " to document");
        }
        return new Document(root);
    }

    /**
     * Turn the payload list into an xml string
     * 
     * @param list the payload list
     */
    public static String toXmlString(final List<IBaseDataObject> list) {
        return JDOMUtil.toString(toXml(list));
    }

    /**
     * Print formatted metadata key:value pairs
     */
    private static final String SEP = ": ";

    public static String printFormattedMetadata(final IBaseDataObject payload) {
        final StringBuilder out = new StringBuilder();
        out.append(LS);
        for (final Map.Entry<String, Collection<Object>> entry : payload.getParameters().entrySet()) {
            out.append(entry.getKey()).append(SEP).append(entry.getValue()).append(LS);
        }
        return out.toString();
    }

    /** This class is not meant to be instantiated. */
    private PayloadUtil() {}
}
