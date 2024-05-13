package emissary.core;

import emissary.core.channels.AbstractSeekableByteChannel;
import emissary.core.channels.InMemoryChannelFactory;
import emissary.core.channels.SeekableByteChannelFactory;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.annotation.Nullable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IBaseDataObjectDiffHelperTest extends UnitTest {

    private IBaseDataObject ibdo1;
    private IBaseDataObject ibdo2;
    private List<IBaseDataObject> ibdoList1;
    private List<IBaseDataObject> ibdoList2;
    private List<String> differences;
    private static final DiffCheckConfiguration EMPTY_OPTIONS = DiffCheckConfiguration.configure().build();
    private static final DiffCheckConfiguration CHECK_DATA = DiffCheckConfiguration.onlyCheckData();

    @BeforeEach
    void setup() {
        ibdo1 = new BaseDataObject();
        ibdo2 = new BaseDataObject();
        ibdoList1 = Arrays.asList(ibdo1);
        ibdoList2 = Arrays.asList(ibdo2);
        differences = new ArrayList<>();
    }

    private void verifyDiff(final int expectedDifferences) {
        verifyDiff(expectedDifferences, EMPTY_OPTIONS);
    }

    private void verifyDiff(final int expectedDifferences, final DiffCheckConfiguration options) {
        IBaseDataObjectDiffHelper.diff(ibdo1, ibdo1, differences, options);
        assertEquals(0, differences.size());
        IBaseDataObjectDiffHelper.diff(ibdo1, ibdo2, differences, options);
        assertEquals(expectedDifferences, differences.size());
        differences.clear();
        IBaseDataObjectDiffHelper.diff(ibdo2, ibdo1, differences, options);
        assertEquals(expectedDifferences, differences.size());
        differences.clear();
    }

    private void verifyDiffList(final int expectedDifferences, @Nullable final List<IBaseDataObject> list1,
            @Nullable final List<IBaseDataObject> list2) {
        IBaseDataObjectDiffHelper.diff(list1, list1, "test", differences, EMPTY_OPTIONS);
        assertEquals(0, differences.size());
        IBaseDataObjectDiffHelper.diff(list1, list2, "test", differences, EMPTY_OPTIONS);
        assertEquals(expectedDifferences, differences.size());
        differences.clear();
    }

    private static void checkThrowsNull(final Executable e) {
        assertThrows(NullPointerException.class, e);
    }

    @Test
    void testDiffArguments() {
        // Objects
        checkThrowsNull(() -> IBaseDataObjectDiffHelper.diff(null, ibdo2, differences, EMPTY_OPTIONS));
        checkThrowsNull(() -> IBaseDataObjectDiffHelper.diff(ibdo1, null, differences, EMPTY_OPTIONS));
        checkThrowsNull(() -> IBaseDataObjectDiffHelper.diff(ibdo1, ibdo2, null, EMPTY_OPTIONS));
        checkThrowsNull(() -> IBaseDataObjectDiffHelper.diff(ibdoList1, ibdoList2, null, differences, EMPTY_OPTIONS));
        checkThrowsNull(() -> IBaseDataObjectDiffHelper.diff(ibdoList1, ibdoList2, "id", null, EMPTY_OPTIONS));
        checkThrowsNull(() -> IBaseDataObjectDiffHelper.diff(ibdo1, ibdo2, differences, null));
        checkThrowsNull(() -> IBaseDataObjectDiffHelper.diff(new Object(), new Object(), null, differences));
        checkThrowsNull(() -> IBaseDataObjectDiffHelper.diff(new Object(), new Object(), "id", null));

        // Integers
        checkThrowsNull(() -> IBaseDataObjectDiffHelper.diff(0, 0, null, differences));
        checkThrowsNull(() -> IBaseDataObjectDiffHelper.diff(0, 0, "id", null));

        // Booleans
        checkThrowsNull(() -> IBaseDataObjectDiffHelper.diff(false, false, null, differences));
        checkThrowsNull(() -> IBaseDataObjectDiffHelper.diff(false, false, "id", null));

        // Maps
        checkThrowsNull(() -> IBaseDataObjectDiffHelper.diff(null, new HashMap<>(), "id", differences));
        checkThrowsNull(() -> IBaseDataObjectDiffHelper.diff(new HashMap<>(), null, "id", differences));
        checkThrowsNull(() -> IBaseDataObjectDiffHelper.diff(new HashMap<>(), new HashMap<>(), null, differences));
        checkThrowsNull(() -> IBaseDataObjectDiffHelper.diff(new HashMap<>(), new HashMap<>(), "id", null));
    }

    @Test
    void testDiffChannelFactory() {
        ibdo2.setData(new byte[1]);
        verifyDiff(1, CHECK_DATA);

        ibdo1.setChannelFactory(InMemoryChannelFactory.create("0123456789".getBytes(StandardCharsets.US_ASCII)));
        ibdo2.setChannelFactory(InMemoryChannelFactory.create("9876543210".getBytes(StandardCharsets.US_ASCII)));
        verifyDiff(1, CHECK_DATA);

        ibdo2.setChannelFactory(new SeekableByteChannelFactory() {
            @Override
            public SeekableByteChannel create() {
                return new AbstractSeekableByteChannel() {
                    @Override
                    protected void closeImpl() throws IOException {
                        throw new IOException("Test SBC that always throws IOException!");
                    }

                    @Override
                    protected int readImpl(final ByteBuffer byteBuffer) throws IOException {
                        throw new IOException("Test SBC that always throws IOException!");
                    }

                    @Override
                    protected long sizeImpl() throws IOException {
                        throw new IOException("Test SBC that always throws IOException!");
                    }
                };
            }
        });

        verifyDiff(1, CHECK_DATA);
    }

    @Test
    void testDiffCurrentForm() {
        ibdo1.pushCurrentForm("AAA");
        ibdo1.pushCurrentForm("BBB");
        ibdo1.pushCurrentForm("CCC");
        verifyDiff(1);
    }

    @Test
    void testDiffHistory() {
        ibdo1.appendTransformHistory("AAA", false);
        ibdo1.appendTransformHistory("BBB", true);
        final DiffCheckConfiguration checkTransformHistory = DiffCheckConfiguration.configure().enableTransformHistory().build();
        verifyDiff(1, checkTransformHistory);
    }

    @Test
    void testDiffParameters() {
        final DiffCheckConfiguration checkKeyValueDiffParameter = DiffCheckConfiguration.configure().enableKeyValueParameterDiff().build();
        final DiffCheckConfiguration checkDetailedDiffParameter = DiffCheckConfiguration.configure().enableDetailedParameterDiff().build();

        ibdo1.putParameter("STRING", "string");
        ibdo1.putParameter("LIST", Arrays.asList("first", "second", "third"));

        verifyDiff(1);
        verifyDiff(1, checkKeyValueDiffParameter);
        verifyDiff(1, checkDetailedDiffParameter);

        ibdo1.clearParameters();
        ibdo2.clearParameters();
        ibdo1.putParameter("STRING", "string");
        ibdo1.putParameter("Integer", Integer.valueOf(1));
        ibdo2.putParameter("STRING", "string");

        verifyDiff(1);
        verifyDiff(1, checkKeyValueDiffParameter);
        verifyDiff(1, checkDetailedDiffParameter);

        ibdo1.clearParameters();
        ibdo2.clearParameters();
        ibdo1.putParameter("STRING", "string");
        ibdo2.putParameter("STRING", "string");
        ibdo2.putParameter("Integer", Integer.valueOf(1));

        verifyDiff(1);
        verifyDiff(1, checkKeyValueDiffParameter);
        verifyDiff(1, checkDetailedDiffParameter);

        ibdo1.clearParameters();
        ibdo2.clearParameters();
        ibdo1.putParameter("STRING", "string");
        ibdo2.putParameter("STRING", "string");

        verifyDiff(0);
        verifyDiff(0, checkKeyValueDiffParameter);
        verifyDiff(0, checkDetailedDiffParameter);
    }

    @Test
    void testDiffAlternateViews() {
        ibdo1.addAlternateView("AAA", "AAA".getBytes(StandardCharsets.US_ASCII));
        ibdo1.addAlternateView("BBB", "BBB".getBytes(StandardCharsets.US_ASCII));
        ibdo1.addAlternateView("CCC", "CCC".getBytes(StandardCharsets.US_ASCII));
        verifyDiff(1);

        ibdo2.addAlternateView("DDD", "DDD".getBytes(StandardCharsets.US_ASCII));
        ibdo2.addAlternateView("EEE", "EEE".getBytes(StandardCharsets.US_ASCII));
        ibdo2.addAlternateView("FFF", "FFF".getBytes(StandardCharsets.US_ASCII));
        verifyDiff(1);
    }

    @Test
    void testDiffPriority() {
        ibdo1.setPriority(13);
        verifyDiff(1);
    }

    @Test
    void testDiffCreationTimestamp() {
        ibdo1.setCreationTimestamp(Instant.ofEpochSecond(1234567890));
        final DiffCheckConfiguration options = DiffCheckConfiguration.configure().enableTimestamp().build();
        verifyDiff(1, options);
    }

    @Test
    void testDiffExtractedRecords() {
        ibdo1.addExtractedRecord(new BaseDataObject());
        ibdo1.addExtractedRecord(new BaseDataObject());
        ibdo1.addExtractedRecord(new BaseDataObject());
        verifyDiff(1);
    }

    @Test
    void testDiffFilename() {
        ibdo1.setFilename("filename");
        verifyDiff(2);
    }

    @Test
    void testDiffInternalId() {
        final DiffCheckConfiguration options = DiffCheckConfiguration.configure().enableInternalId().build();
        verifyDiff(1, options);
    }

    @Test
    void testDiffProcessingError() {
        ibdo1.addProcessingError("processing_error");
        verifyDiff(1);
    }

    @Test
    void testDiffFontEncoding() {
        ibdo1.setFontEncoding("font_encoding");
        verifyDiff(1);
    }

    @Test
    void testDiffNumChildren() {
        ibdo1.setNumChildren(13);
        verifyDiff(1);
    }

    @Test
    void testDiffNumSiblings() {
        ibdo1.setNumSiblings(13);
        verifyDiff(1);
    }

    @Test
    void testDiffBirthOrder() {
        ibdo1.setBirthOrder(13);
        verifyDiff(1);
    }

    @Test
    void testDiffHeader() {
        ibdo1.setHeader("header".getBytes(StandardCharsets.US_ASCII));
        verifyDiff(1);
    }

    @Test
    void testDiffFooter() {
        ibdo1.setFooter("footer".getBytes(StandardCharsets.US_ASCII));
        verifyDiff(1);
    }

    @Test
    void testDiffHeaderEncoding() {
        ibdo1.setHeaderEncoding("header_encoding");
        verifyDiff(1);
    }

    @Test
    void testDiffClassification() {
        ibdo1.setClassification("classification");
        verifyDiff(1);
    }

    @Test
    void testDiffBroken() {
        ibdo1.setBroken("broken");
        verifyDiff(1);
    }

    @Test
    void testDiffOutputable() {
        ibdo1.setOutputable(false);
        verifyDiff(1);
    }

    @Test
    void testDiffId() {
        ibdo1.setId("id");
        verifyDiff(1);
    }

    @Test
    void testDiffWorkBundleId() {
        ibdo1.setWorkBundleId("workbundle_id");
        verifyDiff(1);
    }

    @Test
    void testDiffTransactionId() {
        ibdo1.setTransactionId("transaction_id");
        verifyDiff(1);
    }

    @Test
    void testDiffList() {
        final List<IBaseDataObject> ibdoList3 = Arrays.asList(ibdo1, ibdo2);
        verifyDiffList(0, null, null);
        verifyDiffList(0, new ArrayList<>(), null);
        verifyDiffList(0, null, new ArrayList<>());
        verifyDiffList(0, ibdoList1, ibdoList1);
        verifyDiffList(1, ibdoList3, ibdoList2);
        verifyDiffList(1, ibdoList1, ibdoList3);

        ibdo2.setClassification("classification");
        verifyDiffList(1, ibdoList1, ibdoList2);
    }

    @Test
    void testParamSort() {
        // set-up
        Set<String> expectedParams = new TreeSet<>();
        expectedParams.add("LIST");
        expectedParams.add("STRING");
        expectedParams.add("TEST");
        ibdo1.putParameter("STRING", "string");
        ibdo1.putParameter("TEST", "test");
        ibdo1.putParameter("LIST", Arrays.asList("first", "second", "third"));

        // test
        TreeMap<String, Collection<Object>> sortedParams = new TreeMap<>(ibdo1.getParameters());
        assertEquals(expectedParams, sortedParams.keySet(), "parameters should be sorted in natural order of keys");
    }

    @Test
    void testDecoderIOExceptions() throws IOException {
        assertThrows(IOException.class, () -> IBaseDataObjectXmlCodecs.DEFAULT_BOOLEAN_DECODER.decode(null, null, "badMethodName"));
        assertThrows(IOException.class,
                () -> IBaseDataObjectXmlCodecs.DEFAULT_SEEKABLE_BYTE_CHANNEL_FACTORY_DECODER.decode(null, null, "badMethodName"));
        assertThrows(IOException.class, () -> IBaseDataObjectXmlCodecs.DEFAULT_BYTE_ARRAY_DECODER.decode(null, null, "badMethodName"));
        assertThrows(IOException.class, () -> IBaseDataObjectXmlCodecs.DEFAULT_INTEGER_DECODER.decode(null, null, "badMethodName"));
        assertThrows(IOException.class, () -> IBaseDataObjectXmlCodecs.DEFAULT_STRING_DECODER.decode(null, null, "badMethodName"));
        assertThrows(IOException.class, () -> IBaseDataObjectXmlCodecs.DEFAULT_STRING_BYTE_ARRAY_DECODER.decode(null, null, "badMethodName"));
        assertThrows(IOException.class, () -> IBaseDataObjectXmlCodecs.DEFAULT_STRING_OBJECT_DECODER.decode(null, null, "badMethodName"));
    }
}
