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
        verifyDiff(List.of("data not equal. sbcf1=null sbcf2=emissary.core.channels.ImmutableChannelFactory$ImmutableChannelFactoryImpl@xxxxxxxx"),
                List.of("data not equal. sbcf1=emissary.core.channels.ImmutableChannelFactory$ImmutableChannelFactoryImpl@xxxxxxxx sbcf2=null"),
                CHECK_DATA);

        ibdo1.setChannelFactory(InMemoryChannelFactory.create("0123456789".getBytes(StandardCharsets.US_ASCII)));
        ibdo2.setChannelFactory(InMemoryChannelFactory.create("9876543210".getBytes(StandardCharsets.US_ASCII)));
        verifyDiff(List.of("data not equal. 1.cs=10 2.cs=10"), List.of("data not equal. 1.cs=10 2.cs=10"), CHECK_DATA);

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

        verifyDiff(List.of("Failed to compare data: Test SBC that always throws IOException!"),
                List.of("Failed to compare data: Test SBC that always throws IOException!"), CHECK_DATA);
    }

    @Test
    void testDiffCurrentForm() {
        ibdo1.pushCurrentForm("AAA");
        ibdo1.pushCurrentForm("BBB");
        ibdo1.pushCurrentForm("CCC");
        verifyDiff(List.of("currentForm are not equal: CCC : "), List.of("currentForm are not equal:  : CCC"));
    }

    @Test
    void testDiffHistory() {
        ibdo1.appendTransformHistory("AAA", false);
        ibdo1.appendTransformHistory("BBB", true);
        final DiffCheckConfiguration checkTransformHistory = DiffCheckConfiguration.configure().enableTransformHistory().build();
        verifyDiff(List.of("transformHistory are not equal: [AAA] : []"), List.of("transformHistory are not equal: [] : [AAA]"),
                checkTransformHistory);
    }

    @Test
    void testDiffParameters() {
        final DiffCheckConfiguration checkKeyValueDiffParameter = DiffCheckConfiguration.configure().enableKeyValueParameterDiff().build();
        final DiffCheckConfiguration checkDetailedDiffParameter = DiffCheckConfiguration.configure().enableDetailedParameterDiff().build();

        ibdo1.putParameter("STRING", "string");
        ibdo1.putParameter("LIST", Arrays.asList("first", "second", "third"));

        verifyDiff(List.of("meta are not equal-Differing Keys: [LIST, STRING] : []"),
                List.of("meta are not equal-Differing Keys: [] : [LIST, STRING]"));
        verifyDiff(List.of("meta are not equal-Differing Keys/Values: {LIST=[first, second, third], STRING=[string]} : {}"),
                List.of("meta are not equal-Differing Keys/Values: {} : {LIST=[first, second, third], STRING=[string]}"),
                checkKeyValueDiffParameter);
        verifyDiff(List.of("meta are not equal: {LIST=[first, second, third], STRING=[string]} : {}"),
                List.of("meta are not equal: {} : {LIST=[first, second, third], STRING=[string]}"), checkDetailedDiffParameter);

        ibdo1.clearParameters();
        ibdo2.clearParameters();
        ibdo1.putParameter("STRING", "string");
        ibdo1.putParameter("Integer", Integer.valueOf(1));
        ibdo2.putParameter("STRING", "string");

        verifyDiff(List.of("meta are not equal-Differing Keys: [Integer] : []"), List.of("meta are not equal-Differing Keys: [] : [Integer]"));
        verifyDiff(List.of("meta are not equal-Differing Keys/Values: {Integer=[1]} : {}"),
                List.of("meta are not equal-Differing Keys/Values: {} : {Integer=[1]}"), checkKeyValueDiffParameter);
        verifyDiff(List.of("meta are not equal: {Integer=[1], STRING=[string]} : {STRING=[string]}"),
                List.of("meta are not equal: {STRING=[string]} : {Integer=[1], STRING=[string]}"), checkDetailedDiffParameter);

        ibdo1.clearParameters();
        ibdo2.clearParameters();
        ibdo1.putParameter("STRING", "string");
        ibdo2.putParameter("STRING", "string");
        ibdo2.putParameter("Integer", Integer.valueOf(1));

        verifyDiff(List.of("meta are not equal-Differing Keys: [] : [Integer]"), List.of("meta are not equal-Differing Keys: [Integer] : []"));
        verifyDiff(List.of("meta are not equal-Differing Keys/Values: {} : {Integer=[1]}"),
                List.of("meta are not equal-Differing Keys/Values: {Integer=[1]} : {}"), checkKeyValueDiffParameter);
        verifyDiff(List.of("meta are not equal: {STRING=[string]} : {Integer=[1], STRING=[string]}"),
                List.of("meta are not equal: {Integer=[1], STRING=[string]} : {STRING=[string]}"), checkDetailedDiffParameter);

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
        verifyDiff(List.of("priority are not equal"), List.of("priority are not equal"));
    }

    @Test
    void testDiffCreationTimestamp() {
        ibdo1.setCreationTimestamp(Instant.ofEpochSecond(1234567890));
        ibdo2.setCreationTimestamp(Instant.ofEpochSecond(1234567891));
        final DiffCheckConfiguration options = DiffCheckConfiguration.configure().enableTimestamp().build();
        verifyDiff(List.of("creationTimestamp are not equal: 2009-02-13T23:31:30Z : 2009-02-13T23:31:31Z"),
                List.of("creationTimestamp are not equal: 2009-02-13T23:31:31Z : 2009-02-13T23:31:30Z"), options);
    }

    @Test
    void testDiffExtractedRecords() {
        ibdo1.addExtractedRecord(new BaseDataObject());
        ibdo1.addExtractedRecord(new BaseDataObject());
        ibdo1.addExtractedRecord(new BaseDataObject());
        verifyDiff(List.of("extractedRecords are not equal: 1.s=3 2.s=0"), List.of("extractedRecords are not equal: 1.s=0 2.s=3"));
    }

    @Test
    void testDiffFilename() {
        ibdo1.setFilename("filename");
        verifyDiff(List.of("filename are not equal: filename : null", "shortName are not equal: filename : null"),
                List.of("filename are not equal: null : filename", "shortName are not equal: null : filename"));
    }

    @Test
    void testDiffInternalId() {
        final DiffCheckConfiguration options = DiffCheckConfiguration.configure().enableInternalId().build();
        verifyDiff(List.of("internalId are not equal: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx : xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"),
                List.of("internalId are not equal: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx : xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"),
                options);
    }

    @Test
    void testDiffProcessingError() {
        ibdo1.addProcessingError("processing_error");
        verifyDiff(List.of("processingError are not equal: processing_error\n : null"),
                List.of("processingError are not equal: null : processing_error\n"));
    }

    @Test
    void testDiffFontEncoding() {
        ibdo1.setFontEncoding("font_encoding");
        verifyDiff(List.of("fontEncoding are not equal: font_encoding : null"), List.of("fontEncoding are not equal: null : font_encoding"));
    }

    @Test
    void testDiffNumChildren() {
        ibdo1.setNumChildren(13);
        verifyDiff(List.of("numChildren are not equal"), List.of("numChildren are not equal"));
    }

    @Test
    void testDiffNumSiblings() {
        ibdo1.setNumSiblings(13);
        verifyDiff(List.of("numSiblings are not equal"), List.of("numSiblings are not equal"));
    }

    @Test
    void testDiffBirthOrder() {
        ibdo1.setBirthOrder(13);
        verifyDiff(List.of("birthOrder are not equal"), List.of("birthOrder are not equal"));
    }

    @Test
    void testDiffHeader() {
        ibdo1.setHeader("header".getBytes(StandardCharsets.US_ASCII));
        verifyDiff(List.of("header are not equal: [B@xxxxxxxx : null"), List.of("header are not equal: null : [B@xxxxxxxx"));
    }

    @Test
    void testDiffFooter() {
        ibdo1.setFooter("footer".getBytes(StandardCharsets.US_ASCII));
        verifyDiff(List.of("footer are not equal: [B@xxxxxxxx : null"), List.of("footer are not equal: null : [B@xxxxxxxx"));
    }

    @Test
    void testDiffHeaderEncoding() {
        ibdo1.setHeaderEncoding("header_encoding");
        verifyDiff(List.of("headerEncoding are not equal: header_encoding : null"), List.of("headerEncoding are not equal: null : header_encoding"));
    }

    @Test
    void testDiffClassification() {
        ibdo1.setClassification("classification");
        verifyDiff(List.of("classification are not equal: classification : null"), List.of("classification are not equal: null : classification"));
    }

    @Test
    void testDiffBroken() {
        ibdo1.setBroken("broken");
        verifyDiff(List.of("broken are not equal"), List.of("broken are not equal"));
    }

    @Test
    void testDiffOutputable() {
        ibdo1.setOutputable(false);
        verifyDiff(List.of("outputable are not equal"), List.of("outputable are not equal"));
    }

    @Test
    void testDiffId() {
        ibdo1.setId("id");
        verifyDiff(List.of("id are not equal: id : null"), List.of("id are not equal: null : id"));
    }

    @Test
    void testDiffWorkBundleId() {
        ibdo1.setWorkBundleId("workbundle_id");
        verifyDiff(List.of("workBundleId are not equal: workbundle_id : null"), List.of("workBundleId are not equal: null : workbundle_id"));
    }

    @Test
    void testDiffTransactionId() {
        ibdo1.setTransactionId("transaction_id");
        verifyDiff(List.of("transactionId are not equal: transaction_id : null"), List.of("transactionId are not equal: null : transaction_id"));
    }

    @Test
    void testDiffList() {
        final List<IBaseDataObject> ibdoList3 = Arrays.asList(ibdo1, ibdo2);
        verifyDiffList(Collections.emptyList(), null, null);
        verifyDiffList(Collections.emptyList(), new ArrayList<>(), null);
        verifyDiffList(Collections.emptyList(), null, new ArrayList<>());
        verifyDiffList(Collections.emptyList(), ibdoList1, ibdoList1);
        verifyDiffList(List.of("test are not equal: 1.s=2 2.s=1"), ibdoList3, ibdoList2);
        verifyDiffList(List.of("test are not equal: 1.s=1 2.s=2"), ibdoList1, ibdoList3);

        ibdo2.setClassification("classification");
        verifyDiffList(List.of("test : 0 : classification are not equal: null : classification"), ibdoList1, ibdoList2);
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
