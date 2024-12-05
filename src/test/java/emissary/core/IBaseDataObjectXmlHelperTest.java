package emissary.core;

import emissary.core.IBaseDataObjectXmlCodecs.ElementEncoders;
import emissary.core.channels.InMemoryChannelFactory;
import emissary.core.channels.SeekableByteChannelFactory;
import emissary.core.constants.IbdoXmlElementNames;
import emissary.kff.KffDataObjectHandler;
import emissary.test.core.junit5.UnitTest;
import emissary.util.ByteUtil;
import emissary.util.PlaceComparisonHelper;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TreeMap;
import javax.annotation.Nullable;

import static emissary.core.IBaseDataObjectXmlCodecs.DEFAULT_ELEMENT_DECODERS;
import static emissary.core.IBaseDataObjectXmlCodecs.DEFAULT_ELEMENT_ENCODERS;
import static emissary.core.IBaseDataObjectXmlCodecs.SHA256_ELEMENT_ENCODERS;
import static emissary.core.IBaseDataObjectXmlCodecs.extractBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IBaseDataObjectXmlHelperTest extends UnitTest {
    @Test
    void testParentIbdoNoFieldsChanged() throws Exception {
        final IBaseDataObject initialIbdo = new BaseDataObject();
        final IBaseDataObject expectedIbdo = new BaseDataObject();
        final List<IBaseDataObject> expectedChildren = new ArrayList<>();
        final List<IBaseDataObject> actualChildren = new ArrayList<>();

        final IBaseDataObject actualIbdo = ibdoFromXmlFromIbdo(expectedIbdo, expectedChildren, initialIbdo,
                actualChildren, DEFAULT_ELEMENT_ENCODERS);
        final String diff = PlaceComparisonHelper.checkDifferences(expectedIbdo, actualIbdo, expectedChildren,
                actualChildren, "testParentIbdoNoFieldsChanged", DiffCheckConfiguration.onlyCheckData());

        assertNull(diff);

        final IBaseDataObject sha256ActualIbdo = ibdoFromXmlFromIbdo(expectedIbdo, expectedChildren, initialIbdo,
                actualChildren, SHA256_ELEMENT_ENCODERS);
        final String sha256Diff = PlaceComparisonHelper.checkDifferences(expectedIbdo, sha256ActualIbdo, expectedChildren,
                actualChildren, "testParentIbdoNoFieldsChangedSha256", DiffCheckConfiguration.onlyCheckData());

        assertNull(sha256Diff);
    }

    public static void setAllFieldsPrintable(final IBaseDataObject ibdo, final byte[] bytes) {
        ibdo.setData(bytes);
        ibdo.setBirthOrder(5);
        ibdo.setBroken("Broken1");
        ibdo.setBroken("Broken2");
        ibdo.setClassification("Classification");
        ibdo.pushCurrentForm("Form1");
        ibdo.pushCurrentForm("Form2");
        ibdo.setFilename("Filename");
        ibdo.setFileType("FileType");
        ibdo.setFontEncoding("FontEncoding");
        ibdo.setFooter("Footer".getBytes(StandardCharsets.UTF_8));
        ibdo.setHeader("Header".getBytes(StandardCharsets.UTF_8));
        ibdo.setHeaderEncoding("HeaderEncoding");
        ibdo.setId("Id");
        ibdo.setNumChildren(9);
        ibdo.setNumSiblings(10);
        ibdo.setOutputable(false);
        ibdo.setPriority(1);
        ibdo.addProcessingError("ProcessingError1");
        ibdo.addProcessingError("ProcessingError2");
        ibdo.setTransactionId("TransactionId");
        ibdo.setWorkBundleId("WorkBundleId");
        ibdo.putParameter("Parameter1Key", "Parameter1Value");
        ibdo.putParameter("Parameter2Key", Arrays.asList("Parameter2Value1", "Parameter2Value2"));
        ibdo.putParameter("Parameter3Key", Arrays.asList(10L, 20L));
        ibdo.addAlternateView("AlternateView1Key", "AlternateView1Value".getBytes(StandardCharsets.UTF_8));
        ibdo.addAlternateView("AlternateView11Key", "AlternateView11Value".getBytes(StandardCharsets.UTF_8));
    }

    public static void setAllFieldsNonPrintable(final IBaseDataObject ibdo, final byte[] bytes) {
        ibdo.setData(bytes);
        ibdo.setBirthOrder(5);
        ibdo.setBroken("\001Broken1");
        ibdo.setBroken("\001Broken2");
        ibdo.setClassification("\001Classification");
        ibdo.pushCurrentForm("Form1");
        ibdo.pushCurrentForm("Form2");
        ibdo.setFilename("\001Filename");
        ibdo.setFileType("\001FileType");
        ibdo.setFontEncoding("\001FontEncoding");
        ibdo.setFooter("\001Footer".getBytes(StandardCharsets.UTF_8));
        ibdo.setHeader("\001Header".getBytes(StandardCharsets.UTF_8));
        ibdo.setHeaderEncoding("\001HeaderEncoding");
        ibdo.setId("\001Id");
        ibdo.setNumChildren(9);
        ibdo.setNumSiblings(10);
        ibdo.setOutputable(false);
        ibdo.setPriority(1);
        ibdo.addProcessingError("\001ProcessingError1");
        ibdo.addProcessingError("\001ProcessingError2");
        ibdo.setTransactionId("\001TransactionId");
        ibdo.setWorkBundleId("\001WorkBundleId");
        ibdo.putParameter("\020Parameter1Key", "\020Parameter1Value");
        ibdo.putParameter("\020Parameter2Key", "\020Parameter2Value");
        ibdo.addAlternateView("\200AlternateView1Key",
                "\200AlternateView1Value".getBytes(StandardCharsets.UTF_8));
        ibdo.addAlternateView("\200AlternateView11Key",
                "\200AlternateView11Value".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testParentIbdoAllFieldsChanged() throws Exception {
        final IBaseDataObject initialIbdo = new BaseDataObject();
        final IBaseDataObject expectedIbdo = new BaseDataObject();
        final List<IBaseDataObject> expectedChildren = new ArrayList<>();
        final List<IBaseDataObject> actualChildren = new ArrayList<>();
        final byte[] bytes = "Data".getBytes(StandardCharsets.UTF_8);

        setAllFieldsPrintable(expectedIbdo, bytes);

        final IBaseDataObject actualIbdo = ibdoFromXmlFromIbdo(expectedIbdo, expectedChildren, initialIbdo,
                actualChildren, DEFAULT_ELEMENT_ENCODERS);
        final String diff = PlaceComparisonHelper.checkDifferences(expectedIbdo, actualIbdo, expectedChildren,
                actualChildren, "testParentIbdoAllFieldsChanged", DiffCheckConfiguration.onlyCheckData());

        assertNull(diff);

        final IBaseDataObject sha256ActualIbdo = ibdoFromXmlFromIbdo(expectedIbdo, expectedChildren, initialIbdo,
                actualChildren, SHA256_ELEMENT_ENCODERS);
        final String sha256Diff = PlaceComparisonHelper.checkDifferences(expectedIbdo, sha256ActualIbdo, expectedChildren,
                actualChildren, "testParentIbdoAllFieldsChangedSha256", DiffCheckConfiguration.onlyCheckData());

        assertNull(sha256Diff);
    }

    @Test
    void testBase64Conversion() throws Exception {
        final IBaseDataObject initialIbdo = new BaseDataObject();
        final IBaseDataObject expectedIbdo = new BaseDataObject();
        final List<IBaseDataObject> expectedChildren = new ArrayList<>();
        final List<IBaseDataObject> actualChildren = new ArrayList<>();
        final byte[] bytes = "\001Data".getBytes(StandardCharsets.UTF_8);

        setAllFieldsNonPrintable(expectedIbdo, bytes);

        final IBaseDataObject actualIbdo = ibdoFromXmlFromIbdo(expectedIbdo, expectedChildren, initialIbdo,
                actualChildren, DEFAULT_ELEMENT_ENCODERS);
        final String diff = PlaceComparisonHelper.checkDifferences(expectedIbdo, actualIbdo, expectedChildren,
                actualChildren, "testBase64Conversion", DiffCheckConfiguration.onlyCheckData());

        assertNull(diff);

        final IBaseDataObject sha256ActualIbdo = ibdoFromXmlFromIbdo(expectedIbdo, expectedChildren, initialIbdo,
                actualChildren, SHA256_ELEMENT_ENCODERS);

        expectedIbdo.setData(ByteUtil.sha256Bytes(bytes).getBytes(StandardCharsets.UTF_8));

        for (Entry<String, byte[]> entry : new TreeMap<>(expectedIbdo.getAlternateViews()).entrySet()) {
            expectedIbdo.addAlternateView(entry.getKey(), ByteUtil.sha256Bytes(entry.getValue()).getBytes(StandardCharsets.UTF_8));
        }

        final String sha256Diff = PlaceComparisonHelper.checkDifferences(expectedIbdo, sha256ActualIbdo, expectedChildren,
                actualChildren, "testSha256Conversion", DiffCheckConfiguration.onlyCheckData());

        assertNull(sha256Diff);
    }

    @Test
    void testLengthAttributeDefault() throws Exception {
        final IBaseDataObject ibdo = new BaseDataObject();
        final List<IBaseDataObject> children = new ArrayList<>();
        final byte[] bytes = "Data".getBytes(StandardCharsets.UTF_8);

        setAllFieldsPrintable(ibdo, bytes);

        final String xmlString = IBaseDataObjectXmlHelper.xmlFromIbdo(ibdo, children, ibdo, DEFAULT_ELEMENT_ENCODERS);

        final SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        final Document document = builder.build(new StringReader(xmlString));
        final Element root = document.getRootElement();

        assertNull(checkLengthElement(root, (int) ibdo.getChannelSize(), "answers", "data"));
        assertNull(checkLengthElement(root, ibdo.footer().length, "answers", "footer"));
        assertNull(checkLengthElement(root, ibdo.header().length, "answers", "header"));
        assertNull(checkLengthKeyValue(root, ibdo.getAlternateView("AlternateView1Key").length, "AlternateView1Key", "answers", "view"));
        assertNull(checkLengthKeyValue(root, ibdo.getAlternateView("AlternateView11Key").length, "AlternateView11Key", "answers", "view"));
    }

    @Test
    void testLengthAttributeHash() throws Exception {
        final IBaseDataObject ibdo = new BaseDataObject();
        final List<IBaseDataObject> children = new ArrayList<>();
        final byte[] bytes = "Data".getBytes(StandardCharsets.UTF_8);

        setAllFieldsNonPrintable(ibdo, bytes);

        final String xmlString = IBaseDataObjectXmlHelper.xmlFromIbdo(ibdo, children, ibdo, DEFAULT_ELEMENT_ENCODERS);

        final SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        final Document document = builder.build(new StringReader(xmlString));
        final Element root = document.getRootElement();

        assertNull(checkLengthElement(root, (int) ibdo.getChannelSize(), "answers", "data"));
        assertNull(checkLengthElement(root, ibdo.footer().length, "answers", "footer"));
        assertNull(checkLengthElement(root, ibdo.header().length, "answers", "header"));
        assertNull(checkLengthKeyValue(root, ibdo.getAlternateView("\200AlternateView1Key").length, "\200AlternateView1Key", "answers", "view"));
        assertNull(checkLengthKeyValue(root, ibdo.getAlternateView("\200AlternateView11Key").length, "\200AlternateView11Key", "answers", "view"));
    }

    @Nullable
    private static String checkLengthElement(Element rootElement, int lengthToCheck, String... xmlPathElementNames) {
        Element element = rootElement;

        for (int i = 0; i < xmlPathElementNames.length; i++) {
            final String xmlPathElementName = xmlPathElementNames[i];
            final List<Element> elements = element.getChildren(xmlPathElementName);

            if (elements == null || elements.size() != 1) {
                return "Cound not find element " + xmlPathElementName;
            }

            element = elements.get(0);
        }

        String lengthElement = element.getAttributeValue(IBaseDataObjectXmlCodecs.LENGTH_ATTRIBUTE_NAME);

        if (lengthElement == null) {
            return "Could not find length element";
        }

        try {
            long length = Long.parseLong(lengthElement);

            if (length == lengthToCheck) {
                return null;
            } else {
                return "Looking for length " + lengthToCheck + ", but found " + length;
            }
        } catch (NumberFormatException e) {
            return "NumberFormatException: " + lengthElement;
        }
    }

    @Nullable
    private static String checkLengthKeyValue(Element rootElement, int lengthToCheck, String key, String... xmlPathElementNames) {
        Element element = rootElement;

        for (int i = 0; i < xmlPathElementNames.length - 1; i++) {
            final String xmlPathElementName = xmlPathElementNames[i];
            final List<Element> elements = element.getChildren(xmlPathElementName);

            if (elements == null || elements.isEmpty()) {
                return "Cound not find element " + xmlPathElementName;
            }

            element = elements.get(0);
        }

        List<Element> keyValueElements = element.getChildren(xmlPathElementNames[xmlPathElementNames.length - 1]);

        for (int j = 0; j < keyValueElements.size(); j++) {
            final Element e = keyValueElements.get(j);
            final Element nameElement = e.getChild(IbdoXmlElementNames.NAME);
            final String name = nameElement.getValue();
            final String nameEncoding = nameElement.getAttributeValue(IBaseDataObjectXmlCodecs.ENCODING_ATTRIBUTE_NAME);
            final String nameDecoded = new String(extractBytes(nameEncoding, name), StandardCharsets.UTF_8);

            if (nameDecoded.equals(key)) {
                element = e.getChild(IbdoXmlElementNames.VALUE);
            }
        }

        String lengthElement = element.getAttributeValue(IBaseDataObjectXmlCodecs.LENGTH_ATTRIBUTE_NAME);

        if (lengthElement == null) {
            return "Could not find length element";
        }

        try {
            long length = Long.parseLong(lengthElement);

            if (length == lengthToCheck) {
                return null;
            } else {
                return "Looking for length " + lengthToCheck + ", but found " + length;
            }
        } catch (NumberFormatException e) {
            return "NumberFormatException: " + lengthElement;
        }
    }

    @Test
    void testAttachmentsAndExtractedRecords() throws Exception {
        final IBaseDataObject initialIbdo = new BaseDataObject();
        final IBaseDataObject expectedIbdo = new BaseDataObject();
        final IBaseDataObject childIbdo1 = new BaseDataObject();
        final IBaseDataObject childIbdo2 = new BaseDataObject();
        final IBaseDataObject extractedIbdo1 = new BaseDataObject();
        final IBaseDataObject extractedIbdo2 = new BaseDataObject();
        final List<IBaseDataObject> expectedChildren = new ArrayList<>();
        final List<IBaseDataObject> actualChildren = new ArrayList<>();

        childIbdo1.setFilename("Child1");
        childIbdo2.setFilename("Child2");
        extractedIbdo1.setFilename("ExtractedRecord1");
        extractedIbdo2.setFilename("ExtractedRecord2");

        expectedIbdo.setFilename("Parent");
        expectedIbdo.addExtractedRecord(extractedIbdo1);
        expectedIbdo.addExtractedRecord(extractedIbdo2);

        expectedChildren.add(childIbdo1);
        expectedChildren.add(childIbdo2);

        final IBaseDataObject actualIbdo = ibdoFromXmlFromIbdo(expectedIbdo, expectedChildren, initialIbdo,
                actualChildren, DEFAULT_ELEMENT_ENCODERS);
        final String diff = PlaceComparisonHelper.checkDifferences(expectedIbdo, actualIbdo, expectedChildren,
                actualChildren, "testAttachmentsAndExtractedRecords", DiffCheckConfiguration.onlyCheckData());

        assertNull(diff);
    }

    @Test
    void testBadChannelFactory() throws Exception {
        final IBaseDataObject initialIbdo = new BaseDataObject();
        final IBaseDataObject expectedIbdo = new BaseDataObject();
        final IBaseDataObject dataExceptionIbdo = new BaseDataObject();
        final List<IBaseDataObject> expectedChildren = new ArrayList<>();
        final List<IBaseDataObject> actualChildren = new ArrayList<>();

        dataExceptionIbdo.setChannelFactory(new ExceptionChannelFactory());

        final IBaseDataObject actualIbdo = ibdoFromXmlFromIbdo(dataExceptionIbdo, expectedChildren, initialIbdo,
                actualChildren, DEFAULT_ELEMENT_ENCODERS);
        final String diff = PlaceComparisonHelper.checkDifferences(expectedIbdo, actualIbdo, expectedChildren,
                actualChildren, "testBadChannelFactory", DiffCheckConfiguration.onlyCheckData());

        assertNull(diff);

        final IBaseDataObject sha256ActualIbdo = ibdoFromXmlFromIbdo(dataExceptionIbdo, expectedChildren, initialIbdo,
                actualChildren, SHA256_ELEMENT_ENCODERS);
        final String sha256Diff = PlaceComparisonHelper.checkDifferences(expectedIbdo, sha256ActualIbdo, expectedChildren,
                actualChildren, "testBadChannelFactory", DiffCheckConfiguration.onlyCheckData());

        assertNull(sha256Diff);
    }

    @Test
    void testBadIntegerDecoding() throws Exception {
        final IBaseDataObject initialIbdo = new BaseDataObject();
        final IBaseDataObject expectedIbdo = new BaseDataObject();
        final IBaseDataObject priorityIbdo = new BaseDataObject();
        final List<IBaseDataObject> expectedChildren = new ArrayList<>();
        final List<IBaseDataObject> actualChildren = new ArrayList<>();

        priorityIbdo.setPriority(100);

        final String xmlString = IBaseDataObjectXmlHelper.xmlFromIbdo(priorityIbdo, expectedChildren, initialIbdo, DEFAULT_ELEMENT_ENCODERS);
        final String newXmlString = xmlString.replace("100", "100A");

        final SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        final Document document = builder.build(new StringReader(newXmlString));
        final IBaseDataObject actualIbdo = IBaseDataObjectXmlHelper.ibdoFromXml(document, actualChildren, DEFAULT_ELEMENT_DECODERS);

        final String diff = PlaceComparisonHelper.checkDifferences(expectedIbdo, actualIbdo, expectedChildren,
                actualChildren, "testBadChannelFactory", DiffCheckConfiguration.onlyCheckData());

        assertNull(diff);
    }

    @Test
    void testCreateStandardInitialIbdo() {
        final byte[] bytes = new byte[20];

        new Random().nextBytes(bytes);

        final SeekableByteChannelFactory sbcf = InMemoryChannelFactory.create(bytes);
        final String classification = "Classification";
        final String formAndFileType = "FormAndFiletype";
        final KffDataObjectHandler kff = new KffDataObjectHandler(KffDataObjectHandler.TRUNCATE_KNOWN_DATA,
                KffDataObjectHandler.SET_FORM_WHEN_KNOWN, KffDataObjectHandler.SET_FILE_TYPE);
        final List<String> differences = new ArrayList<>();
        final IBaseDataObject expectedIbdo = new BaseDataObject();
        final IBaseDataObject tempIbdo = new BaseDataObject();

        tempIbdo.setChannelFactory(sbcf);
        kff.hash(tempIbdo);
        expectedIbdo.setParameters(tempIbdo.getParameters());

        expectedIbdo.setCurrentForm(formAndFileType);
        expectedIbdo.setFileType(formAndFileType);
        expectedIbdo.setClassification(classification);

        final IBaseDataObject actualIbdo = IBaseDataObjectXmlHelper.createStandardInitialIbdo(new BaseDataObject(), sbcf, classification,
                formAndFileType, kff);

        IBaseDataObjectDiffHelper.diff(expectedIbdo, actualIbdo, differences, DiffCheckConfiguration.onlyCheckData());

        assertEquals(0, differences.size());
    }

    private static IBaseDataObject ibdoFromXmlFromIbdo(final IBaseDataObject ibdo, final List<IBaseDataObject> children,
            final IBaseDataObject initialIbdo, final List<IBaseDataObject> outputChildren, final ElementEncoders elementEncoders) throws Exception {
        final String xmlString = IBaseDataObjectXmlHelper.xmlFromIbdo(ibdo, children, initialIbdo, elementEncoders);

        final SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        final Document document = builder.build(new StringReader(xmlString));

        return IBaseDataObjectXmlHelper.ibdoFromXml(document, outputChildren, DEFAULT_ELEMENT_DECODERS);
    }

    private static class ExceptionChannelFactory implements SeekableByteChannelFactory {
        @Override
        public SeekableByteChannel create() {
            return new SeekableByteChannel() {
                @Override
                public boolean isOpen() {
                    return true;
                }

                @Override
                public void close() throws IOException {
                    throw new IOException("This SBC only throws Exceptions");
                }

                @Override
                public int read(final ByteBuffer dst) throws IOException {
                    throw new IOException("This SBC only throws Exceptions");
                }

                @Override
                public int write(final ByteBuffer src) throws IOException {
                    throw new IOException("This SBC only throws Exceptions");
                }

                @Override
                public long position() throws IOException {
                    throw new IOException("This SBC only throws Exceptions");
                }

                @Override
                public SeekableByteChannel position(final long newPosition) throws IOException {
                    throw new IOException("This SBC only throws Exceptions");
                }

                @Override
                public long size() throws IOException {
                    throw new IOException("This SBC only throws Exceptions");
                }

                @Override
                public SeekableByteChannel truncate(final long size) throws IOException {
                    throw new IOException("This SBC only throws Exceptions");
                }
            };
        }
    }
}
