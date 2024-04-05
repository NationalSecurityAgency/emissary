package emissary.util.xml;

import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.xml.sax.InputSource;
import org.xml.sax.XMLFilter;

import javax.annotation.Nullable;

/**
 * Utilities for dealing with JDOM documents. Doctypes are disallowed for DTDs for the prevention of XML entity attacks
 * making this a safer alternative to {@link JDOMUtil} .
 */
public class SaferJDOMUtil extends AbstractJDOMUtil {

    protected static SAXBuilder createSAXBuilder() {
        SAXBuilder builder = createSAXBuilder(false);
        // This is the PRIMARY defense. If DTDs (doctypes) are disallowed, almost all XML entity attacks are prevented
        builder.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return builder;
    }

    /**
     * creates a JDOM document from the input XML string.
     *
     * @param xml an XML document in a String
     * @return the JDOM representation of that XML document
     */
    public static Document createDocument(final String xml) throws JDOMException {
        return createDocument(xml, null);
    }

    /**
     * creates a JDOM document from the input XML string.
     *
     * @param xml an XML document in a String
     * @param filter an XMLFilter to receive callbacks during processing
     * @return the JDOM representation of that XML document
     */
    public static Document createDocument(final String xml, @Nullable final XMLFilter filter) throws JDOMException {
        return createDocument(xml, filter, createSAXBuilder());
    }

    /**
     * creates a JDOM document from the input XML bytes. interpreting them in the platform default charset
     *
     * @param xml an XML document in a byte array
     * @return the JDOM representation of that XML document
     */
    public static Document createDocument(final byte[] xml) throws JDOMException {
        return createDocument(xml, null);
    }

    /**
     * creates a JDOM document from the input XML bytes. interpreting them in the platform default charset
     *
     * @param xml an XML document in a byte array
     * @param filter an XMLFilter to receive callbacks during processing
     * @return the JDOM representation of that XML document
     */
    public static Document createDocument(final byte[] xml, @Nullable final XMLFilter filter) throws JDOMException {
        return createDocument(xml, filter, null);
    }

    /**
     * creates a JDOM document from the input XML bytes.
     *
     * @param xml an XML document in a byte array
     * @param filter an XMLFilter to receive callbacks during processing
     * @param charset the charset to interpret the bytes in
     * @return the JDOM representation of that XML document
     */
    public static Document createDocument(final byte[] xml, final XMLFilter filter, @Nullable final String charset)
            throws JDOMException {
        return createDocument(xml, filter, charset, createSAXBuilder());
    }

    /**
     * creates a JDOM document from the InputSource
     *
     * @param is an XML document in an InputSource
     * @param filter an XMLFilter to receive callbacks during processing
     * @return the JDOM representation of that XML document
     */
    public static Document createDocument(final InputSource is, final XMLFilter filter) throws JDOMException {
        return createDocument(is, filter, createSAXBuilder());
    }

    /** This class is not meant to be instantiated. */
    private SaferJDOMUtil() {}
}
