package emissary.core;

import emissary.core.IBaseDataObjectXmlCodecs.ElementEncoders;
import emissary.core.channels.InMemoryChannelFactory;
import emissary.core.channels.SeekableByteChannelFactory;
import emissary.kff.KffDataObjectHandler;
import emissary.test.core.junit5.UnitTest;
import emissary.util.ByteUtil;
import emissary.util.PlaceComparisonHelper;

import org.jdom2.Document;
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

import static emissary.core.IBaseDataObjectXmlCodecs.DEFAULT_ELEMENT_DECODERS;
import static emissary.core.IBaseDataObjectXmlCodecs.DEFAULT_ELEMENT_ENCODERS;
import static emissary.core.IBaseDataObjectXmlCodecs.SHA256_ELEMENT_ENCODERS;
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

    @Test
    void testParentIbdoAllFieldsChanged() throws Exception {
        final IBaseDataObject initialIbdo = new BaseDataObject();
        final IBaseDataObject expectedIbdo = new BaseDataObject();
        final List<IBaseDataObject> expectedChildren = new ArrayList<>();
        final List<IBaseDataObject> actualChildren = new ArrayList<>();

        expectedIbdo.setData("Data".getBytes(StandardCharsets.ISO_8859_1));
        expectedIbdo.setBirthOrder(5);
        expectedIbdo.setBroken("Broken1");
        expectedIbdo.setBroken("Broken2");
        expectedIbdo.setClassification("Classification");
        expectedIbdo.pushCurrentForm("Form1");
        expectedIbdo.pushCurrentForm("Form2");
        expectedIbdo.setFilename("Filename");
        expectedIbdo.setFileType("FileType");
        expectedIbdo.setFontEncoding("FontEncoding");
        expectedIbdo.setFooter("Footer".getBytes(StandardCharsets.ISO_8859_1));
        expectedIbdo.setHeader("Header".getBytes(StandardCharsets.ISO_8859_1));
        expectedIbdo.setHeaderEncoding("HeaderEncoding");
        expectedIbdo.setId("Id");
        expectedIbdo.setNumChildren(9);
        expectedIbdo.setNumSiblings(10);
        expectedIbdo.setOutputable(false);
        expectedIbdo.setPriority(1);
        expectedIbdo.addProcessingError("ProcessingError1");
        expectedIbdo.addProcessingError("ProcessingError2");
        expectedIbdo.setTransactionId("TransactionId");
        expectedIbdo.setWorkBundleId("WorkBundleId");
        expectedIbdo.putParameter("Parameter1Key", "Parameter1Value");
        expectedIbdo.putParameter("Parameter2Key", Arrays.asList("Parameter2Value1", "Parameter2Value2"));
        expectedIbdo.putParameter("Parameter3Key", Arrays.asList(10L, 20L));
        expectedIbdo.addAlternateView("AlternateView1Key", "AlternateView1Value".getBytes(StandardCharsets.ISO_8859_1));
        expectedIbdo.addAlternateView("AlternateView2Key", "AlternateView2Value".getBytes(StandardCharsets.ISO_8859_1));

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
        final byte[] bytes = "\001Data".getBytes(StandardCharsets.ISO_8859_1);

        expectedIbdo.setData(bytes);
        expectedIbdo.setBirthOrder(5);
        expectedIbdo.setBroken("\001Broken1");
        expectedIbdo.setBroken("\001Broken2");
        expectedIbdo.setClassification("\001Classification");
        expectedIbdo.pushCurrentForm("Form1");
        expectedIbdo.pushCurrentForm("Form2");
        expectedIbdo.setFilename("\001Filename");
        expectedIbdo.setFileType("\001FileType");
        expectedIbdo.setFontEncoding("\001FontEncoding");
        expectedIbdo.setFooter("\001Footer".getBytes(StandardCharsets.ISO_8859_1));
        expectedIbdo.setHeader("\001Header".getBytes(StandardCharsets.ISO_8859_1));
        expectedIbdo.setHeaderEncoding("\001HeaderEncoding");
        expectedIbdo.setId("\001Id");
        expectedIbdo.setNumChildren(9);
        expectedIbdo.setNumSiblings(10);
        expectedIbdo.setOutputable(false);
        expectedIbdo.setPriority(1);
        expectedIbdo.addProcessingError("\001ProcessingError1");
        expectedIbdo.addProcessingError("\001ProcessingError2");
        expectedIbdo.setTransactionId("\001TransactionId");
        expectedIbdo.setWorkBundleId("\001WorkBundleId");
        expectedIbdo.putParameter("\020Parameter1Key", "\020Parameter1Value");
        expectedIbdo.putParameter("\020Parameter2Key", "\020Parameter2Value");
        expectedIbdo.addAlternateView("\200AlternateView1Key",
                "\200AlternateView1Value".getBytes(StandardCharsets.ISO_8859_1));
        expectedIbdo.addAlternateView("\200AlternateView2Key",
                "\200AlternateView2Value".getBytes(StandardCharsets.ISO_8859_1));

        final IBaseDataObject actualIbdo = ibdoFromXmlFromIbdo(expectedIbdo, expectedChildren, initialIbdo,
                actualChildren, DEFAULT_ELEMENT_ENCODERS);
        final String diff = PlaceComparisonHelper.checkDifferences(expectedIbdo, actualIbdo, expectedChildren,
                actualChildren, "testBase64Conversion", DiffCheckConfiguration.onlyCheckData());

        assertNull(diff);

        final IBaseDataObject sha256ActualIbdo = ibdoFromXmlFromIbdo(expectedIbdo, expectedChildren, initialIbdo,
                actualChildren, SHA256_ELEMENT_ENCODERS);

        expectedIbdo.setData(ByteUtil.sha256Bytes(bytes).getBytes(StandardCharsets.ISO_8859_1));

        for (Entry<String, byte[]> entry : new TreeMap<>(expectedIbdo.getAlternateViews()).entrySet()) {
            expectedIbdo.addAlternateView(entry.getKey(), ByteUtil.sha256Bytes(entry.getValue()).getBytes(StandardCharsets.ISO_8859_1));
        }

        final String sha256Diff = PlaceComparisonHelper.checkDifferences(expectedIbdo, sha256ActualIbdo, expectedChildren,
                actualChildren, "testSha256Conversion", DiffCheckConfiguration.onlyCheckData());

        assertNull(sha256Diff);
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

        final IBaseDataObject actualIbdo = IBaseDataObjectXmlHelper.createStandardInitialIbdo(sbcf, classification,
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
