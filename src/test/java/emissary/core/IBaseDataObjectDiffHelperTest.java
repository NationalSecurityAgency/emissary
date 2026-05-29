package emissary.core;

import emissary.core.channels.AbstractSeekableByteChannel;
import emissary.core.channels.InMemoryChannelFactory;
import emissary.core.channels.SeekableByteChannelFactory;
import emissary.test.core.junit5.UnitTest;

import jakarta.annotation.Nullable;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
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

    private void verifyDiff(final List<String> expectedDifferencesForward, final List<String> expectedDifferencesReverse) {
        verifyDiff(expectedDifferencesForward, expectedDifferencesReverse, EMPTY_OPTIONS);
    }

    private void verifyDiff(final List<String> expectedDifferencesForward, final List<String> expectedDifferencesReverse,
            final DiffCheckConfiguration options) {
        IBaseDataObjectDiffHelper.diff(ibdo1, ibdo1, differences, options);
        assertEquals(0, differences.size());
        IBaseDataObjectDiffHelper.diff(ibdo1, ibdo2, differences, options);
        replaceVariableText(differences);
        assertIterableEquals(expectedDifferencesForward, differences);
        differences.clear();
        IBaseDataObjectDiffHelper.diff(ibdo2, ibdo1, differences, options);
        replaceVariableText(differences);
        assertIterableEquals(expectedDifferencesReverse, differences);
        differences.clear();
    }

    private static void replaceVariableText(final List<String> differences) {
        for (int i = 0; i < differences.size(); i++) {
            final String difference = differences.remove(i);
            final String differenceNohash = difference.replaceAll("@[0-9a-fA-F]{5,8}", "@xxxxxxxx");
            final String differenceNohashNouuid =
                    differenceNohash.replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
                            "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx");

            differences.add(i, differenceNohashNouuid);
        }
    }

    private void verifyDiffList(final List<String> expectedDifferences, @Nullable final List<IBaseDataObject> list1,
            @Nullable final List<IBaseDataObject> list2) {
        IBaseDataObjectDiffHelper.diff(list1, list1, "test", differences, EMPTY_OPTIONS);
        assertEquals(0, differences.size());
        IBaseDataObjectDiffHelper.diff(list1, list2, "test", differences, EMPTY_OPTIONS);
        assertIterableEquals(expectedDifferences, differences);
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
        verifyDiff(List.of(
                "data not equal. Expected SeekableByteChannelFactory=null Actual SeekableByteChannelFactory=emissary.core.channels.ImmutableChannelFactory$ImmutableChannelFactoryImpl@xxxxxxxx"),
                List.of("data not equal. Expected SeekableByteChannelFactory=emissary.core.channels.ImmutableChannelFactory$ImmutableChannelFactoryImpl@xxxxxxxx Actual SeekableByteChannelFactory=null"),
                CHECK_DATA);

        ibdo1.setChannelFactory(InMemoryChannelFactory.create("0123456789".getBytes(StandardCharsets.US_ASCII)));
        ibdo2.setChannelFactory(InMemoryChannelFactory.create("9876543210".getBytes(StandardCharsets.US_ASCII)));
        verifyDiff(List.of("data content mismatch -> Expected size: 10, Actual size: 10"),
                List.of("data content mismatch -> Expected size: 10, Actual size: 10"), CHECK_DATA);

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

        verifyDiff(List.of("IO Error comparing data: Test SBC that always throws IOException!"),
                List.of("IO Error comparing data: Test SBC that always throws IOException!"), CHECK_DATA);
    }

    @Test
    void testDiffCurrentForm() {
        ibdo1.pushCurrentForm("AAA");
        ibdo1.pushCurrentForm("BBB");
        ibdo1.pushCurrentForm("CCC");
        verifyDiff(List.of("currentForm are not equal -> Expected: CCC, Actual: "), List.of("currentForm are not equal -> Expected: , Actual: CCC"));
    }

    @Test
    void testDiffHistory() {
        ibdo1.appendTransformHistory("AAA", false);
        ibdo1.appendTransformHistory("BBB", true);
        final DiffCheckConfiguration checkTransformHistory = DiffCheckConfiguration.configure().enableTransformHistory().build();
        verifyDiff(List.of("transformHistory are not equal -> Expected: [AAA], Actual: []"),
                List.of("transformHistory are not equal -> Expected: [], Actual: [AAA]"),
                checkTransformHistory);
    }

    @Test
    void testDiffParameters() {
        final DiffCheckConfiguration checkKeyValueDiffParameter = DiffCheckConfiguration.configure().enableKeyValueParameterDiff().build();
        final DiffCheckConfiguration checkDetailedDiffParameter = DiffCheckConfiguration.configure().enableDetailedParameterDiff().build();

        ibdo1.putParameter("STRING", "string");
        ibdo1.putParameter("LIST", Arrays.asList("first", "second", "third"));

        verifyDiff(List.of("meta key set mismatch -> Expected: [LIST, STRING], Actual: []"),
                List.of("meta key set mismatch -> Expected: [], Actual: [LIST, STRING]"));
        verifyDiff(List.of("meta map differences: | Missing keys: [LIST, STRING]"),
                List.of("meta map differences: | Unexpected keys: [LIST, STRING]"),
                checkKeyValueDiffParameter);
        verifyDiff(List.of("meta are not equal -> Expected: {LIST=[first, second, third], STRING=[string]}, Actual: {}"),
                List.of("meta are not equal -> Expected: {}, Actual: {LIST=[first, second, third], STRING=[string]}"), checkDetailedDiffParameter);

        ibdo1.clearParameters();
        ibdo2.clearParameters();
        ibdo1.putParameter("STRING", "string");
        ibdo1.putParameter("Integer", Integer.valueOf(1));
        ibdo2.putParameter("STRING", "string");

        verifyDiff(List.of("meta key set mismatch -> Expected: [Integer], Actual: []"),
                List.of("meta key set mismatch -> Expected: [], Actual: [Integer]"));
        verifyDiff(List.of("meta map differences: | Missing keys: [Integer]"),
                List.of("meta map differences: | Unexpected keys: [Integer]"), checkKeyValueDiffParameter);
        verifyDiff(List.of("meta are not equal -> Expected: {Integer=[1], STRING=[string]}, Actual: {STRING=[string]}"),
                List.of("meta are not equal -> Expected: {STRING=[string]}, Actual: {Integer=[1], STRING=[string]}"), checkDetailedDiffParameter);

        ibdo1.clearParameters();
        ibdo2.clearParameters();
        ibdo1.putParameter("STRING", "string");
        ibdo2.putParameter("STRING", "string");
        ibdo2.putParameter("Integer", Integer.valueOf(1));

        verifyDiff(List.of("meta key set mismatch -> Expected: [], Actual: [Integer]"),
                List.of("meta key set mismatch -> Expected: [Integer], Actual: []"));
        verifyDiff(List.of("meta map differences: | Unexpected keys: [Integer]"),
                List.of("meta map differences: | Missing keys: [Integer]"), checkKeyValueDiffParameter);
        verifyDiff(List.of("meta are not equal -> Expected: {STRING=[string]}, Actual: {Integer=[1], STRING=[string]}"),
                List.of("meta are not equal -> Expected: {Integer=[1], STRING=[string]}, Actual: {STRING=[string]}"), checkDetailedDiffParameter);

        ibdo1.clearParameters();
        ibdo2.clearParameters();
        ibdo1.putParameter("STRING", "string");
        ibdo2.putParameter("STRING", "string");

        verifyDiff(Collections.emptyList(), Collections.emptyList());
        verifyDiff(Collections.emptyList(), Collections.emptyList(), checkKeyValueDiffParameter);
        verifyDiff(Collections.emptyList(), Collections.emptyList(), checkDetailedDiffParameter);
    }

    @Test
    void testDiffAlternateViews() {
        ibdo1.addAlternateView("AAA", "AAA".getBytes(StandardCharsets.US_ASCII));
        ibdo1.addAlternateView("BBB", "BBB".getBytes(StandardCharsets.US_ASCII));
        ibdo1.addAlternateView("CCC", "CCC".getBytes(StandardCharsets.US_ASCII));
        verifyDiff(List.of("view are not equal"), List.of("view are not equal"));

        ibdo2.addAlternateView("DDD", "DDD".getBytes(StandardCharsets.US_ASCII));
        ibdo2.addAlternateView("EEE", "EEE".getBytes(StandardCharsets.US_ASCII));
        ibdo2.addAlternateView("FFF", "FFF".getBytes(StandardCharsets.US_ASCII));
        verifyDiff(List.of("view are not equal"), List.of("view are not equal"));
    }

    @Test
    void testDiffPriority() {
        ibdo1.setPriority(13);
        verifyDiff(List.of("priority value mismatch -> Expected: 13, Actual: 10"), List.of("priority value mismatch -> Expected: 10, Actual: 13"));
    }

    @Test
    void testDiffCreationTimestamp() {
        ibdo1.setCreationTimestamp(Instant.ofEpochSecond(1234567890));
        ibdo2.setCreationTimestamp(Instant.ofEpochSecond(1234567891));
        final DiffCheckConfiguration options = DiffCheckConfiguration.configure().enableTimestamp().build();
        verifyDiff(List.of("creationTimestamp are not equal -> Expected: 2009-02-13T23:31:30Z, Actual: 2009-02-13T23:31:31Z"),
                List.of("creationTimestamp are not equal -> Expected: 2009-02-13T23:31:31Z, Actual: 2009-02-13T23:31:30Z"), options);
    }

    @Test
    void testDiffExtractedRecords() {
        ibdo1.addExtractedRecord(new BaseDataObject());
        ibdo1.addExtractedRecord(new BaseDataObject());
        ibdo1.addExtractedRecord(new BaseDataObject());
        verifyDiff(List.of("extractedRecords list size mismatch -> Expected: 3, Actual: 0"),
                List.of("extractedRecords list size mismatch -> Expected: 0, Actual: 3"));
    }

    @Test
    void testDiffFilename() {
        ibdo1.setFilename("filename");
        verifyDiff(
                List.of("filename are not equal -> Expected: filename, Actual: null", "shortName are not equal -> Expected: filename, Actual: null"),
                List.of("filename are not equal -> Expected: null, Actual: filename", "shortName are not equal -> Expected: null, Actual: filename"));
    }

    @Test
    void testDiffInternalId() {
        final DiffCheckConfiguration options = DiffCheckConfiguration.configure().enableInternalId().build();
        verifyDiff(
                List.of("internalId are not equal -> Expected: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx, Actual: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"),
                List.of("internalId are not equal -> Expected: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx, Actual: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"),
                options);
    }

    @Test
    void testDiffProcessingError() {
        ibdo1.addProcessingError("processing_error");
        verifyDiff(List.of("processingError are not equal -> Expected: processing_error\n, Actual: null"),
                List.of("processingError are not equal -> Expected: null, Actual: processing_error\n"));
    }

    @Test
    void testDiffFontEncoding() {
        ibdo1.setFontEncoding("font_encoding");
        verifyDiff(List.of("fontEncoding are not equal -> Expected: font_encoding, Actual: null"),
                List.of("fontEncoding are not equal -> Expected: null, Actual: font_encoding"));
    }

    @Test
    void testDiffNumChildren() {
        ibdo1.setNumChildren(13);
        verifyDiff(List.of("numChildren value mismatch -> Expected: 13, Actual: 0"),
                List.of("numChildren value mismatch -> Expected: 0, Actual: 13"));
    }

    @Test
    void testDiffNumSiblings() {
        ibdo1.setNumSiblings(13);
        verifyDiff(List.of("numSiblings value mismatch -> Expected: 13, Actual: 0"),
                List.of("numSiblings value mismatch -> Expected: 0, Actual: 13"));
    }

    @Test
    void testDiffBirthOrder() {
        ibdo1.setBirthOrder(13);
        verifyDiff(List.of("birthOrder value mismatch -> Expected: 13, Actual: 0"), List.of("birthOrder value mismatch -> Expected: 0, Actual: 13"));
    }

    @Test
    void testDiffHeader() {
        ibdo1.setHeader("header".getBytes(StandardCharsets.US_ASCII));
        verifyDiff(List.of("header are not equal -> Expected: [B@xxxxxxxx, Actual: null"),
                List.of("header are not equal -> Expected: null, Actual: [B@xxxxxxxx"));
    }

    @Test
    void testDiffFooter() {
        ibdo1.setFooter("footer".getBytes(StandardCharsets.US_ASCII));
        verifyDiff(List.of("footer are not equal -> Expected: [B@xxxxxxxx, Actual: null"),
                List.of("footer are not equal -> Expected: null, Actual: [B@xxxxxxxx"));
    }

    @Test
    void testDiffHeaderEncoding() {
        ibdo1.setHeaderEncoding("header_encoding");
        verifyDiff(List.of("headerEncoding are not equal -> Expected: header_encoding, Actual: null"),
                List.of("headerEncoding are not equal -> Expected: null, Actual: header_encoding"));
    }

    @Test
    void testDiffClassification() {
        ibdo1.setClassification("classification");
        verifyDiff(List.of("classification are not equal -> Expected: classification, Actual: null"),
                List.of("classification are not equal -> Expected: null, Actual: classification"));
    }

    @Test
    void testDiffBroken() {
        ibdo1.setBroken("broken");
        verifyDiff(List.of("broken boolean mismatch -> Expected: true, Actual: false"),
                List.of("broken boolean mismatch -> Expected: false, Actual: true"));
    }

    @Test
    void testDiffOutputable() {
        ibdo1.setOutputable(false);
        verifyDiff(List.of("outputable boolean mismatch -> Expected: false, Actual: true"),
                List.of("outputable boolean mismatch -> Expected: true, Actual: false"));
    }

    @Test
    void testDiffId() {
        ibdo1.setId("id");
        verifyDiff(List.of("id are not equal -> Expected: id, Actual: null"), List.of("id are not equal -> Expected: null, Actual: id"));
    }

    @Test
    void testDiffWorkBundleId() {
        ibdo1.setWorkBundleId("workbundle_id");
        verifyDiff(List.of("workBundleId are not equal -> Expected: workbundle_id, Actual: null"),
                List.of("workBundleId are not equal -> Expected: null, Actual: workbundle_id"));
    }

    @Test
    void testDiffTransactionId() {
        ibdo1.setTransactionId("transaction_id");
        verifyDiff(List.of("transactionId are not equal -> Expected: transaction_id, Actual: null"),
                List.of("transactionId are not equal -> Expected: null, Actual: transaction_id"));
    }

    @Test
    void testDiffList() {
        final List<IBaseDataObject> ibdoList3 = Arrays.asList(ibdo1, ibdo2);
        verifyDiffList(Collections.emptyList(), null, null);
        verifyDiffList(Collections.emptyList(), new ArrayList<>(), null);
        verifyDiffList(Collections.emptyList(), null, new ArrayList<>());
        verifyDiffList(Collections.emptyList(), ibdoList1, ibdoList1);
        verifyDiffList(List.of("test list size mismatch -> Expected: 2, Actual: 1"), ibdoList3, ibdoList2);
        verifyDiffList(List.of("test list size mismatch -> Expected: 1, Actual: 2"), ibdoList1, ibdoList3);

        ibdo2.setClassification("classification");
        verifyDiffList(List.of("test[index 0] : classification are not equal -> Expected: null, Actual: classification"), ibdoList1, ibdoList2);
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
}
