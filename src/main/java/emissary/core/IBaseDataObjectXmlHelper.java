package emissary.core;

import emissary.core.IBaseDataObjectXmlCodecs.ElementDecoders;
import emissary.core.IBaseDataObjectXmlCodecs.ElementEncoders;
import emissary.core.channels.SeekableByteChannelFactory;
import emissary.kff.KffDataObjectHandler;
import emissary.util.xml.AbstractJDOMUtil;

import org.apache.commons.lang3.Validate;
import org.jdom2.Document;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

import static emissary.core.constants.AnswerXMLFileElementNames.ANSWERS_ELEMENT_NAME;
import static emissary.core.constants.AnswerXMLFileElementNames.ATTACHMENT_ELEMENT_PREFIX;
import static emissary.core.constants.AnswerXMLFileElementNames.BIRTH_ORDER_ELEMENT_NAME;
import static emissary.core.constants.AnswerXMLFileElementNames.BROKEN_ELEMENT_NAME;
import static emissary.core.constants.AnswerXMLFileElementNames.CLASSIFICATION_ELEMENT_NAME;
import static emissary.core.constants.AnswerXMLFileElementNames.CURRENT_FORM_ELEMENT_NAME;
import static emissary.core.constants.AnswerXMLFileElementNames.DATA_ELEMENT_NAME;
import static emissary.core.constants.AnswerXMLFileElementNames.EXTRACTED_RECORD_ELEMENT_PREFIX;
import static emissary.core.constants.AnswerXMLFileElementNames.FILENAME_ELEMENT_NAME;
import static emissary.core.constants.AnswerXMLFileElementNames.FONT_ENCODING_ELEMENT_NAME;
import static emissary.core.constants.AnswerXMLFileElementNames.FOOTER_ELEMENT_NAME;
import static emissary.core.constants.AnswerXMLFileElementNames.HEADER_ELEMENT_NAME;
import static emissary.core.constants.AnswerXMLFileElementNames.HEADER_ENCODING_ELEMENT_NAME;
import static emissary.core.constants.AnswerXMLFileElementNames.ID_ELEMENT_NAME;
import static emissary.core.constants.AnswerXMLFileElementNames.NUM_CHILDREN_ELEMENT_NAME;
import static emissary.core.constants.AnswerXMLFileElementNames.NUM_SIBLINGS_ELEMENT_NAME;
import static emissary.core.constants.AnswerXMLFileElementNames.OUTPUTABLE_ELEMENT_NAME;
import static emissary.core.constants.AnswerXMLFileElementNames.PARAMETER_ELEMENT_NAME;
import static emissary.core.constants.AnswerXMLFileElementNames.PRIORITY_ELEMENT_NAME;
import static emissary.core.constants.AnswerXMLFileElementNames.PROCESSING_ERROR_ELEMENT_NAME;
import static emissary.core.constants.AnswerXMLFileElementNames.RESULT_ELEMENT_NAME;
import static emissary.core.constants.AnswerXMLFileElementNames.SETUP_ELEMENT_NAME;
import static emissary.core.constants.AnswerXMLFileElementNames.TRANSACTION_ID_ELEMENT_NAME;
import static emissary.core.constants.AnswerXMLFileElementNames.VIEW_ELEMENT_NAME;
import static emissary.core.constants.AnswerXMLFileElementNames.WORK_BUNDLE_ID_ELEMENT_NAME;
import static emissary.core.constants.IBDOMethodNames.BIRTH_ORDER_SET_METHOD_NAME;
import static emissary.core.constants.IBDOMethodNames.BROKEN_SET_METHOD_NAME;
import static emissary.core.constants.IBDOMethodNames.CLASSIFICATION_SET_METHOD_NAME;
import static emissary.core.constants.IBDOMethodNames.CURRENT_FORM_SET_METHOD_NAME;
import static emissary.core.constants.IBDOMethodNames.DATA_SET_METHOD_NAME;
import static emissary.core.constants.IBDOMethodNames.FILENAME_SET_METHOD_NAME;
import static emissary.core.constants.IBDOMethodNames.FONT_ENCODING_SET_METHOD_NAME;
import static emissary.core.constants.IBDOMethodNames.FOOTER_SET_METHOD_NAME;
import static emissary.core.constants.IBDOMethodNames.HEADER_ENCODING_SET_METHOD_NAME;
import static emissary.core.constants.IBDOMethodNames.HEADER_SET_METHOD_NAME;
import static emissary.core.constants.IBDOMethodNames.ID_SET_METHOD_NAME;
import static emissary.core.constants.IBDOMethodNames.NUM_CHILDREN_SET_METHOD_NAME;
import static emissary.core.constants.IBDOMethodNames.NUM_SIBLINGS_SET_METHOD_NAME;
import static emissary.core.constants.IBDOMethodNames.OUTPUTABLE_SET_METHOD_NAME;
import static emissary.core.constants.IBDOMethodNames.PARAMETER_SET_METHOD_NAME;
import static emissary.core.constants.IBDOMethodNames.PRIORITY_SET_METHOD_NAME;
import static emissary.core.constants.IBDOMethodNames.PROCESSING_ERROR_SET_METHOD_NAME;
import static emissary.core.constants.IBDOMethodNames.TRANSACTION_ID_SET_METHOD_NAME;
import static emissary.core.constants.IBDOMethodNames.VIEW_SET_METHOD_NAME;
import static emissary.core.constants.IBDOMethodNames.WORK_BUNDLE_ID_SET_METHOD_NAME;

/**
 * This class helps convert IBaseDataObjects to and from XML.
 */
public final class IBaseDataObjectXmlHelper {
    /**
     * Logger instance
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(IBaseDataObjectXmlHelper.class);

    private IBaseDataObjectXmlHelper() {}

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
     * @param decoders used to decode XML into ibdo.
     * @return the IBaseDataObject.
     */
    public static IBaseDataObject ibdoFromXml(final Document document, final List<IBaseDataObject> children, final ElementDecoders decoders) {
        Validate.notNull(document, "Required document != null!");
        Validate.notNull(children, "Required children != null!");
        Validate.notNull(decoders, "Required: decoders not null!");

        final Element root = document.getRootElement();
        final Element answersElement = root.getChild(ANSWERS_ELEMENT_NAME);
        final IBaseDataObject parentIbdo = new BaseDataObject();
        final List<Element> answerChildren = answersElement.getChildren();

        ibdoFromXmlMainElements(answersElement, parentIbdo, decoders);

        for (final Element answerChild : answerChildren) {
            final IBaseDataObject childIbdo = new BaseDataObject();
            final String childName = answerChild.getName();

            if (childName.startsWith(EXTRACTED_RECORD_ELEMENT_PREFIX)) {
                parentIbdo.addExtractedRecord(ibdoFromXmlMainElements(answerChild, childIbdo, decoders));
            } else if (childName.startsWith(ATTACHMENT_ELEMENT_PREFIX)) {
                children.add(ibdoFromXmlMainElements(answerChild, childIbdo, decoders));
            }
        }

        return parentIbdo;
    }

    /**
     * Creates an IBaseDataObject from an XML element excluding Extracted Records and children.
     * 
     * @param element to create IBaseDataObject from.
     * @param ibdo to apply the element values to.
     * @param decoders used to decode XML into ibdo.
     * @return the IBaseDataObject that was passed in.
     */
    public static IBaseDataObject ibdoFromXmlMainElements(final Element element, final IBaseDataObject ibdo,
            final ElementDecoders decoders) {
        Validate.notNull(element, "Required: element not null!");
        Validate.notNull(ibdo, "Required: ibdo not null!");
        Validate.notNull(decoders, "Required: decoders not null!");

        try {
            decoders.integerDecoder.decode(element.getChildren(BIRTH_ORDER_ELEMENT_NAME), ibdo, BIRTH_ORDER_SET_METHOD_NAME);
            decoders.stringDecoder.decode(element.getChildren(BROKEN_ELEMENT_NAME), ibdo, BROKEN_SET_METHOD_NAME);
            decoders.stringDecoder.decode(element.getChildren(CLASSIFICATION_ELEMENT_NAME), ibdo, CLASSIFICATION_SET_METHOD_NAME);
            decoders.stringDecoder.decode(element.getChildren(CURRENT_FORM_ELEMENT_NAME), ibdo, CURRENT_FORM_SET_METHOD_NAME);
            decoders.seekableByteChannelFactoryDecoder.decode(element.getChildren(DATA_ELEMENT_NAME), ibdo, DATA_SET_METHOD_NAME);
            decoders.stringDecoder.decode(element.getChildren(FILENAME_ELEMENT_NAME), ibdo, FILENAME_SET_METHOD_NAME);
            decoders.stringDecoder.decode(element.getChildren(FONT_ENCODING_ELEMENT_NAME), ibdo, FONT_ENCODING_SET_METHOD_NAME);
            decoders.byteArrayDecoder.decode(element.getChildren(FOOTER_ELEMENT_NAME), ibdo, FOOTER_SET_METHOD_NAME);
            decoders.byteArrayDecoder.decode(element.getChildren(HEADER_ELEMENT_NAME), ibdo, HEADER_SET_METHOD_NAME);
            decoders.stringDecoder.decode(element.getChildren(HEADER_ENCODING_ELEMENT_NAME), ibdo, HEADER_ENCODING_SET_METHOD_NAME);
            decoders.stringDecoder.decode(element.getChildren(ID_ELEMENT_NAME), ibdo, ID_SET_METHOD_NAME);
            decoders.integerDecoder.decode(element.getChildren(NUM_CHILDREN_ELEMENT_NAME), ibdo, NUM_CHILDREN_SET_METHOD_NAME);
            decoders.integerDecoder.decode(element.getChildren(NUM_SIBLINGS_ELEMENT_NAME), ibdo, NUM_SIBLINGS_SET_METHOD_NAME);
            decoders.booleanDecoder.decode(element.getChildren(OUTPUTABLE_ELEMENT_NAME), ibdo, OUTPUTABLE_SET_METHOD_NAME);
            decoders.stringObjectDecoder.decode(element.getChildren(PARAMETER_ELEMENT_NAME), ibdo, PARAMETER_SET_METHOD_NAME);
            decoders.integerDecoder.decode(element.getChildren(PRIORITY_ELEMENT_NAME), ibdo, PRIORITY_SET_METHOD_NAME);
            decoders.stringDecoder.decode(element.getChildren(PROCESSING_ERROR_ELEMENT_NAME), ibdo, PROCESSING_ERROR_SET_METHOD_NAME);
            decoders.stringDecoder.decode(element.getChildren(TRANSACTION_ID_ELEMENT_NAME), ibdo, TRANSACTION_ID_SET_METHOD_NAME);
            decoders.stringByteArrayDecoder.decode(element.getChildren(VIEW_ELEMENT_NAME), ibdo, VIEW_SET_METHOD_NAME);
            decoders.stringDecoder.decode(element.getChildren(WORK_BUNDLE_ID_ELEMENT_NAME), ibdo, WORK_BUNDLE_ID_SET_METHOD_NAME);
        } catch (Exception e) {
            LOGGER.error("Failed to parse XML!", e);
        }

        return ibdo;
    }

    /**
     * Creates an XML string from a parent IBaseDataObject and a list of children IBaseDataObjects.
     * 
     * @param parent the parent IBaseDataObject
     * @param children the children IBaseDataObjects.
     * @param initialIbdo the initial IBaseDataObject.
     * @param encoders used to encode ibdo into XML.
     * @return the XML string.
     */
    public static String xmlFromIbdo(final IBaseDataObject parent, final List<IBaseDataObject> children,
            final IBaseDataObject initialIbdo, final ElementEncoders encoders) {
        Validate.notNull(parent, "Required: parent != null!");
        Validate.notNull(children, "Required: children != null!");
        Validate.notNull(initialIbdo, "Required: initialIbdo != null!");
        Validate.notNull(encoders, "Required: encoders not null!");

        final Element rootElement = new Element(RESULT_ELEMENT_NAME);
        final Element setupElement = new Element(SETUP_ELEMENT_NAME);

        rootElement.addContent(setupElement);

        xmlFromIbdoMainElements(initialIbdo, setupElement, encoders);

        final Element answersElement = new Element(ANSWERS_ELEMENT_NAME);

        rootElement.addContent(answersElement);

        xmlFromIbdoMainElements(parent, answersElement, encoders);

        final List<IBaseDataObject> extractedRecords = parent.getExtractedRecords();
        if (extractedRecords != null) {
            for (int i = 0; i < extractedRecords.size(); i++) {
                final IBaseDataObject extractedRecord = extractedRecords.get(i);
                final Element extractElement = new Element(EXTRACTED_RECORD_ELEMENT_PREFIX + (i + 1));

                xmlFromIbdoMainElements(extractedRecord, extractElement, encoders);

                answersElement.addContent(extractElement);
            }
        }

        for (int i = 0; i < children.size(); i++) {
            final IBaseDataObject child = children.get(i);
            final Element childElement = new Element(ATTACHMENT_ELEMENT_PREFIX + (i + 1));

            xmlFromIbdoMainElements(child, childElement, encoders);

            answersElement.addContent(childElement);
        }

        return AbstractJDOMUtil.toString(new Document(rootElement));
    }

    /**
     * Creates xml from the IBaseDataObject excluding the extracted records and children.
     * 
     * @param ibdo to create xml from.
     * @param element to add the xml to.
     * @param encoders used to encode ibdo into XML.
     */
    public static void xmlFromIbdoMainElements(final IBaseDataObject ibdo, final Element element, final ElementEncoders encoders) {
        Validate.notNull(ibdo, "Required: ibdo not null!");
        Validate.notNull(element, "Required: element not null!");
        Validate.notNull(encoders, "Required: encoders not null!");

        encoders.seekableByteChannelFactoryEncoder.encode(Collections.singletonList(ibdo.getChannelFactory()), element, DATA_ELEMENT_NAME);
        encoders.integerEncoder.encode(Collections.singletonList(ibdo.getBirthOrder()), element, BIRTH_ORDER_ELEMENT_NAME);
        encoders.stringEncoder.encode(Collections.singletonList(ibdo.getBroken()), element, BROKEN_ELEMENT_NAME);
        encoders.stringEncoder.encode(Collections.singletonList(ibdo.getClassification()), element, CLASSIFICATION_ELEMENT_NAME);
        encoders.stringEncoder.encode(ibdo.getAllCurrentForms(), element, CURRENT_FORM_ELEMENT_NAME);
        encoders.stringEncoder.encode(Collections.singletonList(ibdo.getFilename()), element, FILENAME_ELEMENT_NAME);
        encoders.stringEncoder.encode(Collections.singletonList(ibdo.getFontEncoding()), element, FONT_ENCODING_ELEMENT_NAME);
        encoders.byteArrayEncoder.encode(Collections.singletonList(ibdo.footer()), element, FOOTER_ELEMENT_NAME);
        encoders.byteArrayEncoder.encode(Collections.singletonList(ibdo.header()), element, HEADER_ELEMENT_NAME);
        encoders.stringEncoder.encode(Collections.singletonList(ibdo.getHeaderEncoding()), element, HEADER_ENCODING_ELEMENT_NAME);
        encoders.stringEncoder.encode(Collections.singletonList(ibdo.getId()), element, ID_ELEMENT_NAME);
        encoders.integerEncoder.encode(Collections.singletonList(ibdo.getNumChildren()), element, NUM_CHILDREN_ELEMENT_NAME);
        encoders.integerEncoder.encode(Collections.singletonList(ibdo.getNumSiblings()), element, NUM_SIBLINGS_ELEMENT_NAME);
        encoders.booleanEncoder.encode(Collections.singletonList(ibdo.isOutputable()), element, OUTPUTABLE_ELEMENT_NAME);
        encoders.integerEncoder.encode(Collections.singletonList(ibdo.getPriority()), element, PRIORITY_ELEMENT_NAME);
        encoders.stringEncoder.encode(Collections.singletonList(ibdo.getProcessingError()), element, PROCESSING_ERROR_ELEMENT_NAME);
        encoders.stringEncoder.encode(Collections.singletonList(ibdo.getTransactionId()), element, TRANSACTION_ID_ELEMENT_NAME);
        encoders.stringEncoder.encode(Collections.singletonList(ibdo.getWorkBundleId()), element, WORK_BUNDLE_ID_ELEMENT_NAME);
        encoders.stringObjectEncoder.encode(Collections.singletonList(ibdo.getParameters()), element, PARAMETER_ELEMENT_NAME);
        encoders.stringByteArrayEncoder.encode(Collections.singletonList(ibdo.getAlternateViews()), element, VIEW_ELEMENT_NAME);
    }
}
