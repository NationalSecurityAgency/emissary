package emissary.util.xml;

import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.xml.sax.InputSource;
import org.xml.sax.XMLFilter;

import javax.annotation.Nullable;

/**
 * Utilities for dealing with JDOM documents. If DTD validation is not needed, consider using {@link SaferJDOMUtil}.
 */
public class JDOMUtil extends AbstractJDOMUtil {

    /**
     * creates a JDOM document from the input XML string.
     *
     * @param xml an XML document in a String
     * @param validate if true, XML should be validated
     * @return the JDOM representation of that XML document
     */
    public static Document createDocument(final String xml, final boolean validate) throws JDOMException {
        return createDocument(xml, null, validate);
    }

    /**
     * creates a JDOM document from the input XML string.
     *
     * @param xml an XML document in a String
     * @param filter an XMLFilter to receive callbacks during processing
     * @param validate if true, XML should be validated
     * @return the JDOM representation of that XML document
     */
    public static Document createDocument(final String xml, @Nullable final XMLFilter filter, final boolean validate) throws JDOMException {
        return createDocument(xml, filter, createSAXBuilder(validate));
    }

    /**
     * creates a JDOM document from the input XML bytes.
     *
     * @param xml an XML document in a byte array
     * @param validate if true, XML should be validated
     * @return the JDOM representation of that XML document
     */
    public static Document createDocument(final byte[] xml, final boolean validate) throws JDOMException {
        return createDocument(xml, null, validate);
    }

    /**
     * creates a JDOM document from the input XML bytes. interpreting them in the platform default charset
     *
     * @param xml an XML document in a byte array
     * @param filter an XMLFilter to receive callbacks during processing
     * @param validate if true, XML should be validated
     * @return the JDOM representation of that XML document
     */
    public static Document createDocument(final byte[] xml, @Nullable final XMLFilter filter, final boolean validate) throws JDOMException {
        return createDocument(xml, filter, validate, null);
    }

    /**
     * creates a JDOM document from the input XML bytes.
     *
     * @param xml an XML document in a byte array
     * @param filter an XMLFilter to receive callbacks during processing
     * @param validate if true, XML should be validated
     * @param charset the charset to interpret the bytes in
     * @return the JDOM representation of that XML document
     */
    public static Document createDocument(final byte[] xml, final XMLFilter filter, final boolean validate, @Nullable final String charset)
            throws JDOMException {
        return createDocument(xml, filter, charset, createSAXBuilder(validate));
    }

    /**
     * creates a JDOM document from the InputSource
     *
     * @param is an XML document in an InputSource
     * @param filter an XMLFilter to receive callbacks during processing
     * @param validate if true, XML should be validated
     * @return the JDOM representation of that XML document
     */
    public static Document createDocument(final InputSource is, final XMLFilter filter, final boolean validate) throws JDOMException {
        return createDocument(is, filter, createSAXBuilder(validate));
    }

    /** This class is not meant to be instantiated. */
    private JDOMUtil() {}
}
