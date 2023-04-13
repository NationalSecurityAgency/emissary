package emissary.core;

import emissary.core.channels.InMemoryChannelFactory;
import emissary.core.channels.SeekableByteChannelFactory;
import emissary.core.channels.SeekableByteChannelHelper;
import emissary.kff.KffDataObjectHandler;
import emissary.util.xml.AbstractJDOMUtil;

import org.apache.commons.lang3.Validate;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleEntry;
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

/**
 * This class helps convert IBaseDataObjects to and from XML.
 */
public final class IBaseDataObjectXmlHelper {
    /**
     * Logger instance
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(IBaseDataObjectXmlHelper.class);
    /**
     * The XML element name for Answers.
     */
    public static final String ANSWERS_ELEMENT_NAME = "answers";
    /**
     * The XML element prefix for attachments.
     */
    public static final String ATTACHMENT_ELEMENT_PREFIX = "att";
    /**
     * The XML attribute name for Base64.
     */
    public static final String BASE64_ATTRIBUTE_NAME = "base64";
    /**
     * New line byte array to use for normalised XML
     */
    private static final byte[] BASE64_NEW_LINE_BYTE = {'\n'};
    /**
     * New line string to use for normalised XML
     */
    private static final String BASE64_NEW_LINE_STRING = new String(BASE64_NEW_LINE_BYTE);
    /**
     * Max width of Base64 char block.
     */
    private static final int BASE64_LINE_WIDTH = 76;
    /**
     * The Base64 decoder.
     */
    private static final Base64.Decoder BASE64_DECODER = Base64.getMimeDecoder();
    /**
     * The Base64 encoder.
     * 
     * Uses same width as default, but overrides new line separator to use normalised XML separator.
     * 
     * @see http://www.jdom.org/docs/apidocs/org/jdom2/output/Format.html#setLineSeparator(java.lang.String)
     */
    private static final Base64.Encoder BASE64_ENCODER = Base64.getMimeEncoder(BASE64_LINE_WIDTH, BASE64_NEW_LINE_BYTE);
    /**
     * The XML element name for Birth Order.
     */
    public static final String BIRTH_ORDER_ELEMENT_NAME = "birthOrder";
    /**
     * The IBaseDataObject set method name for Birth Order.
     */
    public static final String BIRTH_ORDER_SET_METHOD_NAME = "setBirthOrder";
    /**
     * The XML element name for Broken.
     */
    public static final String BROKEN_ELEMENT_NAME = "broken";
    /**
     * The IBaseDataObject set method name for Broken.
     */
    public static final String BROKEN_SET_METHOD_NAME = "setBroken";
    /**
     * The XML element name for Classification.
     */
    public static final String CLASSIFICATION_ELEMENT_NAME = "classification";
    /**
     * The IBaseDataObject set method name for Classification.
     */
    public static final String CLASSIFICATION_SET_METHOD_NAME = "setClassification";
    /**
     * The XML element name for Current Form.
     */
    public static final String CURRENT_FORM_ELEMENT_NAME = "currentForm";
    /**
     * The IBaseDataObject set method name for Current Form.
     */
    public static final String CURRENT_FORM_SET_METHOD_NAME = "pushCurrentForm";
    /**
     * The XML element name for Data.
     */
    public static final String DATA_ELEMENT_NAME = "data";
    /**
     * The IBaseDataObject set method name for Data.
     */
    public static final String DATA_SET_METHOD_NAME = "setChannelFactory";
    /**
     * The XML attribute name for Encoding.
     */
    public static final String ENCODING_ATTRIBUTE_NAME = "encoding";
    /**
     * The XML element prefix for Extracted Records.
     */
    public static final String EXTRACTED_RECORD_ELEMENT_PREFIX = "extract";
    /**
     * The XML element name for Filename.
     */
    public static final String FILENAME_ELEMENT_NAME = "filename";
    /**
     * The IBaseDataObject set method name for Filename.
     */
    public static final String FILENAME_SET_METHOD_NAME = "setFilename";
    /**
     * The XML element name for Font Encoding.
     */
    public static final String FONT_ENCODING_ELEMENT_NAME = "fontEncoding";
    /**
     * The IBaseDataObject set method name for Font Encoding.
     */
    public static final String FONT_ENCODING_SET_METHOD_NAME = "setFontEncoding";
    /**
     * The XML element name for Footer.
     */
    public static final String FOOTER_ELEMENT_NAME = "footer";
    /**
     * The IBaseDataObject set method name for Footer.
     */
    public static final String FOOTER_SET_METHOD_NAME = "setFooter";
    /**
     * The XML element name for Header.
     */
    public static final String HEADER_ELEMENT_NAME = "header";
    /**
     * The IBaseDataObject set method name for Header.
     */
    public static final String HEADER_SET_METHOD_NAME = "setHeader";
    /**
     * The XML element name for Header Encoding.
     */
    public static final String HEADER_ENCODING_ELEMENT_NAME = "headerEncoding";
    /**
     * The IBaseDataObject set method name for Header Encoding.
     */
    public static final String HEADER_ENCODING_SET_METHOD_NAME = "setHeaderEncoding";
    /**
     * The XML element name for Id.
     */
    public static final String ID_ELEMENT_NAME = "id";
    /**
     * The IBaseDataObject set method name for Id.
     */
    public static final String ID_SET_METHOD_NAME = "setId";
    /**
     * The XML element name for Name.
     */
    public static final String NAME_ELEMENT_NAME = "name";
    /**
     * The XML element name for Num Siblings.
     */
    public static final String NUM_CHILDREN_ELEMENT_NAME = "numChildren";
    /**
     * The IBaseDataObject set method name for Num Siblings.
     */
    public static final String NUM_CHILDREN_SET_METHOD_NAME = "setNumChildren";
    /**
     * The XML element name for Num Siblings.
     */
    public static final String NUM_SIBLINGS_ELEMENT_NAME = "numSiblings";
    /**
     * The IBaseDataObject set method name for Num Siblings.
     */
    public static final String NUM_SIBLINGS_SET_METHOD_NAME = "setNumSiblings";
    /**
     * The XML element name for Outputable.
     */
    public static final String OUTPUTABLE_ELEMENT_NAME = "outputable";
    /**
     * The IBaseDataObject set method name for Outputable.
     */
    public static final String OUTPUTABLE_SET_METHOD_NAME = "setOutputable";
    /**
     * The XML element name for Parameters.
     */
    public static final String PARAMETER_ELEMENT_NAME = "meta";
    /**
     * The IBaseDataObject set method name for Parameters.
     */
    public static final String PARAMETER_SET_METHOD_NAME = "putParameter";
    /**
     * The XML element name for Priority.
     */
    public static final String PRIORITY_ELEMENT_NAME = "priority";
    /**
     * The IBaseDataObject set method name for Priority.
     */
    public static final String PRIORITY_SET_METHOD_NAME = "setPriority";
    /**
     * The XML element name for Processing Error.
     */
    public static final String PROCESSING_ERROR_ELEMENT_NAME = "processingError";
    /**
     * The IBaseDataObject set method name for Processing Error.
     */
    public static final String PROCESSING_ERROR_SET_METHOD_NAME = "addProcessingError";
    /**
     * The XML element name for Result.
     */
    public static final String RESULT_ELEMENT_NAME = "result";
    /**
     * The XML element name for Transaction Id.
     */
    public static final String TRANSACTION_ID_ELEMENT_NAME = "transactionId";
    /**
     * The IBaseDataObject set method name for Transaction Id.
     */
    public static final String TRANSACTION_ID_SET_METHOD_NAME = "setTransactionId";
    /**
     * The XML element name for Value.
     */
    public static final String VALUE_ELEMENT_NAME = "value";
    /**
     * The XML element name for View.
     */
    public static final String VIEW_ELEMENT_NAME = "view";
    /**
     * The IBaseDataObject set method name for View.
     */
    public static final String VIEW_SET_METHOD_NAME = "addAlternateView";
    /**
     * The XML element name for Work Bundle Id.
     */
    public static final String WORK_BUNDLE_ID_ELEMENT_NAME = "workBundleId";
    /**
     * The IBaseDataObject set method name for Work Bundle Id.
     */
    public static final String WORK_BUNDLE_ID_SET_METHOD_NAME = "setWorkBundleId";
    /**
     * The XML element name for Setup.
     */
    public static final String SETUP_ELEMENT_NAME = "setup";
    /**
     * The XML namespace for "xml".
     */
    public static final Namespace XML_NAMESPACE = Namespace.getNamespace(XMLConstants.XML_NS_PREFIX,
            XMLConstants.XML_NS_URI);
    /**
     * A map of element names of IBaseDataObject methods that get/set primitives and their default values.
     */
    public static final Map<String, Object> PRIMITVE_NAME_DEFAULT_MAP = Collections
            .unmodifiableMap(new ConcurrentHashMap<>(Stream.of(
                    new SimpleEntry<>(BIRTH_ORDER_ELEMENT_NAME, new BaseDataObject().getBirthOrder()),
                    new SimpleEntry<>(BROKEN_ELEMENT_NAME, new BaseDataObject().isBroken()),
                    new SimpleEntry<>(NUM_CHILDREN_ELEMENT_NAME, new BaseDataObject().getNumChildren()),
                    new SimpleEntry<>(NUM_SIBLINGS_ELEMENT_NAME, new BaseDataObject().getNumSiblings()),
                    new SimpleEntry<>(OUTPUTABLE_ELEMENT_NAME, new BaseDataObject().isOutputable()),
                    new SimpleEntry<>(PRIORITY_ELEMENT_NAME, new BaseDataObject().getPriority()))
                    .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue))));

    /**
     * Interface for decoding an element value.
     */
    private interface ElementDecoder {
        /**
         * Decodes an XML element.
         * 
         * @param element to decode.
         * @return the decoded element value.
         */
        Object decode(Element element);

        /**
         * Returns the class of the key for a mapped value or null for a non-mapped value.
         * 
         * @return the class of the key for a mapped value or null for a non-mapped value.
         */
        Class<?> getKeyClass();

        /**
         * Returns the class of the value, whether mapped or non-mapped.
         * 
         * @return the class of the value, whether mapped or non-mapped.
         */
        Class<?> getValueClass();
    }

    /**
     * Implementation of an XML element decoder that has a boolean value.
     */
    private static ElementDecoder booleanDecoder = new ElementDecoder() {
        @Override
        public Object decode(final Element element) {
            return Boolean.valueOf(element.getValue());
        }

        @Override
        public Class<?> getKeyClass() {
            return null;
        }

        @Override
        public Class<?> getValueClass() {
            return boolean.class;
        }
    };

    /**
     * Implementation of an XML element decoder that has a SeekableByteChannel value.
     */
    private static ElementDecoder seekableByteChannelFactoryDecoder = new ElementDecoder() {
        @Override
        public Object decode(final Element element) {
            final String elementValue = element.getValue();
            final String encoding = element.getAttributeValue(ENCODING_ATTRIBUTE_NAME);

            return InMemoryChannelFactory.create(extractBytes(encoding, elementValue));
        }

        @Override
        public Class<?> getKeyClass() {
            return null;
        }

        @Override
        public Class<?> getValueClass() {
            return SeekableByteChannelFactory.class;
        }
    };

    /**
     * Implementation of an XML element decoder that has a byte array value.
     */
    private static ElementDecoder byteArrayDecoder = new ElementDecoder() {
        @Override
        public Object decode(final Element element) {
            final String elementValue = element.getValue();
            final String encoding = element.getAttributeValue(ENCODING_ATTRIBUTE_NAME);

            return extractBytes(encoding, elementValue);
        }

        @Override
        public Class<?> getKeyClass() {
            return null;
        }

        @Override
        public Class<?> getValueClass() {
            return byte[].class;
        }
    };

    /**
     * Implementation of an XML element decoder that has an integer value.
     */
    private static ElementDecoder integerDecoder = new ElementDecoder() {
        @Override
        public Object decode(final Element element) {
            try {
                return Integer.decode(element.getValue());
            } catch (final NumberFormatException e) {
                return null;
            }
        }

        @Override
        public Class<?> getKeyClass() {
            return null;
        }

        @Override
        public Class<?> getValueClass() {
            return int.class;
        }
    };

    /**
     * Implementation of an XML element decoder that has a string value.
     */
    private static ElementDecoder stringDecoder = new ElementDecoder() {
        @Override
        public Object decode(final Element element) {
            final String elementValue = element.getValue();
            final String encoding = element.getAttributeValue(ENCODING_ATTRIBUTE_NAME);

            return new String(extractBytes(encoding, elementValue), StandardCharsets.UTF_8);
        }

        @Override
        public Class<?> getKeyClass() {
            return null;
        }

        @Override
        public Class<?> getValueClass() {
            return String.class;
        }
    };

    /**
     * Implementation of an XML element decoder that has a mapped value where the key is a string and the value is a byte
     * array.
     */
    private static ElementDecoder stringByteArrayDecoder = new ElementDecoder() {
        @Override
        public Object decode(final Element element) {
            final Element childElement = element.getChild(VALUE_ELEMENT_NAME);
            final String elementValue = childElement.getValue();
            final String encoding = childElement.getAttributeValue(ENCODING_ATTRIBUTE_NAME);

            return extractBytes(encoding, elementValue);
        }

        @Override
        public Class<?> getKeyClass() {
            return String.class;
        }

        @Override
        public Class<?> getValueClass() {
            return byte[].class;
        }
    };

    /**
     * Implementation of an XML element decoder that has a mapped value where the key is a string and the value is an
     * object.
     */
    private static ElementDecoder stringObjectDecoder = new ElementDecoder() {
        @Override
        public Object decode(final Element element) {
            final Element childElement = element.getChild(VALUE_ELEMENT_NAME);
            final String elementValue = childElement.getValue();
            final String encoding = childElement.getAttributeValue(ENCODING_ATTRIBUTE_NAME);

            return new String(extractBytes(encoding, elementValue), StandardCharsets.UTF_8);
        }

        @Override
        public Class<?> getKeyClass() {
            return String.class;
        }

        @Override
        public Class<?> getValueClass() {
            return Object.class;
        }
    };

    private IBaseDataObjectXmlHelper() {}

    /**
     * Return UTF8 bytes from an XML value, decoding base64 if required
     * 
     * @param encoding e.g. 'base64', otherwise it returns the bytes as they are presented
     * @param elementValue containing the data
     * @return the data from elementValue, decoded from base64 if required
     */
    private static byte[] extractBytes(final String encoding, final String elementValue) {
        if (BASE64_ATTRIBUTE_NAME.equalsIgnoreCase(encoding)) {
            final String newElementValue = elementValue.replace("\n", "");
            final byte[] bytes = newElementValue.getBytes(StandardCharsets.UTF_8);
            return BASE64_DECODER.decode(bytes);
        } else {
            return elementValue.getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * Setup a typical BDO
     * 
     * @param sbcf initial channel factory for the data
     * @param classification initial classification string
     * @param formAndFileType initial form and file type
     * @param kff an existing Kff handler
     * @return a typical BDO with the specified data
     */
    public static IBaseDataObject createStandardInitialIbdo(final SeekableByteChannelFactory sbcf,
            final String classification, final String formAndFileType, final KffDataObjectHandler kff) {
        final IBaseDataObject ibdo = new BaseDataObject();
        final IBaseDataObject tempIbdo = new BaseDataObject();

        // We want to return the ibdo with the data field equal to null. This can only
        // be accomplished if the data is never set. Therefore, we have to set the data
        // on a separate ibdo, hash the ibdo and then transfer just the parameters back
        // to the original ibdo.
        tempIbdo.setChannelFactory(sbcf);
        kff.hash(tempIbdo);
        ibdo.setParameters(tempIbdo.getParameters());

        ibdo.setCurrentForm(formAndFileType);
        ibdo.setFileType(formAndFileType);
        ibdo.setClassification(classification);

        return ibdo;
    }

    /**
     * Creates an IBaseDataObject and associated children from an XML document.
     * 
     * @param document containing the IBaseDataObject and children descriptions.
     * @param children the list where the children will be added.
     * @return the IBaseDataObject.
     */
    public static IBaseDataObject ibdoFromXml(final Document document, final List<IBaseDataObject> children) {
        Validate.notNull(document, "Required document != null!");
        Validate.notNull(children, "Required children != null!");

        final Element root = document.getRootElement();
        final Element answersElement = root.getChild(ANSWERS_ELEMENT_NAME);
        final IBaseDataObject parentIbdo = new BaseDataObject();
        final List<Element> answerChildren = answersElement.getChildren();

        ibdoFromXmlMainElements(answersElement, parentIbdo);

        for (final Element answerChild : answerChildren) {
            final IBaseDataObject childIbdo = new BaseDataObject();
            final String childName = answerChild.getName();

            if (childName.startsWith(EXTRACTED_RECORD_ELEMENT_PREFIX)) {
                parentIbdo.addExtractedRecord(ibdoFromXmlMainElements(answerChild, childIbdo));
            } else if (childName.startsWith(ATTACHMENT_ELEMENT_PREFIX)) {
                children.add(ibdoFromXmlMainElements(answerChild, childIbdo));
            }
        }

        return parentIbdo;
    }

    /**
     * Creates an IBaseDataObject from an XML element excluding Extracted Records and children.
     * 
     * @param element to create IBaseDataObject from.
     * @param ibdo to apply the element values to.
     * @return the IBaseDataObject that was passed in.
     */
    public static IBaseDataObject ibdoFromXmlMainElements(final Element element, final IBaseDataObject ibdo) {
        parseElement(element.getChild(DATA_ELEMENT_NAME), ibdo, DATA_SET_METHOD_NAME,
                seekableByteChannelFactoryDecoder);
        parseElement(element.getChild(BIRTH_ORDER_ELEMENT_NAME), ibdo, BIRTH_ORDER_SET_METHOD_NAME, integerDecoder);
        parseElement(element.getChild(BROKEN_ELEMENT_NAME), ibdo, BROKEN_SET_METHOD_NAME, stringDecoder);
        parseElement(element.getChild(CLASSIFICATION_ELEMENT_NAME), ibdo, CLASSIFICATION_SET_METHOD_NAME,
                stringDecoder);

        for (final Element currentForm : element.getChildren(CURRENT_FORM_ELEMENT_NAME)) {
            parseElement(currentForm, ibdo, CURRENT_FORM_SET_METHOD_NAME, stringDecoder);
        }

        parseElement(element.getChild(FILENAME_ELEMENT_NAME), ibdo, FILENAME_SET_METHOD_NAME, stringDecoder);
        parseElement(element.getChild(FONT_ENCODING_ELEMENT_NAME), ibdo, FONT_ENCODING_SET_METHOD_NAME, stringDecoder);
        parseElement(element.getChild(FOOTER_ELEMENT_NAME), ibdo, FOOTER_SET_METHOD_NAME, byteArrayDecoder);
        parseElement(element.getChild(HEADER_ELEMENT_NAME), ibdo, HEADER_SET_METHOD_NAME, byteArrayDecoder);
        parseElement(element.getChild(HEADER_ENCODING_ELEMENT_NAME), ibdo, HEADER_ENCODING_SET_METHOD_NAME,
                stringDecoder);
        parseElement(element.getChild(ID_ELEMENT_NAME), ibdo, ID_SET_METHOD_NAME, stringDecoder);
        parseElement(element.getChild(NUM_CHILDREN_ELEMENT_NAME), ibdo, NUM_CHILDREN_SET_METHOD_NAME, integerDecoder);
        parseElement(element.getChild(NUM_SIBLINGS_ELEMENT_NAME), ibdo, NUM_SIBLINGS_SET_METHOD_NAME, integerDecoder);
        parseElement(element.getChild(OUTPUTABLE_ELEMENT_NAME), ibdo, OUTPUTABLE_SET_METHOD_NAME, booleanDecoder);
        parseElement(element.getChild(PRIORITY_ELEMENT_NAME), ibdo, PRIORITY_SET_METHOD_NAME, integerDecoder);
        parseElement(element.getChild(PROCESSING_ERROR_ELEMENT_NAME), ibdo, PROCESSING_ERROR_SET_METHOD_NAME,
                stringDecoder);
        parseElement(element.getChild(TRANSACTION_ID_ELEMENT_NAME), ibdo, TRANSACTION_ID_SET_METHOD_NAME,
                stringDecoder);
        parseElement(element.getChild(WORK_BUNDLE_ID_ELEMENT_NAME), ibdo, WORK_BUNDLE_ID_SET_METHOD_NAME,
                stringDecoder);

        for (final Element parameter : element.getChildren(PARAMETER_ELEMENT_NAME)) {
            parseElement(parameter, ibdo, PARAMETER_SET_METHOD_NAME, stringObjectDecoder);
        }

        for (final Element view : element.getChildren(VIEW_ELEMENT_NAME)) {
            parseElement(view, ibdo, VIEW_SET_METHOD_NAME, stringByteArrayDecoder);
        }

        return ibdo;
    }

    /**
     * Parse an element to set the value on a BDO
     * 
     * @param element to get the data from
     * @param ibdo to set the data on
     * @param ibdoMethodName to use to set the data
     * @param elementDecoder to use to decode the element data
     */
    private static void parseElement(final Element element, final IBaseDataObject ibdo, final String ibdoMethodName,
            final ElementDecoder elementDecoder) {
        if (element != null) {
            final Object parameter = elementDecoder.decode(element);

            if (parameter != null) {
                setParameterOnIbdo(elementDecoder.getKeyClass(), elementDecoder.getValueClass(), ibdo, ibdoMethodName,
                        parameter, element);
            }
        }
    }

    /**
     * Set a parameter on a specific BDO
     * 
     * @param keyClass to use for the key, otherwise assumes string
     * @param valueClass to use for the value
     * @param ibdo to set the parameter on
     * @param ibdoMethodName method name to use (e.g. setFontEncoding)
     * @param parameter value to use
     * @param element to get the name from
     */
    private static void setParameterOnIbdo(final Class<?> keyClass, final Class<?> valueClass,
            final IBaseDataObject ibdo, final String ibdoMethodName, final Object parameter, final Element element) {
        try {
            if (keyClass == null) {
                final Method method = IBaseDataObject.class.getDeclaredMethod(ibdoMethodName, valueClass);

                method.invoke(ibdo, parameter);
            } else {
                final String name = (String) stringDecoder.decode(element.getChild(NAME_ELEMENT_NAME));
                final Method method = IBaseDataObject.class.getDeclaredMethod(ibdoMethodName, keyClass, valueClass);

                method.invoke(ibdo, name, parameter);
            }
        } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            LOGGER.warn("Unable to call ibdo method {}!", ibdoMethodName, e);
        }
    }

    /**
     * Creates an XML string from a parent IBaseDataObject and a list of children IBaseDataObjects.
     * 
     * @param parent the parent IBaseDataObject
     * @param children the children IBaseDataObjects.
     * @param initialIbdo the initial IBaseDataObject.
     * @return the XML string.
     */
    public static String xmlFromIbdo(final IBaseDataObject parent, final List<IBaseDataObject> children,
            final IBaseDataObject initialIbdo) {
        Validate.notNull(parent, "Required: parent != null!");
        Validate.notNull(children, "Required: children != null!");
        Validate.notNull(initialIbdo, "Required: initialIbdo != null!");

        final Element rootElement = new Element(RESULT_ELEMENT_NAME);
        final Element setupElement = new Element(SETUP_ELEMENT_NAME);

        rootElement.addContent(setupElement);

        xmlFromIbdoMainElements(initialIbdo, setupElement);

        final Element answersElement = new Element(ANSWERS_ELEMENT_NAME);

        rootElement.addContent(answersElement);

        xmlFromIbdoMainElements(parent, answersElement);

        final List<IBaseDataObject> extractedRecords = parent.getExtractedRecords();
        if (extractedRecords != null) {
            for (int i = 0; i < extractedRecords.size(); i++) {
                final IBaseDataObject extractedRecord = extractedRecords.get(i);
                final Element extractElement = new Element(EXTRACTED_RECORD_ELEMENT_PREFIX + (i + 1));

                xmlFromIbdoMainElements(extractedRecord, extractElement);

                answersElement.addContent(extractElement);
            }
        }

        for (int i = 0; i < children.size(); i++) {
            final IBaseDataObject child = children.get(i);
            final Element childElement = new Element(ATTACHMENT_ELEMENT_PREFIX + (i + 1));

            xmlFromIbdoMainElements(child, childElement);

            answersElement.addContent(childElement);
        }

        return AbstractJDOMUtil.toString(new Document(rootElement));
    }

    /**
     * Creates xml from the IBaseDataObject excluding the extracted records and children.
     * 
     * @param ibdo to create xml from.
     * @param element to add the xml to.
     */
    public static void xmlFromIbdoMainElements(final IBaseDataObject ibdo, final Element element) {
        addNonNullContent(element, DATA_ELEMENT_NAME, ibdo.getChannelFactory());
        addNonDefaultContent(element, BIRTH_ORDER_ELEMENT_NAME, ibdo.getBirthOrder());
        addNonNullContent(element, BROKEN_ELEMENT_NAME, ibdo.getBroken());
        addNonNullContent(element, CLASSIFICATION_ELEMENT_NAME, ibdo.getClassification());

        final int childCount = element.getChildren().size();
        for (final String currentForm : ibdo.getAllCurrentForms()) {
            element.addContent(childCount, protectedElement(CURRENT_FORM_ELEMENT_NAME, currentForm));
        }

        addNonNullContent(element, FILENAME_ELEMENT_NAME, ibdo.getFilename());
        addNonNullContent(element, FONT_ENCODING_ELEMENT_NAME, ibdo.getFontEncoding());
        addNonNullContent(element, FOOTER_ELEMENT_NAME, ibdo.footer());
        addNonNullContent(element, HEADER_ELEMENT_NAME, ibdo.header());
        addNonNullContent(element, HEADER_ENCODING_ELEMENT_NAME, ibdo.getHeaderEncoding());
        addNonNullContent(element, ID_ELEMENT_NAME, ibdo.getId());
        addNonDefaultContent(element, NUM_CHILDREN_ELEMENT_NAME, ibdo.getNumChildren());
        addNonDefaultContent(element, NUM_SIBLINGS_ELEMENT_NAME, ibdo.getNumSiblings());
        addNonDefaultContent(element, OUTPUTABLE_ELEMENT_NAME, ibdo.isOutputable());
        addNonDefaultContent(element, PRIORITY_ELEMENT_NAME, ibdo.getPriority());

        final String processingError = ibdo.getProcessingError();
        final String fixedProcessingError = processingError == null ? null
                : processingError.substring(0, processingError.length() - 1);
        addNonNullContent(element, PROCESSING_ERROR_ELEMENT_NAME, fixedProcessingError);

        addNonNullContent(element, TRANSACTION_ID_ELEMENT_NAME, ibdo.getTransactionId());
        addNonNullContent(element, WORK_BUNDLE_ID_ELEMENT_NAME, ibdo.getWorkBundleId());

        for (final Entry<String, Collection<Object>> parameter : ibdo.getParameters().entrySet()) {
            for (final Object item : parameter.getValue()) {
                final Element metaElement = new Element(PARAMETER_ELEMENT_NAME);

                element.addContent(metaElement);
                metaElement.addContent(preserve(protectedElement(NAME_ELEMENT_NAME, parameter.getKey())));
                metaElement.addContent(preserve(protectedElement(VALUE_ELEMENT_NAME, item.toString())));
            }
        }

        for (final Entry<String, byte[]> view : ibdo.getAlternateViews().entrySet()) {
            final Element metaElement = new Element(VIEW_ELEMENT_NAME);

            element.addContent(metaElement);
            metaElement.addContent(preserve(protectedElement(NAME_ELEMENT_NAME, view.getKey())));
            metaElement.addContent(preserve(protectedElement(VALUE_ELEMENT_NAME, view.getValue())));
        }
    }

    private static void addNonNullContent(final Element parent, final String elementName, final String string) {
        if (string != null) {
            parent.addContent(preserve(protectedElement(elementName, string)));
        }
    }

    private static void addNonNullContent(final Element parent, final String elementName, final byte[] bytes) {
        if (bytes != null) {
            parent.addContent(preserve(protectedElement(elementName, bytes)));
        }
    }

    private static void addNonNullContent(final Element parent, final String elementName,
            final SeekableByteChannelFactory seekableByteChannelFactory) {
        if (seekableByteChannelFactory != null) {
            try {
                final byte[] bytes = SeekableByteChannelHelper.getByteArrayFromChannel(seekableByteChannelFactory,
                        BaseDataObject.MAX_BYTE_ARRAY_SIZE);

                addNonNullContent(parent, elementName, bytes);
            } catch (final IOException e) {
                LOGGER.error("Could not get bytes from SeekableByteChannel!", e);
            }
        }
    }

    private static void addNonDefaultContent(final Element parent, final String elementName, final boolean bool) {
        if (((Boolean) PRIMITVE_NAME_DEFAULT_MAP.get(elementName)).booleanValue() != bool) {
            parent.addContent(AbstractJDOMUtil.simpleElement(elementName, bool));
        }
    }

    private static void addNonDefaultContent(final Element parent, final String elementName, final int integer) {
        if (((Integer) PRIMITVE_NAME_DEFAULT_MAP.get(elementName)).intValue() != integer) {
            parent.addContent(AbstractJDOMUtil.simpleElement(elementName, integer));
        }
    }

    private static Element preserve(final Element element) {
        element.setAttribute("space", "preserve", XML_NAMESPACE);

        return element;
    }

    private static Element protectedElement(final String name, final String string) {
        return protectedElement(name, string.getBytes(StandardCharsets.UTF_8));
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
    private static Element protectedElement(final String name, final byte[] bytes) {
        final Element element = new Element(name);

        boolean badCharacters = false;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] < 9 || bytes[i] > 13 && bytes[i] < 32) {
                badCharacters = true;
                break;
            }
        }
        if (badCharacters) {
            final StringBuilder base64String = new StringBuilder();
            base64String.append(BASE64_NEW_LINE_STRING);
            base64String.append(BASE64_ENCODER.encodeToString(bytes));
            base64String.append(BASE64_NEW_LINE_STRING);

            element.setAttribute(ENCODING_ATTRIBUTE_NAME, BASE64_ATTRIBUTE_NAME);
            element.addContent(base64String.toString());
        } else {
            element.addContent(new String(bytes, StandardCharsets.ISO_8859_1));
        }

        return element;
    }
}
