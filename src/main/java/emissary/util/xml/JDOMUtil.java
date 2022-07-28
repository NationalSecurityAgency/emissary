package emissary.util.xml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.net.QuotedPrintableCodec;
import org.jdom2.CDATA;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.XMLFilter;

/**
 * Utilities for dealing with JDOM documents
 */
public class JDOMUtil {

    private static final Logger logger = LoggerFactory.getLogger(JDOMUtil.class);

    public static final String ERR_MSG = "Could not parse document: ";

    static SAXBuilder createSAXBuilder(final boolean validate) {
        SAXBuilder builder;
        if (validate) {
            builder = new SAXBuilder(org.jdom2.input.sax.XMLReaders.DTDVALIDATING);
        } else {
            builder = new SAXBuilder(org.jdom2.input.sax.XMLReaders.NONVALIDATING);
            builder.setFeature("http://xml.org/sax/features/validation", false);
            builder.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
            builder.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        }

        // If you can't completely disable DTDs, then at least do the following:
        builder.setFeature("http://xml.org/sax/features/external-general-entities", false);
        builder.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        builder.setFeature("http://apache.org/xml/features/xinclude", false);
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        builder.setExpandEntities(false);
        return builder;
    }

    /**
     * creates a JDOM document from the input XML string.
     *
     * @param xml an XML document in a String
     * @param filter an XMLFilter to receive callbacks during processing
     * @param validate if true, XML should be validated
     * @return the JDOM representation of that XML document
     */
    public static Document createDocument(final String xml, final XMLFilter filter, final boolean validate) throws JDOMException {
        return createDocument(createSAXBuilder(validate), xml, filter);
    }

    static Document createDocument(final SAXBuilder builder, final String xml, final XMLFilter filter) throws JDOMException {
        if (filter != null) {
            builder.setXMLFilter(filter);
        }

        try {
            return builder.build(new StringReader(xml));
        } catch (IOException iox) {
            throw new JDOMException(ERR_MSG + iox.getMessage(), iox);
        }
    }

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
     * creates a JDOM document from the input XML bytes. interpreting them in the platform default charset
     *
     * @param xml an XML document in a byte array
     * @param filter an XMLFilter to receive callbacks during processing
     * @param validate if true, XML should be validated
     * @return the JDOM representation of that XML document
     */
    public static Document createDocument(final byte[] xml, final XMLFilter filter, final boolean validate) throws JDOMException {
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
    public static Document createDocument(final byte[] xml, final XMLFilter filter, final boolean validate, final String charset)
            throws JDOMException {
        return createDocument(createSAXBuilder(validate), xml, filter, charset);
    }

    static Document createDocument(final SAXBuilder builder, final byte[] xml, final XMLFilter filter, final String charset)
            throws JDOMException {
        if (filter != null) {
            builder.setXMLFilter(filter);
        }
        final ByteArrayInputStream bais = new ByteArrayInputStream(xml);
        InputStreamReader isr;

        if (charset != null) {
            try {
                isr = new InputStreamReader(bais, charset);
            } catch (UnsupportedEncodingException e) {
                isr = new InputStreamReader(bais);
            }
        } else {
            isr = new InputStreamReader(bais);
        }

        try {
            return builder.build(isr);
        } catch (IOException iox) {
            throw new JDOMException(ERR_MSG + iox.getMessage(), iox);
        }
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
        return createDocument(createSAXBuilder(validate), is, filter);
    }

    static Document createDocument(final SAXBuilder builder, final InputSource is, final XMLFilter filter) throws JDOMException {
        if (filter != null) {
            builder.setXMLFilter(filter);
        }

        try {
            return builder.build(is);
        } catch (IOException iox) {
            throw new JDOMException(ERR_MSG + iox.getMessage(), iox);
        }
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
     * Get a string from a JDOM Document
     *
     * @param jdom the jdom document
     * @return String value in UTF-8
     */
    public static String toString(final Document jdom) {
        final XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());
        try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            outputter.output(jdom, os);
            return os.toString();
        } catch (IOException iox) {
            logger.error("ByteArrayOutputStream exception", iox);
            return null;
        }
    }

    /**
     * Create a JDOM element with some text
     */
    public static Element simpleElement(final String name, final String text) {
        final Element e = new Element(name);
        e.addContent(text);
        return e;
    }

    /**
     * Create a JDOM element with a number value
     */
    public static Element simpleElement(final String name, final int value) {
        final Element e = new Element(name);
        e.addContent(Integer.toString(value));
        return e;
    }

    /**
     * Create a JDOM element with a long value
     */
    public static Element simpleElement(final String name, final long value) {
        final Element e = new Element(name);
        e.addContent(Long.toString(value));
        return e;
    }

    /**
     * Create a JDOM element with a boolean value
     */
    public static Element simpleElement(final String name, final boolean value) {
        final Element e = new Element(name);
        e.addContent(Boolean.toString(value));
        return e;
    }

    /**
     * Create a JDOM element with some CDATA
     */
    public static Element cdataElement(final String name, final String text) {
        final Element e = new Element(name);
        e.addContent(new CDATA(text));
        return e;
    }

    /**
     * Create a JDOM element, protectign the data with encoding if needed
     */
    public static Element protectedElement(final String name, final byte[] data) {
        return protectedElement(name, new String(data, StandardCharsets.ISO_8859_1));
    }

    /**
     * Create a JDOM element, protecting the data with encoding if needed
     */
    public static Element protectedElement(final String name, final String s) {
        final Element e = new Element(name);
        int badCount = 0;
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (Character.isISOControl(c) && !Character.isWhitespace(c)) {
                badCount++;
            }
        }
        if ((1.0 * badCount) / (1.0 * s.length()) > 0.1) {
            e.setAttribute("encoding", "base64");
            final Base64 b64 = new Base64();
            e.addContent(new String(b64.encode(s.getBytes())));
        } else if (badCount > 0) {
            e.setAttribute("encoding", "quoted-printable");
            final QuotedPrintableCodec qp = new QuotedPrintableCodec();
            e.addContent(new String(qp.encode(s.getBytes())));
        } else {
            e.addContent(s);
        }
        return e;
    }

    /**
     * Like Element.getChildTextTrim but for an int
     */
    public static int getChildIntValue(final Element el, final String childName) {
        final String val = el.getChildTextTrim(childName);
        int x = -1;
        try {
            x = Integer.parseInt(val);
        } catch (NumberFormatException ex) {
            logger.debug("Unable to parse String as an integer");
        }
        return x;
    }

    /**
     * Like Element.getChildTextTrim but for an long
     */
    public static long getChildLongValue(final Element el, final String childName) {
        final String val = el.getChildTextTrim(childName);
        long x = -1L;
        try {
            x = Long.parseLong(val);
        } catch (NumberFormatException ex) {
            logger.debug("Unable to parse String as an long");
        }
        return x;
    }

    /**
     * Like Element.getChildTextTrim but for a boolean
     */
    public static boolean getChildBooleanValue(final Element el, final String childName) {
        final String val = el.getChildTextTrim(childName);
        return Boolean.parseBoolean(val);
    }

    public static void main(final String[] args) {
        for (int i = 0; args != null && i < args.length; i++) {
            try {
                final byte[] content = emissary.util.shell.Executrix.readDataFromFile(args[i]);
                if (content == null) {
                    logger.warn("Cannot read {}", args[i]);
                    continue;
                }
                createDocument(content, false);
                logger.info("{} is valid xml", args[i]);
            } catch (Exception e) {
                logger.error("{} is broken", args[i], e);
            }
        }
    }

    /** This class is not meant to be instantiated. */
    private JDOMUtil() {}
}
