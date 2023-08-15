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

import static emissary.core.constants.IbdoMethodNames.ADD_ALTERNATE_VIEW;
import static emissary.core.constants.IbdoMethodNames.ADD_PROCESSING_ERROR;
import static emissary.core.constants.IbdoMethodNames.SET_BIRTH_ORDER;
import static emissary.core.constants.IbdoMethodNames.SET_BROKEN;
import static emissary.core.constants.IbdoMethodNames.SET_CLASSIFICATION;
import static emissary.core.constants.IbdoMethodNames.SET_CURRENT_FORM;
import static emissary.core.constants.IbdoMethodNames.SET_DATA;
import static emissary.core.constants.IbdoMethodNames.SET_FILENAME;
import static emissary.core.constants.IbdoMethodNames.SET_FONT_ENCODING;
import static emissary.core.constants.IbdoMethodNames.SET_FOOTER;
import static emissary.core.constants.IbdoMethodNames.SET_HEADER;
import static emissary.core.constants.IbdoMethodNames.SET_HEADER_ENCODING;
import static emissary.core.constants.IbdoMethodNames.SET_ID;
import static emissary.core.constants.IbdoMethodNames.SET_NUM_CHILDREN;
import static emissary.core.constants.IbdoMethodNames.SET_NUM_SIBLINGS;
import static emissary.core.constants.IbdoMethodNames.SET_OUTPUTABLE;
import static emissary.core.constants.IbdoMethodNames.SET_PARAMETER;
import static emissary.core.constants.IbdoMethodNames.SET_PRIORITY;
import static emissary.core.constants.IbdoMethodNames.SET_TRANSACTION_ID;
import static emissary.core.constants.IbdoMethodNames.SET_WORK_BUNDLE_ID;
import static emissary.core.constants.IbdoXmlElementNames.ANSWERS;
import static emissary.core.constants.IbdoXmlElementNames.ATTACHMENT_ELEMENT_PREFIX;
import static emissary.core.constants.IbdoXmlElementNames.BIRTH_ORDER;
import static emissary.core.constants.IbdoXmlElementNames.BROKEN;
import static emissary.core.constants.IbdoXmlElementNames.CLASSIFICATION;
import static emissary.core.constants.IbdoXmlElementNames.CURRENT_FORM;
import static emissary.core.constants.IbdoXmlElementNames.DATA;
import static emissary.core.constants.IbdoXmlElementNames.EXTRACTED_RECORD_ELEMENT_PREFIX;
import static emissary.core.constants.IbdoXmlElementNames.FILENAME;
import static emissary.core.constants.IbdoXmlElementNames.FONT_ENCODING;
import static emissary.core.constants.IbdoXmlElementNames.FOOTER;
import static emissary.core.constants.IbdoXmlElementNames.HEADER;
import static emissary.core.constants.IbdoXmlElementNames.HEADER_ENCODING;
import static emissary.core.constants.IbdoXmlElementNames.ID;
import static emissary.core.constants.IbdoXmlElementNames.NUM_CHILDREN;
import static emissary.core.constants.IbdoXmlElementNames.NUM_SIBLINGS;
import static emissary.core.constants.IbdoXmlElementNames.OUTPUTABLE;
import static emissary.core.constants.IbdoXmlElementNames.PARAMETER;
import static emissary.core.constants.IbdoXmlElementNames.PRIORITY;
import static emissary.core.constants.IbdoXmlElementNames.PROCESSING_ERROR;
import static emissary.core.constants.IbdoXmlElementNames.RESULT;
import static emissary.core.constants.IbdoXmlElementNames.SETUP;
import static emissary.core.constants.IbdoXmlElementNames.TRANSACTION_ID;
import static emissary.core.constants.IbdoXmlElementNames.VIEW;
import static emissary.core.constants.IbdoXmlElementNames.WORK_BUNDLE_ID;

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
        final Element answersElement = root.getChild(ANSWERS);
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
            decoders.integerDecoder.decode(element.getChildren(BIRTH_ORDER), ibdo, SET_BIRTH_ORDER);
            decoders.stringDecoder.decode(element.getChildren(BROKEN), ibdo, SET_BROKEN);
            decoders.stringDecoder.decode(element.getChildren(CLASSIFICATION), ibdo, SET_CLASSIFICATION);
            decoders.stringDecoder.decode(element.getChildren(CURRENT_FORM), ibdo, SET_CURRENT_FORM);
            decoders.seekableByteChannelFactoryDecoder.decode(element.getChildren(DATA), ibdo, SET_DATA);
            decoders.stringDecoder.decode(element.getChildren(FILENAME), ibdo, SET_FILENAME);
            decoders.stringDecoder.decode(element.getChildren(FONT_ENCODING), ibdo, SET_FONT_ENCODING);
            decoders.byteArrayDecoder.decode(element.getChildren(FOOTER), ibdo, SET_FOOTER);
            decoders.byteArrayDecoder.decode(element.getChildren(HEADER), ibdo, SET_HEADER);
            decoders.stringDecoder.decode(element.getChildren(HEADER_ENCODING), ibdo, SET_HEADER_ENCODING);
            decoders.stringDecoder.decode(element.getChildren(ID), ibdo, SET_ID);
            decoders.integerDecoder.decode(element.getChildren(NUM_CHILDREN), ibdo, SET_NUM_CHILDREN);
            decoders.integerDecoder.decode(element.getChildren(NUM_SIBLINGS), ibdo, SET_NUM_SIBLINGS);
            decoders.booleanDecoder.decode(element.getChildren(OUTPUTABLE), ibdo, SET_OUTPUTABLE);
            decoders.stringObjectDecoder.decode(element.getChildren(PARAMETER), ibdo, SET_PARAMETER);
            decoders.integerDecoder.decode(element.getChildren(PRIORITY), ibdo, SET_PRIORITY);
            decoders.stringDecoder.decode(element.getChildren(PROCESSING_ERROR), ibdo, ADD_PROCESSING_ERROR);
            decoders.stringDecoder.decode(element.getChildren(TRANSACTION_ID), ibdo, SET_TRANSACTION_ID);
            decoders.stringByteArrayDecoder.decode(element.getChildren(VIEW), ibdo, ADD_ALTERNATE_VIEW);
            decoders.stringDecoder.decode(element.getChildren(WORK_BUNDLE_ID), ibdo, SET_WORK_BUNDLE_ID);
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

        final Element rootElement = new Element(RESULT);
        final Element setupElement = new Element(SETUP);

        rootElement.addContent(setupElement);

        xmlFromIbdoMainElements(initialIbdo, setupElement, encoders);

        final Element answersElement = new Element(ANSWERS);

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

        encoders.seekableByteChannelFactoryEncoder.encode(Collections.singletonList(ibdo.getChannelFactory()), element, DATA);
        encoders.integerEncoder.encode(Collections.singletonList(ibdo.getBirthOrder()), element, BIRTH_ORDER);
        encoders.stringEncoder.encode(Collections.singletonList(ibdo.getBroken()), element, BROKEN);
        encoders.stringEncoder.encode(Collections.singletonList(ibdo.getClassification()), element, CLASSIFICATION);
        encoders.stringEncoder.encode(ibdo.getAllCurrentForms(), element, CURRENT_FORM);
        encoders.stringEncoder.encode(Collections.singletonList(ibdo.getFilename()), element, FILENAME);
        encoders.stringEncoder.encode(Collections.singletonList(ibdo.getFontEncoding()), element, FONT_ENCODING);
        encoders.byteArrayEncoder.encode(Collections.singletonList(ibdo.footer()), element, FOOTER);
        encoders.byteArrayEncoder.encode(Collections.singletonList(ibdo.header()), element, HEADER);
        encoders.stringEncoder.encode(Collections.singletonList(ibdo.getHeaderEncoding()), element, HEADER_ENCODING);
        encoders.stringEncoder.encode(Collections.singletonList(ibdo.getId()), element, ID);
        encoders.integerEncoder.encode(Collections.singletonList(ibdo.getNumChildren()), element, NUM_CHILDREN);
        encoders.integerEncoder.encode(Collections.singletonList(ibdo.getNumSiblings()), element, NUM_SIBLINGS);
        encoders.booleanEncoder.encode(Collections.singletonList(ibdo.isOutputable()), element, OUTPUTABLE);
        encoders.integerEncoder.encode(Collections.singletonList(ibdo.getPriority()), element, PRIORITY);
        encoders.stringEncoder.encode(Collections.singletonList(ibdo.getProcessingError()), element, PROCESSING_ERROR);
        encoders.stringEncoder.encode(Collections.singletonList(ibdo.getTransactionId()), element, TRANSACTION_ID);
        encoders.stringEncoder.encode(Collections.singletonList(ibdo.getWorkBundleId()), element, WORK_BUNDLE_ID);
        encoders.stringObjectEncoder.encode(Collections.singletonList(ibdo.getParameters()), element, PARAMETER);
        encoders.stringByteArrayEncoder.encode(Collections.singletonList(ibdo.getAlternateViews()), element, VIEW);
    }
}
