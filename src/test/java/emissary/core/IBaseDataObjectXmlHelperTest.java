package emissary.core;

import emissary.core.channels.InMemoryChannelFactory;
import emissary.core.channels.SeekableByteChannelFactory;
import emissary.kff.KffDataObjectHandler;
import emissary.test.core.junit5.UnitTest;
import emissary.util.PlaceComparisonHelper;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
                actualChildren);
        final String diff = PlaceComparisonHelper.checkDifferences(expectedIbdo, actualIbdo, expectedChildren,
                actualChildren, "testParentIbdoNoFieldsChanged", DiffCheckConfiguration.onlyCheckData());

        assertNull(diff);
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
        expectedIbdo.putParameter("Parameter2Key", "Parameter2Value");
        expectedIbdo.addAlternateView("AlternateView1Key", "AlternateView1Value".getBytes(StandardCharsets.ISO_8859_1));
        expectedIbdo.addAlternateView("AlternateView2Key", "AlternateView2Value".getBytes(StandardCharsets.ISO_8859_1));

        final IBaseDataObject actualIbdo = ibdoFromXmlFromIbdo(expectedIbdo, expectedChildren, initialIbdo,
                actualChildren);
        final String diff = PlaceComparisonHelper.checkDifferences(expectedIbdo, actualIbdo, expectedChildren,
                actualChildren, "testParentIbdoAllFieldsChanged", DiffCheckConfiguration.onlyCheckData());

        assertNull(diff);
    }

    @Test
    void testBase64Conversion() throws Exception {
        final IBaseDataObject initialIbdo = new BaseDataObject();
        final IBaseDataObject expectedIbdo = new BaseDataObject();
        final List<IBaseDataObject> expectedChildren = new ArrayList<>();
        final List<IBaseDataObject> actualChildren = new ArrayList<>();

        expectedIbdo.setData("\001Data".getBytes(StandardCharsets.ISO_8859_1));
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
                actualChildren);
        final String diff = PlaceComparisonHelper.checkDifferences(expectedIbdo, actualIbdo, expectedChildren,
                actualChildren, "testBase64Conversion", DiffCheckConfiguration.onlyCheckData());

        assertNull(diff);
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
                actualChildren);
        final String diff = PlaceComparisonHelper.checkDifferences(expectedIbdo, actualIbdo, expectedChildren,
                actualChildren, "testBase64Conversion", DiffCheckConfiguration.onlyCheckData());

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
                actualChildren);
        final String diff = PlaceComparisonHelper.checkDifferences(expectedIbdo, actualIbdo, expectedChildren,
                actualChildren, "testBadChannelFactory", DiffCheckConfiguration.onlyCheckData());

        assertNull(diff);
    }

    @Test
    void testBadIntegerDecoding() throws Exception {
        final IBaseDataObject initialIbdo = new BaseDataObject();
        final IBaseDataObject expectedIbdo = new BaseDataObject();
        final IBaseDataObject priorityIbdo = new BaseDataObject();
        final List<IBaseDataObject> expectedChildren = new ArrayList<>();
        final List<IBaseDataObject> actualChildren = new ArrayList<>();

        priorityIbdo.setPriority(100);

        final String xmlString = IBaseDataObjectXmlHelper.xmlFromIbdo(priorityIbdo, expectedChildren, initialIbdo);
        final String newXmlString = xmlString.replace("100", "100A");

        final SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        final Document document = builder.build(new StringReader(newXmlString));
        final IBaseDataObject actualIbdo = IBaseDataObjectXmlHelper.ibdoFromXml(document, actualChildren);

        final String diff = PlaceComparisonHelper.checkDifferences(expectedIbdo, actualIbdo, expectedChildren,
                actualChildren, "testBadChannelFactory", DiffCheckConfiguration.onlyCheckData());

        assertNull(diff);
    }

    @Test
    void testSetParameterOnIbdoException() throws Exception {
        final Class<?> keyClass = null;
        final Class<?> valueClass = int.class;
        final IBaseDataObject ibdo = new BaseDataObject();
        final String ibdoMethodName = "methodNotInIbdo";
        final Object parameter = null;
        final Element element = null;
        final List<String> differences = new ArrayList<>();
        final Method method = IBaseDataObjectXmlHelper.class.getDeclaredMethod("setParameterOnIbdo", Class.class,
                Class.class, IBaseDataObject.class, String.class, Object.class, Element.class);

        method.setAccessible(true);

        method.invoke(IBaseDataObjectXmlHelper.class, keyClass, valueClass, ibdo, ibdoMethodName, parameter, element);

        method.setAccessible(false);

        IBaseDataObjectDiffHelper.diff(new BaseDataObject(), ibdo, differences, DiffCheckConfiguration.onlyCheckData());

        assertEquals(0, differences.size());
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
            final IBaseDataObject initialIbdo, final List<IBaseDataObject> outputChildren) throws Exception {
        final String xmlString = IBaseDataObjectXmlHelper.xmlFromIbdo(ibdo, children, initialIbdo);

        final SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        final Document document = builder.build(new StringReader(xmlString));
        final IBaseDataObject ibdo2 = IBaseDataObjectXmlHelper.ibdoFromXml(document, outputChildren);

        return ibdo2;
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
                public int read(ByteBuffer dst) throws IOException {
                    throw new IOException("This SBC only throws Exceptions");
                }

                @Override
                public int write(ByteBuffer src) throws IOException {
                    throw new IOException("This SBC only throws Exceptions");
                }

                @Override
                public long position() throws IOException {
                    throw new IOException("This SBC only throws Exceptions");
                }

                @Override
                public SeekableByteChannel position(long newPosition) throws IOException {
                    throw new IOException("This SBC only throws Exceptions");
                }

                @Override
                public long size() throws IOException {
                    throw new IOException("This SBC only throws Exceptions");
                }

                @Override
                public SeekableByteChannel truncate(long size) throws IOException {
                    throw new IOException("This SBC only throws Exceptions");
                }
            };
        }
    };
}
