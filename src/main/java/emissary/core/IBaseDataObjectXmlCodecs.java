package emissary.core;

import emissary.core.channels.InMemoryChannelFactory;
import emissary.core.channels.SeekableByteChannelFactory;
import emissary.core.channels.SeekableByteChannelHelper;
import emissary.util.ByteUtil;
import emissary.util.xml.AbstractJDOMUtil;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.XMLConstants;

import static emissary.core.constants.IbdoXmlElementNames.BASE64_ATTRIBUTE_NAME;
import static emissary.core.constants.IbdoXmlElementNames.BIRTH_ORDER;
import static emissary.core.constants.IbdoXmlElementNames.BROKEN;
import static emissary.core.constants.IbdoXmlElementNames.ENCODING_ATTRIBUTE_NAME;
import static emissary.core.constants.IbdoXmlElementNames.NAME;
import static emissary.core.constants.IbdoXmlElementNames.NUM_CHILDREN;
import static emissary.core.constants.IbdoXmlElementNames.NUM_SIBLINGS;
import static emissary.core.constants.IbdoXmlElementNames.OUTPUTABLE;
import static emissary.core.constants.IbdoXmlElementNames.PARAMETER;
import static emissary.core.constants.IbdoXmlElementNames.PRIORITY;
import static emissary.core.constants.IbdoXmlElementNames.PROCESSING_ERROR;
import static emissary.core.constants.IbdoXmlElementNames.SHA256_ATTRIBUTE_NAME;
import static emissary.core.constants.IbdoXmlElementNames.VALUE;
import static emissary.core.constants.IbdoXmlElementNames.VIEW;

/**
 * This class contains the interfaces and implementations used to convert an IBDO-&gt;XML and XML-&gt;IBDO.
 */
public final class IBaseDataObjectXmlCodecs {
    /**
     * Logger instance
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(IBaseDataObjectXmlCodecs.class);
    /**
     * New line string to use for normalised XML
     */
    public static final String BASE64_NEW_LINE_STRING = new String(new byte[] {'\n'});
    /**
     * Max width of Base64 char block.
     */
    public static final int BASE64_LINE_WIDTH = 76;
    /**
     * The Base64 encoder.
     * 
     * Uses same width as default, but overrides new line separator to use normalized XML separator.
     * 
     * See http://www.jdom.org/docs/apidocs/org/jdom2/output/Format.html#setLineSeparator(java.lang.String)
     */
    public static final Base64.Encoder BASE64_ENCODER = Base64.getMimeEncoder(BASE64_LINE_WIDTH, new byte[] {'\n'});
    /**
     * The Base64 decoder.
     */
    private static final Base64.Decoder BASE64_DECODER = Base64.getMimeDecoder();
    /**
     * A map of element names of IBaseDataObject methods that get/set primitives and their default values.
     */
    public static final Map<String, Object> PRIMITVE_NAME_DEFAULT_MAP = Collections
            .unmodifiableMap(new ConcurrentHashMap<>(Stream.of(
                    new AbstractMap.SimpleEntry<>(BIRTH_ORDER, new BaseDataObject().getBirthOrder()),
                    new AbstractMap.SimpleEntry<>(BROKEN, new BaseDataObject().isBroken()),
                    new AbstractMap.SimpleEntry<>(NUM_CHILDREN, new BaseDataObject().getNumChildren()),
                    new AbstractMap.SimpleEntry<>(NUM_SIBLINGS, new BaseDataObject().getNumSiblings()),
                    new AbstractMap.SimpleEntry<>(OUTPUTABLE, new BaseDataObject().isOutputable()),
                    new AbstractMap.SimpleEntry<>(PRIORITY, new BaseDataObject().getPriority()))
                    .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue))));
    /**
     * The XML namespace for "xml".
     */
    public static final Namespace XML_NAMESPACE = Namespace.getNamespace(XMLConstants.XML_NS_PREFIX,
            XMLConstants.XML_NS_URI);

    /**
     * Interface for decoding an element value.
     */
    public interface ElementDecoder {
        /**
         * Decodes and XML element value and sets it on the specified IBDO method.
         * 
         * @param elements the list of elements to be decoded.
         * @param ibdo the ibdo to set the values on.
         * @param ibdoMethodName the ibdo method name to use to set the values.
         * @throws Exception thrown if anything goes wrong.
         */
        void decode(List<Element> elements, IBaseDataObject ibdo, String ibdoMethodName) throws Exception;
    }

    /**
     * Interface for encoding an element value.
     */
    public interface ElementEncoder<T> {
        /**
         * Encodes a list of values into an element that is attached to the parent element with the specified child element
         * name.
         * 
         * @param values to be encoded.
         * @param parentElement that the child element is to be attached to.
         * @param childElementName the name of the child element.
         */
        void encode(List<T> values, Element parentElement, String childElementName);
    }

    /**
     * Class that contains the element decoders.
     */
    public static class ElementDecoders {
        /**
         * Decoder for boolean elements.
         */
        public final ElementDecoder booleanDecoder;
        /**
         * Decoder for byte[] elements.
         */
        public final ElementDecoder byteArrayDecoder;
        /**
         * Decoder for integer elements.
         */
        public final ElementDecoder integerDecoder;
        /**
         * Decoder for SeekableByteChannel elements.
         */
        public final ElementDecoder seekableByteChannelFactoryDecoder;
        /**
         * Decoder for Map&lt;String,byte[]&gt; elements.
         */
        public final ElementDecoder stringByteArrayDecoder;
        /**
         * Decoder for String elements.
         */
        public final ElementDecoder stringDecoder;
        /**
         * Decoder for Map&lt;String,Collection&lt;Object&gt;&gt; elements.
         */
        public final ElementDecoder stringObjectDecoder;

        /**
         * Constructs a container for the XML element decoders.
         * 
         * @param booleanDecoder decoder for boolean elements
         * @param byteArrayDecoder decoder for byte[] elements
         * @param integerDecoder decoder for integer elements
         * @param seekableByteChannelFactoryDecoder decoder for SeekableByteChannelElements.
         * @param stringByteArrayDecoder decoder for Map&lt;String,byte[]&gt; elements
         * @param stringDecoder decoder for String elements
         * @param stringObjectDecoder decoder for Map&lt;String,Collection&lt;Object&gt;&gt; elements
         */
        public ElementDecoders(
                final ElementDecoder booleanDecoder,
                final ElementDecoder byteArrayDecoder,
                final ElementDecoder integerDecoder,
                final ElementDecoder seekableByteChannelFactoryDecoder,
                final ElementDecoder stringByteArrayDecoder,
                final ElementDecoder stringDecoder,
                final ElementDecoder stringObjectDecoder) {
            Validate.notNull(booleanDecoder, "Required: booleanDecoder not null!");
            Validate.notNull(byteArrayDecoder, "Required: byteArrayDecoder not null!");
            Validate.notNull(integerDecoder, "Required: integerDecoder not null!");
            Validate.notNull(seekableByteChannelFactoryDecoder, "Required: seekableByteChannelFactoryDecoder not null!");
            Validate.notNull(stringByteArrayDecoder, "Required: stringByteArrayDecoder not null!");
            Validate.notNull(stringDecoder, "Required: stringDecoder not null!");
            Validate.notNull(stringObjectDecoder, "Required: stringObjectDecoder not null!");

            this.booleanDecoder = booleanDecoder;
            this.byteArrayDecoder = byteArrayDecoder;
            this.integerDecoder = integerDecoder;
            this.seekableByteChannelFactoryDecoder = seekableByteChannelFactoryDecoder;
            this.stringByteArrayDecoder = stringByteArrayDecoder;
            this.stringDecoder = stringDecoder;
            this.stringObjectDecoder = stringObjectDecoder;
        }
    }

    /**
     * Class that contains the element encoders.
     */
    public static class ElementEncoders {
        /**
         * Encoder for boolean elements.
         */
        public final ElementEncoder<Boolean> booleanEncoder;
        /**
         * Encoder for byte[] elements.
         */
        public final ElementEncoder<byte[]> byteArrayEncoder;
        /**
         * Encoder for integer elements.
         */
        public final ElementEncoder<Integer> integerEncoder;
        /**
         * Encoder for SeekableByteChannel elements.
         */
        public final ElementEncoder<SeekableByteChannelFactory> seekableByteChannelFactoryEncoder;
        /**
         * Encoder for Map&lt;String,byte[]&gt; elements.
         */
        public final ElementEncoder<Map<String, byte[]>> stringByteArrayEncoder;
        /**
         * Encoder for String elements.
         */
        public final ElementEncoder<String> stringEncoder;
        /**
         * Encoder for Map&lt;String, Collection&lt;Object&gt;&gt; elements.
         */
        public final ElementEncoder<Map<String, Collection<Object>>> stringObjectEncoder;

        /**
         * Constructs a container for the XML element encoders.
         * 
         * @param booleanEncoder encoder for boolean elements
         * @param byteArrayEncoder encoder for byte[] elements
         * @param integerEncoder encoder for integer elements
         * @param seekableByteChannelFactoryEncoder encoder for SeekableByteChannel elements
         * @param stringByteArrayEncoder encoder for Map&lt;String,byte[]&gt; elements
         * @param stringEncoder encoder for String elements.
         * @param stringObjectEncoder encoder for Map&lt;String, Collection&lt;Object&gt;&gt; elements
         */
        public ElementEncoders(
                final ElementEncoder<Boolean> booleanEncoder,
                final ElementEncoder<byte[]> byteArrayEncoder,
                final ElementEncoder<Integer> integerEncoder,
                final ElementEncoder<SeekableByteChannelFactory> seekableByteChannelFactoryEncoder,
                final ElementEncoder<Map<String, byte[]>> stringByteArrayEncoder,
                final ElementEncoder<String> stringEncoder,
                final ElementEncoder<Map<String, Collection<Object>>> stringObjectEncoder) {
            Validate.notNull(booleanEncoder, "Required: booleanEncoder not null!");
            Validate.notNull(byteArrayEncoder, "Required: byteArrayEncoder not null!");
            Validate.notNull(integerEncoder, "Required: integerEncoder not null!");
            Validate.notNull(seekableByteChannelFactoryEncoder, "Required: seekableByteChannelFactoryEncoder not null!");
            Validate.notNull(stringByteArrayEncoder, "Required: stringByteArrayEncoder not null!");
            Validate.notNull(stringEncoder, "Required: stringEncoder not null!");
            Validate.notNull(stringObjectEncoder, "Required: stringObjectEncoder not null!");

            this.booleanEncoder = booleanEncoder;
            this.byteArrayEncoder = byteArrayEncoder;
            this.integerEncoder = integerEncoder;
            this.seekableByteChannelFactoryEncoder = seekableByteChannelFactoryEncoder;
            this.stringByteArrayEncoder = stringByteArrayEncoder;
            this.stringEncoder = stringEncoder;
            this.stringObjectEncoder = stringObjectEncoder;
        }
    }

    /**
     * Implementation of an XML element decoder that has a boolean value.
     */
    public static final ElementDecoder DEFAULT_BOOLEAN_DECODER = (elements, ibdo, ibdoMethodName) -> {
        final Method method = getIbdoMethod(ibdoMethodName, boolean.class);

        for (final Element element : elements) {
            method.invoke(ibdo, Boolean.valueOf(element.getValue()));
        }
    };

    /**
     * Implementation of an XML element decoder that has a SeekableByteChannel value.
     */
    public static final ElementDecoder DEFAULT_SEEKABLE_BYTE_CHANNEL_FACTORY_DECODER = (elements, ibdo, ibdoMethodName) -> {
        final Method method = getIbdoMethod(ibdoMethodName, SeekableByteChannelFactory.class);

        for (final Element element : elements) {
            final String elementValue = element.getValue();
            final String encoding = element.getAttributeValue(ENCODING_ATTRIBUTE_NAME);

            method.invoke(ibdo, InMemoryChannelFactory.create(extractBytes(encoding, elementValue)));
        }
    };

    /**
     * Implementation of an XML element decoder that has a byte array value.
     */
    public static final ElementDecoder DEFAULT_BYTE_ARRAY_DECODER = (elements, ibdo, ibdoMethodName) -> {
        final Method method = getIbdoMethod(ibdoMethodName, byte[].class);

        for (final Element element : elements) {
            final String elementValue = element.getValue();
            final String encoding = element.getAttributeValue(ENCODING_ATTRIBUTE_NAME);

            method.invoke(ibdo, extractBytes(encoding, elementValue));
        }
    };

    /**
     * Implementation of an XML element decoder that has an integer value.
     */
    public static final ElementDecoder DEFAULT_INTEGER_DECODER = (elements, ibdo, ibdoMethodName) -> {
        final Method method = getIbdoMethod(ibdoMethodName, int.class);

        for (final Element element : elements) {
            method.invoke(ibdo, Integer.decode(element.getValue()));
        }
    };

    /**
     * Implementation of an XML element decoder that has a string value.
     */
    public static final ElementDecoder DEFAULT_STRING_DECODER = (elements, ibdo, ibdoMethodName) -> {
        final Method method = getIbdoMethod(ibdoMethodName, String.class);

        for (final Element element : elements) {
            final String elementValue = element.getValue();
            final String encoding = element.getAttributeValue(ENCODING_ATTRIBUTE_NAME);

            method.invoke(ibdo, new String(extractBytes(encoding, elementValue), StandardCharsets.UTF_8));
        }
    };

    /**
     * Implementation of an XML element decoder that has a mapped value where the key is a string and the value is a byte
     * array.
     */
    public static final ElementDecoder DEFAULT_STRING_BYTE_ARRAY_DECODER = (elements, ibdo, ibdoMethodName) -> {
        final Method method = getIbdoMethod(ibdoMethodName, String.class, byte[].class);

        for (final Element element : elements) {
            final Element nameElement = element.getChild(NAME);
            final String name = nameElement.getValue();
            final String nameEncoding = nameElement.getAttributeValue(ENCODING_ATTRIBUTE_NAME);
            final String nameDecoded = new String(extractBytes(nameEncoding, name), StandardCharsets.UTF_8);
            final Element valueElement = element.getChild(VALUE);
            final String value = valueElement.getValue();
            final String valueEncoding = valueElement.getAttributeValue(ENCODING_ATTRIBUTE_NAME);
            final byte[] valueDecoded = extractBytes(valueEncoding, value);

            method.invoke(ibdo, nameDecoded, valueDecoded);
        }
    };

    /**
     * Implementation of an XML element decoder that has a mapped value where the key is a string and the value is an
     * object.
     */
    public static final ElementDecoder DEFAULT_STRING_OBJECT_DECODER = (elements, ibdo, ibdoMethodName) -> {
        final Method method = getIbdoMethod(ibdoMethodName, String.class, Object.class);

        for (final Element element : elements) {
            final Element nameElement = element.getChild(NAME);
            final String name = nameElement.getValue();
            final String nameEncoding = nameElement.getAttributeValue(ENCODING_ATTRIBUTE_NAME);
            final String nameDecoded = new String(extractBytes(nameEncoding, name), StandardCharsets.UTF_8);
            final Element valueElement = element.getChild(VALUE);
            final String value = valueElement.getValue();
            final String valueEncoding = valueElement.getAttributeValue(ENCODING_ATTRIBUTE_NAME);
            final String valueDecoded = new String(extractBytes(valueEncoding, value));

            method.invoke(ibdo, nameDecoded, valueDecoded);
        }
    };

    /**
     * An implementation of an XML element encoder for SeekableByteChannel's that produces a base64 value.
     */
    public static final ElementEncoder<SeekableByteChannelFactory> DEFAULT_SEEKABLE_BYTE_CHANNEL_FACTORY_ENCODER =
            new SeekableByteChannelFactoryEncoder();

    private static class SeekableByteChannelFactoryEncoder implements ElementEncoder<SeekableByteChannelFactory> {
        @Override
        public void encode(final List<SeekableByteChannelFactory> values, final Element parentElement, final String childElementName) {
            for (final SeekableByteChannelFactory value : values) {
                if (value != null) {
                    try {
                        final byte[] bytes = SeekableByteChannelHelper.getByteArrayFromChannel(value,
                                BaseDataObject.MAX_BYTE_ARRAY_SIZE);

                        parentElement.addContent(preserve(protectedElementBase64(childElementName, bytes)));
                    } catch (final IOException e) {
                        LOGGER.error("Could not get bytes from SeekableByteChannel!", e);
                    }
                }
            }
        }
    }

    /**
     * An implementation of an XML element encoder for SeekableByteChannel's that produces a SHA256 hash value.
     */
    public static final ElementEncoder<SeekableByteChannelFactory> SHA256_SEEKABLE_BYTE_CHANNEL_FACTORY_ENCODER =
            new HashSeekableByteChannelFactoryEncoder();

    private static class HashSeekableByteChannelFactoryEncoder implements ElementEncoder<SeekableByteChannelFactory> {
        @Override
        public void encode(final List<SeekableByteChannelFactory> values, final Element parentElement, final String childElementName) {
            for (final SeekableByteChannelFactory value : values) {
                if (value != null) {
                    try {
                        final byte[] bytes = SeekableByteChannelHelper.getByteArrayFromChannel(value,
                                BaseDataObject.MAX_BYTE_ARRAY_SIZE);

                        parentElement.addContent(preserve(protectedElementHash(childElementName, bytes)));
                    } catch (final IOException e) {
                        LOGGER.error("Could not get bytes from SeekableByteChannel!", e);
                    }
                }
            }
        }

        private static Element protectedElementHash(final String name, final byte[] bytes) {
            final Element element = new Element(name);

            if (ByteUtil.hasNonPrintableValues(bytes)) {
                element.setAttribute(ENCODING_ATTRIBUTE_NAME, SHA256_ATTRIBUTE_NAME);
                element.addContent(ByteUtil.sha256Bytes(bytes));
            } else {
                element.addContent(new String(bytes, StandardCharsets.ISO_8859_1));
            }

            return element;
        }
    }

    /**
     * An implementation of an XML element encoder for integers.
     */
    public static final ElementEncoder<Integer> DEFAULT_INTEGER_ENCODER = new IntegerEncoder();

    private static class IntegerEncoder implements ElementEncoder<Integer> {
        @Override
        public void encode(final List<Integer> values, final Element parentElement, final String childElementName) {
            for (final int value : values) {
                if (((Integer) PRIMITVE_NAME_DEFAULT_MAP.get(childElementName)) != value) {
                    parentElement.addContent(AbstractJDOMUtil.simpleElement(childElementName, value));
                }
            }
        }
    }

    /**
     * An implementation of an XML element encoder for Strings.
     */
    public static final ElementEncoder<String> DEFAULT_STRING_ENCODER = new StringEncoder();

    private static class StringEncoder implements ElementEncoder<String> {
        @Override
        public void encode(final List<String> values, final Element parentElement, final String childElementName) {
            for (int i = values.size() - 1; i >= 0; i--) {
                String value = values.get(i);

                if (PROCESSING_ERROR.equals(childElementName) && StringUtils.isNotEmpty(value)) {
                    value = value.substring(0, value.length() - 1);
                }

                if (value != null) {
                    parentElement.addContent(preserve(protectedElement(childElementName, value)));
                }
            }
        }
    }

    /**
     * An implementation of an XML element encoder for byte[].
     */
    public static final ElementEncoder<byte[]> DEFAULT_BYTE_ARRAY_ENCODER = new ByteArrayEncoder();

    private static class ByteArrayEncoder implements ElementEncoder<byte[]> {
        @Override
        public void encode(final List<byte[]> values, final Element parentElement, final String childElementName) {
            for (final byte[] value : values) {
                if (value != null) {
                    parentElement.addContent(preserve(protectedElementBase64(childElementName, value)));
                }
            }
        }
    }

    /**
     * An implementation of an XML element encoder for booleans.
     */
    public static final ElementEncoder<Boolean> DEFAULT_BOOLEAN_ENCODER = new BooleanEncoder();

    private static class BooleanEncoder implements ElementEncoder<Boolean> {
        @Override
        public void encode(final List<Boolean> values, final Element parentElement, final String childElementName) {
            for (final boolean value : values) {
                if ((Boolean) PRIMITVE_NAME_DEFAULT_MAP.get(childElementName) != value) {
                    parentElement.addContent(AbstractJDOMUtil.simpleElement(childElementName, value));
                }
            }
        }
    }

    /**
     * An implementation of an XML element encoder for Map&lt;String, Collection&lt;Object&gt;&gt;.
     */
    public static final ElementEncoder<Map<String, Collection<Object>>> DEFAULT_STRING_OBJECT_ENCODER = new StringObjectEncoder();

    private static class StringObjectEncoder implements ElementEncoder<Map<String, Collection<Object>>> {
        @Override
        public void encode(final List<Map<String, Collection<Object>>> values, final Element parentElement, final String childElementName) {
            for (final Map<String, Collection<Object>> value : values) {
                for (final Entry<String, Collection<Object>> parameter : value.entrySet()) {
                    for (final Object item : parameter.getValue()) {
                        final Element metaElement = new Element(PARAMETER);

                        parentElement.addContent(metaElement);
                        metaElement.addContent(preserve(protectedElement(NAME, parameter.getKey())));
                        metaElement.addContent(preserve(protectedElement(VALUE, item.toString())));
                    }
                }
            }
        }
    }

    /**
     * An implementation of an XML element encoder for Map&lt;String, byte[]&gt;.
     */
    public static final ElementEncoder<Map<String, byte[]>> DEFAULT_STRING_BYTE_ARRAY_ENCODER = new StringByteArrayEncoder();

    private static class StringByteArrayEncoder implements ElementEncoder<Map<String, byte[]>> {
        @Override
        public void encode(final List<Map<String, byte[]>> values, final Element parentElement, final String childElementName) {
            for (final Map<String, byte[]> value : values) {
                for (final Entry<String, byte[]> view : value.entrySet()) {
                    final Element metaElement = new Element(VIEW);

                    parentElement.addContent(metaElement);
                    metaElement.addContent(preserve(protectedElement(NAME, view.getKey())));
                    metaElement.addContent(preserve(protectedElementBase64(VALUE, view.getValue())));
                }
            }
        }
    }

    /**
     * The default set of XML element decoders.
     */
    public static final ElementDecoders DEFAULT_ELEMENT_DECODERS = new ElementDecoders(
            DEFAULT_BOOLEAN_DECODER,
            DEFAULT_BYTE_ARRAY_DECODER,
            DEFAULT_INTEGER_DECODER,
            DEFAULT_SEEKABLE_BYTE_CHANNEL_FACTORY_DECODER,
            DEFAULT_STRING_BYTE_ARRAY_DECODER,
            DEFAULT_STRING_DECODER,
            DEFAULT_STRING_OBJECT_DECODER);

    /**
     * The default set of XML element encoders.
     */
    public static final ElementEncoders DEFAULT_ELEMENT_ENCODERS = new ElementEncoders(
            DEFAULT_BOOLEAN_ENCODER,
            DEFAULT_BYTE_ARRAY_ENCODER,
            DEFAULT_INTEGER_ENCODER,
            DEFAULT_SEEKABLE_BYTE_CHANNEL_FACTORY_ENCODER,
            DEFAULT_STRING_BYTE_ARRAY_ENCODER,
            DEFAULT_STRING_ENCODER,
            DEFAULT_STRING_OBJECT_ENCODER);

    /**
     * The set of XML element encoders that will sha256 hash the specified element types.
     */
    public static final ElementEncoders SHA256_ELEMENT_ENCODERS = new ElementEncoders(
            DEFAULT_BOOLEAN_ENCODER,
            DEFAULT_BYTE_ARRAY_ENCODER,
            DEFAULT_INTEGER_ENCODER,
            SHA256_SEEKABLE_BYTE_CHANNEL_FACTORY_ENCODER,
            DEFAULT_STRING_BYTE_ARRAY_ENCODER,
            DEFAULT_STRING_ENCODER,
            DEFAULT_STRING_OBJECT_ENCODER);

    private IBaseDataObjectXmlCodecs() {}

    /**
     * Return UTF8 bytes from an XML value, decoding base64 if required
     * 
     * @param encoding e.g. 'base64', otherwise it returns the bytes as they are presented
     * @param elementValue containing the data
     * @return the data from elementValue, decoded from base64 if required
     */
    public static byte[] extractBytes(final String encoding, final String elementValue) {
        if (BASE64_ATTRIBUTE_NAME.equalsIgnoreCase(encoding)) {
            final String newElementValue = elementValue.replace("\n", "");
            final byte[] bytes = newElementValue.getBytes(StandardCharsets.UTF_8);
            return BASE64_DECODER.decode(bytes);
        }

        return elementValue.getBytes(StandardCharsets.UTF_8);
    }

    private static Element preserve(final Element element) {
        element.setAttribute("space", "preserve", XML_NAMESPACE);

        return element;
    }

    private static Element protectedElement(final String name, final String string) {
        return protectedElementBase64(name, string.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a 'protected' element which can be encoded with base64 if it contains unsafe characters
     * 
     * See method source for specific definition of 'unsafe'.
     * 
     * @param name of the element
     * @param bytes to wrap, if they contain unsafe characters
     * @return the created element
     */
    private static Element protectedElementBase64(final String name, final byte[] bytes) {
        final Element element = new Element(name);

        if (ByteUtil.hasNonPrintableValues(bytes)) {
            String base64String = BASE64_NEW_LINE_STRING +
                    BASE64_ENCODER.encodeToString(bytes) +
                    BASE64_NEW_LINE_STRING;

            element.setAttribute(ENCODING_ATTRIBUTE_NAME, BASE64_ATTRIBUTE_NAME);
            element.addContent(base64String);
        } else {
            element.addContent(new String(bytes, StandardCharsets.ISO_8859_1));
        }

        return element;
    }

    /**
     * Gets the requested method object from the IBaseDataObject class.
     * 
     * @throws SecurityException
     * @throws NoSuchMethodException
     */
    private static Method getIbdoMethod(final String name, final Class<?>... parameterTypes)
            throws NoSuchMethodException, SecurityException {
        return IBaseDataObject.class.getDeclaredMethod(name, parameterTypes);
    }
}
