package emissary.core;

import emissary.core.channels.AbstractSeekableByteChannel;
import emissary.core.channels.InMemoryChannelFactory;
import emissary.core.channels.SeekableByteChannelFactory;
import emissary.kff.KffDataObjectHandler;
import emissary.parser.SessionParser;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;

class IBaseDataObjectHelperTest {

    private IBaseDataObject ibdo1;
    private IBaseDataObject ibdo2;
    private List<IBaseDataObject> ibdoList1;
    private List<IBaseDataObject> ibdoList2;
    private List<String> differences;

    private static final Boolean IS_SAME = true;
    private static final Boolean IS_EQUALS = true;
    private static final Boolean IS_NOT_SAME = false;
    private static final Boolean IS_NOT_EQUALS = false;
    private static final Boolean DONT_CHECK = null;
    private static final Boolean EQUAL_WITHOUT_FULL_CLONE = false;
    private static final Boolean EQUAL_AFTER_FULL_CLONE = true;

    @BeforeEach
    void setup() {
        ibdo1 = new BaseDataObject();
        ibdo2 = new BaseDataObject();
        ibdoList1 = Arrays.asList(ibdo1);
        ibdoList2 = Arrays.asList(ibdo2);
        differences = new ArrayList<>();
    }

    private void verifyClone(final String methodName, final IBaseDataObject origObj, final Boolean isSame, final Boolean isEquals,
            final Boolean switchWithFullClone) {
        try {
            final Method method = IBaseDataObject.class.getMethod(methodName);
            final IBaseDataObject cloneFalseObj = IBaseDataObjectHelper.clone(origObj, false);
            final IBaseDataObject cloneTrueObj = IBaseDataObjectHelper.clone(origObj, true);
            verifyCloneAssertions(method, origObj, cloneFalseObj, isSame, isEquals);
            if (switchWithFullClone) {
                verifyCloneAssertions(method, origObj, cloneTrueObj, isSame == null ? null : !isSame, isEquals == null ? null : !isEquals);
            } else {
                verifyCloneAssertions(method, origObj, cloneTrueObj, isSame, isEquals);
            }
        } catch (final NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            Assertions.fail("Test error - couldn't invoke specified method", e);
        }
    }

    private void verifyCloneAssertions(final Method method, final Object obj1, final Object obj2, final Boolean isSame, final Boolean isEquals)
            throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        final Object o1 = method.invoke(obj1);
        final Object o2 = method.invoke(obj2);

        if (isSame != null) {
            if (isSame) {
                Assertions.assertSame(o1, o2);
            } else {
                Assertions.assertNotSame(o1, o2);
            }
        }

        if (isEquals != null) {
            if (isEquals) {
                Assertions.assertEquals(o1, o2);
            } else {
                Assertions.assertNotEquals(o1, o2);
            }
        }

    }

    @Test
    void testCloneArguments() {
        Assertions.assertNotNull(IBaseDataObjectHelper.clone(new BaseDataObject(), false));
        Assertions.assertThrows(NullPointerException.class, () -> IBaseDataObjectHelper.clone(null, false));
    }

    @Test
    void testCloneChannelFactory() {
        ibdo1.setChannelFactory(InMemoryChannelFactory.create("0123456789".getBytes(StandardCharsets.US_ASCII)));
        verifyClone("getChannelFactory", ibdo1, IS_SAME, IS_EQUALS, EQUAL_WITHOUT_FULL_CLONE);
    }

    @Test
    void testCloneCurrentForms() {
        ibdo1.pushCurrentForm("AAA");
        ibdo1.pushCurrentForm("BBB");
        ibdo1.pushCurrentForm("CCC");
        verifyClone("getAllCurrentForms", ibdo1, IS_NOT_SAME, IS_EQUALS, EQUAL_WITHOUT_FULL_CLONE);
    }

    @Test
    void testCloneTransformHistory() {
        ibdo1.appendTransformHistory("AAA", false);
        ibdo1.appendTransformHistory("BBB", true);

        final IBaseDataObject cloneFalseIbdo = IBaseDataObjectHelper.clone(ibdo1, false);

        Assertions.assertNotSame(ibdo1.getTransformHistory(), cloneFalseIbdo.getTransformHistory());
        Assertions.assertEquals(ibdo1.getTransformHistory().getHistory(), cloneFalseIbdo.getTransformHistory().getHistory());

        final IBaseDataObject cloneTrueIbdo = IBaseDataObjectHelper.clone(ibdo1, true);

        Assertions.assertNotSame(ibdo1.getTransformHistory(), cloneTrueIbdo.getTransformHistory());
        Assertions.assertEquals(ibdo1.getTransformHistory().getHistory(), cloneTrueIbdo.getTransformHistory().getHistory());
    }

    @Test
    void testCloneParameters() {
        ibdo1.putParameter("STRING", "string");
        ibdo1.putParameter("LIST", Arrays.asList("first", "second", "third"));
        verifyClone("getParameters", ibdo1, IS_NOT_SAME, IS_EQUALS, EQUAL_WITHOUT_FULL_CLONE);
    }

    @Test
    void testCloneAlternateViews() {
        ibdo1.addAlternateView("AAA", "AAA".getBytes(StandardCharsets.US_ASCII));
        ibdo1.addAlternateView("BBB", "BBB".getBytes(StandardCharsets.US_ASCII));
        ibdo1.addAlternateView("CCC", "CCC".getBytes(StandardCharsets.US_ASCII));
        verifyClone("getAlternateViews", ibdo1, IS_NOT_SAME, IS_EQUALS, EQUAL_WITHOUT_FULL_CLONE);
    }

    @Test
    void testClonePriority() {
        ibdo1.setPriority(13);
        verifyClone("getPriority", ibdo1, DONT_CHECK, IS_EQUALS, EQUAL_WITHOUT_FULL_CLONE);
    }

    @Test
    void testCloneCreationTimestamp() {
        ibdo1.setCreationTimestamp(new Date(1234567890));
        verifyClone("getCreationTimestamp", ibdo1, IS_NOT_SAME, IS_EQUALS, EQUAL_WITHOUT_FULL_CLONE);
    }

    @Test
    void testCloneExtractedRecords() {
        ibdo1.addExtractedRecord(new BaseDataObject());
        ibdo1.addExtractedRecord(new BaseDataObject());
        ibdo1.addExtractedRecord(new BaseDataObject());

        verifyClone("getExtractedRecords", ibdo1, IS_NOT_SAME, IS_EQUALS, EQUAL_WITHOUT_FULL_CLONE);
    }

    @Test
    void testCloneFilename() {
        ibdo1.setFilename("filename");
        verifyClone("getFilename", ibdo1, DONT_CHECK, IS_EQUALS, EQUAL_WITHOUT_FULL_CLONE);
    }

    @Test
    void testCloneInternalId() {
        verifyClone("getInternalId", ibdo1, IS_NOT_SAME, IS_NOT_EQUALS, EQUAL_AFTER_FULL_CLONE);
        // Now assert that if an exception occurs, the IDs will differ
        try (final MockedStatic<IBaseDataObjectHelper> helper = Mockito.mockStatic(IBaseDataObjectHelper.class, Mockito.CALLS_REAL_METHODS)) {
            helper.when(() -> IBaseDataObjectHelper.setPrivateField(any(), any(), any()))
                    .thenThrow(IllegalAccessException.class);

            final IBaseDataObject cloneExceptionIbdo = IBaseDataObjectHelper.clone(ibdo1, EQUAL_AFTER_FULL_CLONE);
            assertNotEquals(ibdo1.getInternalId(), cloneExceptionIbdo.getInternalId());
            // IDs will differ if an exception occurs during setPrivateField
        }
    }

    @Test
    void testCloneProcessingError() {
        ibdo1.addProcessingError("processing_error");
        verifyClone("getProcessingError", ibdo1, DONT_CHECK, IS_NOT_EQUALS, EQUAL_AFTER_FULL_CLONE);
    }

    @Test
    void testCloneFontEncoding() {
        ibdo1.setFontEncoding("font_encoding");
        verifyClone("getFontEncoding", ibdo1, IS_NOT_SAME, IS_NOT_EQUALS, EQUAL_AFTER_FULL_CLONE);
    }

    @Test
    void testCloneNumChildren() {
        ibdo1.setNumChildren(13);
        verifyClone("getNumChildren", ibdo1, DONT_CHECK, IS_NOT_EQUALS, EQUAL_AFTER_FULL_CLONE);
    }

    @Test
    void testCloneNumSiblings() {
        ibdo1.setNumSiblings(13);
        verifyClone("getNumSiblings", ibdo1, DONT_CHECK, IS_NOT_EQUALS, EQUAL_AFTER_FULL_CLONE);
    }

    @Test
    void testCloneBirthOrder() {
        ibdo1.setBirthOrder(13);
        verifyClone("getBirthOrder", ibdo1, DONT_CHECK, IS_NOT_EQUALS, EQUAL_AFTER_FULL_CLONE);
    }

    @Test
    void testCloneHeader() {
        ibdo1.setHeader("header".getBytes(StandardCharsets.US_ASCII));

        final IBaseDataObject cloneFalseIbdo = IBaseDataObjectHelper.clone(ibdo1, false);

        Assertions.assertNotSame(ibdo1.header(), cloneFalseIbdo.header());
        Assertions.assertNotEquals(ibdo1.header(), cloneFalseIbdo.header());

        final IBaseDataObject cloneTrueIbdo = IBaseDataObjectHelper.clone(ibdo1, true);

        Assertions.assertNotSame(ibdo1.header(), cloneTrueIbdo.header());
        Assertions.assertArrayEquals(ibdo1.header(), cloneTrueIbdo.header());
    }

    @Test
    void testCloneFooter() {
        ibdo1.setFooter("footer".getBytes(StandardCharsets.US_ASCII));

        final IBaseDataObject cloneFalseIbdo = IBaseDataObjectHelper.clone(ibdo1, false);

        Assertions.assertNotSame(ibdo1.footer(), cloneFalseIbdo.footer());
        Assertions.assertNotEquals(ibdo1.footer(), cloneFalseIbdo.footer());

        final IBaseDataObject cloneTrueIbdo = IBaseDataObjectHelper.clone(ibdo1, true);

        Assertions.assertNotSame(ibdo1.footer(), cloneTrueIbdo.footer());
        Assertions.assertArrayEquals(ibdo1.footer(), cloneTrueIbdo.footer());
    }

    @Test
    void testCloneHeaderEncoding() {
        ibdo1.setHeaderEncoding("header_encoding");
        verifyClone("getHeaderEncoding", ibdo1, DONT_CHECK, IS_NOT_EQUALS, EQUAL_AFTER_FULL_CLONE);
    }

    @Test
    void testCloneClassification() {
        ibdo1.setClassification("classification");
        verifyClone("getClassification", ibdo1, DONT_CHECK, IS_NOT_EQUALS, EQUAL_AFTER_FULL_CLONE);
    }

    @Test
    void testCloneBroken() {
        ibdo1.setBroken("broken");
        verifyClone("getBroken", ibdo1, DONT_CHECK, IS_NOT_EQUALS, EQUAL_AFTER_FULL_CLONE);
    }

    @Test
    void testCloneOutputable() {
        ibdo1.setOutputable(false);
        verifyClone("isOutputable", ibdo1, DONT_CHECK, IS_NOT_EQUALS, EQUAL_AFTER_FULL_CLONE);
    }

    @Test
    void testCloneId() {
        ibdo1.setId("id");
        verifyClone("getId", ibdo1, DONT_CHECK, IS_NOT_EQUALS, EQUAL_AFTER_FULL_CLONE);
    }

    @Test
    void testCloneWorkBundleId() {
        ibdo1.setWorkBundleId("workbundle_id");
        verifyClone("getWorkBundleId", ibdo1, DONT_CHECK, IS_NOT_EQUALS, EQUAL_AFTER_FULL_CLONE);
    }

    @Test
    void testCloneTransactionId() {
        ibdo1.setTransactionId("transaction_id");

        final IBaseDataObject cloneFalseIbdo = IBaseDataObjectHelper.clone(ibdo1, false);

        Assertions.assertNotEquals(ibdo1.getTransactionId(), cloneFalseIbdo.getTransactionId());

        final IBaseDataObject cloneTrueIbdo = IBaseDataObjectHelper.clone(ibdo1, true);

        Assertions.assertEquals(ibdo1.getTransactionId(), cloneTrueIbdo.getTransactionId());
    }

    @Test
    void testDiffArguments() {
        Assertions.assertThrows(NullPointerException.class,
                () -> IBaseDataObjectHelper.diff(null, ibdo2, differences, false, false, false, false));
        Assertions.assertThrows(NullPointerException.class,
                () -> IBaseDataObjectHelper.diff(ibdo1, null, differences, false, false, false, false));
        Assertions.assertThrows(NullPointerException.class,
                () -> IBaseDataObjectHelper.diff(ibdo1, ibdo2, null, false, false, false, false));

        Assertions.assertThrows(NullPointerException.class,
                () -> IBaseDataObjectHelper.diff(ibdoList1, ibdoList2, null, differences, false, false, false, false));
        Assertions.assertThrows(NullPointerException.class,
                () -> IBaseDataObjectHelper.diff(ibdoList1, ibdoList2, "id", null, false, false, false, false));

        Assertions.assertThrows(NullPointerException.class,
                () -> IBaseDataObjectHelper.diff(new Object(), new Object(), null, differences));
        Assertions.assertThrows(NullPointerException.class,
                () -> IBaseDataObjectHelper.diff(new Object(), new Object(), "id", null));

        Assertions.assertThrows(NullPointerException.class,
                () -> IBaseDataObjectHelper.diff(0, 0, null, differences));
        Assertions.assertThrows(NullPointerException.class,
                () -> IBaseDataObjectHelper.diff(0, 0, "id", null));

        Assertions.assertThrows(NullPointerException.class,
                () -> IBaseDataObjectHelper.diff(false, false, null, differences));
        Assertions.assertThrows(NullPointerException.class,
                () -> IBaseDataObjectHelper.diff(false, false, "id", null));

        Assertions.assertThrows(NullPointerException.class,
                () -> IBaseDataObjectHelper.diff(null, new HashMap<>(), "id", differences));
        Assertions.assertThrows(NullPointerException.class,
                () -> IBaseDataObjectHelper.diff(new HashMap<>(), null, "id", differences));
        Assertions.assertThrows(NullPointerException.class,
                () -> IBaseDataObjectHelper.diff(new HashMap<>(), new HashMap<>(), null, differences));
        Assertions.assertThrows(NullPointerException.class,
                () -> IBaseDataObjectHelper.diff(new HashMap<>(), new HashMap<>(), "id", null));
    }

    @Test
    void testDiffChannelFactory() {
        final IBaseDataObject noDataIbdo = new BaseDataObject();
        final IBaseDataObject dataIbdo = new BaseDataObject();

        dataIbdo.setData(new byte[1]);

        IBaseDataObjectHelper.diff(noDataIbdo, noDataIbdo, differences, true, false, false, false);
        Assertions.assertEquals(0, differences.size());
        differences.clear();

        IBaseDataObjectHelper.diff(noDataIbdo, dataIbdo, differences, true, false, false, false);
        Assertions.assertEquals(1, differences.size());
        differences.clear();

        IBaseDataObjectHelper.diff(dataIbdo, noDataIbdo, differences, true, false, false, false);
        Assertions.assertEquals(1, differences.size());
        differences.clear();

        final SeekableByteChannelFactory sbcf1 = InMemoryChannelFactory.create("0123456789".getBytes(StandardCharsets.US_ASCII));
        final SeekableByteChannelFactory sbcf2 = InMemoryChannelFactory.create("9876543210".getBytes(StandardCharsets.US_ASCII));

        ibdo1.setChannelFactory(sbcf1);
        ibdo2.setChannelFactory(sbcf2);

        IBaseDataObjectHelper.diff(ibdo1, ibdo1, differences, true, false, false, false);

        Assertions.assertEquals(0, differences.size());

        IBaseDataObjectHelper.diff(ibdo1, ibdo2, differences, true, false, false, false);

        Assertions.assertEquals(1, differences.size());

        ibdo1.setChannelFactory(new SeekableByteChannelFactory() {
            @Override
            public SeekableByteChannel create() {
                return new AbstractSeekableByteChannel() {
                    @Override
                    protected void closeImpl() throws IOException {
                        throw new IOException("Test SBC that always throws IOException!");
                    }

                    @Override
                    protected int readImpl(ByteBuffer byteBuffer) throws IOException {
                        throw new IOException("Test SBC that always throws IOException!");
                    }

                    @Override
                    protected long sizeImpl() throws IOException {
                        throw new IOException("Test SBC that always throws IOException!");
                    }
                };
            }
        });

        differences.clear();

        IBaseDataObjectHelper.diff(ibdo1, ibdo2, differences, true, false, false, false);

        Assertions.assertEquals(1, differences.size());
    }

    @Test
    void testDiffCurrentForm() {
        ibdo1.pushCurrentForm("AAA");
        ibdo1.pushCurrentForm("BBB");
        ibdo1.pushCurrentForm("CCC");

        IBaseDataObjectHelper.diff(ibdo1, ibdo1, differences, false, false, false, false);

        Assertions.assertEquals(0, differences.size());

        IBaseDataObjectHelper.diff(ibdo1, ibdo2, differences, false, false, false, false);

        Assertions.assertEquals(1, differences.size());
    }

    @Test
    void testDiffHistory() {
        ibdo1.appendTransformHistory("AAA", false);
        ibdo1.appendTransformHistory("BBB", true);

        IBaseDataObjectHelper.diff(ibdo1, ibdo1, differences, false, false, false, true);

        Assertions.assertEquals(0, differences.size());

        IBaseDataObjectHelper.diff(ibdo1, ibdo2, differences, false, false, false, true);

        Assertions.assertEquals(1, differences.size());
    }

    @Test
    void testDiffParameters() {
        ibdo1.putParameter("STRING", "string");
        ibdo1.putParameter("LIST", Arrays.asList("first", "second", "third"));

        IBaseDataObjectHelper.diff(ibdo1, ibdo1, differences, false, false, false, false);

        Assertions.assertEquals(0, differences.size());

        IBaseDataObjectHelper.diff(ibdo1, ibdo2, differences, false, false, false, false);

        Assertions.assertEquals(1, differences.size());
    }

    @Test
    void testDiffAlternateViews() {
        ibdo1.addAlternateView("AAA", "AAA".getBytes(StandardCharsets.US_ASCII));
        ibdo1.addAlternateView("BBB", "BBB".getBytes(StandardCharsets.US_ASCII));
        ibdo1.addAlternateView("CCC", "CCC".getBytes(StandardCharsets.US_ASCII));

        IBaseDataObjectHelper.diff(ibdo1, ibdo1, differences, false, false, false, false);

        Assertions.assertEquals(0, differences.size());

        IBaseDataObjectHelper.diff(ibdo1, ibdo2, differences, false, false, false, false);

        Assertions.assertEquals(1, differences.size());

        ibdo2.addAlternateView("DDD", "DDD".getBytes(StandardCharsets.US_ASCII));
        ibdo2.addAlternateView("EEE", "EEE".getBytes(StandardCharsets.US_ASCII));
        ibdo2.addAlternateView("FFF", "FFF".getBytes(StandardCharsets.US_ASCII));

        differences.clear();

        IBaseDataObjectHelper.diff(ibdo1, ibdo2, differences, false, false, false, false);

        Assertions.assertEquals(1, differences.size());
    }

    @Test
    void testDiffPriority() {
        ibdo1.setPriority(13);

        IBaseDataObjectHelper.diff(ibdo1, ibdo1, differences, false, false, false, false);

        Assertions.assertEquals(0, differences.size());

        IBaseDataObjectHelper.diff(ibdo1, ibdo2, differences, false, false, false, false);

        Assertions.assertEquals(1, differences.size());
    }

    @Test
    void testDiffCreationTimestamp() {
        ibdo1.setCreationTimestamp(new Date(1234567890));

        IBaseDataObjectHelper.diff(ibdo1, ibdo1, differences, false, true, false, false);

        Assertions.assertEquals(0, differences.size());

        IBaseDataObjectHelper.diff(ibdo1, ibdo2, differences, false, true, false, false);

        Assertions.assertEquals(1, differences.size());
    }

    @Test
    void testDiffExtractedRecords() {
        ibdo1.addExtractedRecord(new BaseDataObject());
        ibdo1.addExtractedRecord(new BaseDataObject());
        ibdo1.addExtractedRecord(new BaseDataObject());

        IBaseDataObjectHelper.diff(ibdo1, ibdo1, differences, false, false, false, false);

        Assertions.assertEquals(0, differences.size());

        IBaseDataObjectHelper.diff(ibdo1, ibdo2, differences, false, false, false, false);

        Assertions.assertEquals(1, differences.size());
    }

    @Test
    void testDiffFilename() {
        ibdo1.setFilename("filename");

        IBaseDataObjectHelper.diff(ibdo1, ibdo1, differences, false, false, false, false);

        Assertions.assertEquals(0, differences.size());

        IBaseDataObjectHelper.diff(ibdo1, ibdo2, differences, false, false, false, false);

        Assertions.assertEquals(2, differences.size());
    }

    @Test
    void testDiffInternalId() {
        IBaseDataObjectHelper.diff(ibdo1, ibdo1, differences, false, false, true, false);

        Assertions.assertEquals(0, differences.size());

        IBaseDataObjectHelper.diff(ibdo1, ibdo2, differences, false, false, true, false);

        Assertions.assertEquals(1, differences.size());
    }

    @Test
    void testDiffProcessingError() {
        ibdo1.addProcessingError("processing_error");

        IBaseDataObjectHelper.diff(ibdo1, ibdo1, differences, false, false, false, false);

        Assertions.assertEquals(0, differences.size());

        IBaseDataObjectHelper.diff(ibdo1, ibdo2, differences, false, false, false, false);

        Assertions.assertEquals(1, differences.size());
    }

    @Test
    void testDiffFontEncoding() {
        ibdo1.setFontEncoding("font_encoding");

        IBaseDataObjectHelper.diff(ibdo1, ibdo1, differences, false, false, false, false);

        Assertions.assertEquals(0, differences.size());

        IBaseDataObjectHelper.diff(ibdo1, ibdo2, differences, false, false, false, false);

        Assertions.assertEquals(1, differences.size());
    }

    @Test
    void testDiffNumChildren() {
        ibdo1.setNumChildren(13);

        IBaseDataObjectHelper.diff(ibdo1, ibdo1, differences, false, false, false, false);

        Assertions.assertEquals(0, differences.size());

        IBaseDataObjectHelper.diff(ibdo1, ibdo2, differences, false, false, false, false);

        Assertions.assertEquals(1, differences.size());
    }

    @Test
    void testDiffNumSiblings() {
        ibdo1.setNumSiblings(13);

        IBaseDataObjectHelper.diff(ibdo1, ibdo1, differences, false, false, false, false);

        Assertions.assertEquals(0, differences.size());

        IBaseDataObjectHelper.diff(ibdo1, ibdo2, differences, false, false, false, false);

        Assertions.assertEquals(1, differences.size());
    }

    @Test
    void testDiffBirthOrder() {
        ibdo1.setBirthOrder(13);

        IBaseDataObjectHelper.diff(ibdo1, ibdo1, differences, false, false, false, false);

        Assertions.assertEquals(0, differences.size());

        IBaseDataObjectHelper.diff(ibdo1, ibdo2, differences, false, false, false, false);

        Assertions.assertEquals(1, differences.size());
    }

    @Test
    void testDiffHeader() {
        ibdo1.setHeader("header".getBytes(StandardCharsets.US_ASCII));

        IBaseDataObjectHelper.diff(ibdo1, ibdo1, differences, false, false, false, false);

        Assertions.assertEquals(0, differences.size());

        IBaseDataObjectHelper.diff(ibdo1, ibdo2, differences, false, false, false, false);

        Assertions.assertEquals(1, differences.size());
    }

    @Test
    void testDiffFooter() {
        ibdo1.setFooter("footer".getBytes(StandardCharsets.US_ASCII));

        IBaseDataObjectHelper.diff(ibdo1, ibdo1, differences, false, false, false, false);

        Assertions.assertEquals(0, differences.size());

        IBaseDataObjectHelper.diff(ibdo1, ibdo2, differences, false, false, false, false);

        Assertions.assertEquals(1, differences.size());
    }

    @Test
    void testDiffHeaderEncoding() {
        ibdo1.setHeaderEncoding("header_encoding");

        IBaseDataObjectHelper.diff(ibdo1, ibdo1, differences, false, false, false, false);

        Assertions.assertEquals(0, differences.size());

        IBaseDataObjectHelper.diff(ibdo1, ibdo2, differences, false, false, false, false);

        Assertions.assertEquals(1, differences.size());
    }

    @Test
    void testDiffClassification() {
        ibdo1.setClassification("classification");

        IBaseDataObjectHelper.diff(ibdo1, ibdo1, differences, false, false, false, false);

        Assertions.assertEquals(0, differences.size());

        IBaseDataObjectHelper.diff(ibdo1, ibdo2, differences, false, false, false, false);

        Assertions.assertEquals(1, differences.size());
    }

    @Test
    void testDiffBroken() {
        ibdo1.setBroken("broken");

        IBaseDataObjectHelper.diff(ibdo1, ibdo1, differences, false, false, false, false);

        Assertions.assertEquals(0, differences.size());

        IBaseDataObjectHelper.diff(ibdo1, ibdo2, differences, false, false, false, false);

        Assertions.assertEquals(1, differences.size());
    }

    @Test
    void testDiffOutputable() {
        ibdo1.setOutputable(false);

        IBaseDataObjectHelper.diff(ibdo1, ibdo1, differences, false, false, false, false);

        Assertions.assertEquals(0, differences.size());

        IBaseDataObjectHelper.diff(ibdo1, ibdo2, differences, false, false, false, false);

        Assertions.assertEquals(1, differences.size());
    }

    @Test
    void testDiffId() {
        ibdo1.setId("id");

        IBaseDataObjectHelper.diff(ibdo1, ibdo1, differences, false, false, false, false);

        Assertions.assertEquals(0, differences.size());

        IBaseDataObjectHelper.diff(ibdo1, ibdo2, differences, false, false, false, false);

        Assertions.assertEquals(1, differences.size());
    }

    @Test
    void testDiffWorkBundleId() {
        ibdo1.setWorkBundleId("workbundle_id");

        IBaseDataObjectHelper.diff(ibdo1, ibdo1, differences, false, false, false, false);

        Assertions.assertEquals(0, differences.size());

        IBaseDataObjectHelper.diff(ibdo1, ibdo2, differences, false, false, false, false);

        Assertions.assertEquals(1, differences.size());
    }

    @Test
    void testDiffTransactionId() {
        ibdo1.setTransactionId("transaction_id");

        IBaseDataObjectHelper.diff(ibdo1, ibdo1, differences, false, false, false, false);

        Assertions.assertEquals(0, differences.size());

        IBaseDataObjectHelper.diff(ibdo1, ibdo2, differences, false, false, false, false);

        Assertions.assertEquals(1, differences.size());
    }

    @Test
    void testDiffList() {
        final List<IBaseDataObject> ibdoList3 = Arrays.asList(ibdo1, ibdo2);
        IBaseDataObjectHelper.diff(null, null, "id", differences, false, false, false, false);

        Assertions.assertEquals(0, differences.size());

        IBaseDataObjectHelper.diff(new ArrayList<>(), null, "id", differences, false, false, false, false);

        Assertions.assertEquals(0, differences.size());

        IBaseDataObjectHelper.diff(null, new ArrayList<>(), "id", differences, false, false, false, false);

        Assertions.assertEquals(0, differences.size());

        IBaseDataObjectHelper.diff(ibdoList1, ibdoList1, "id", differences, false, false, false, false);

        Assertions.assertEquals(0, differences.size());

        IBaseDataObjectHelper.diff(ibdoList3, ibdoList2, "id", differences, false, false, false, false);

        Assertions.assertEquals(1, differences.size());
        differences.clear();

        IBaseDataObjectHelper.diff(ibdoList1, ibdoList3, "id", differences, false, false, false, false);

        Assertions.assertEquals(1, differences.size());
        differences.clear();

        ibdo2.setClassification("classification");

        IBaseDataObjectHelper.diff(ibdoList1, ibdoList2, "id", differences, false, false, false, false);

        Assertions.assertEquals(1, differences.size());
    }

    @Test
    void testAddParentInformationToChild() throws Exception {
        final IBaseDataObject parentIbdo = ibdo1;
        final IBaseDataObject childIbdo = ibdo2;
        final boolean nullifyFileType = false;
        final Set<String> alwaysCopyMetadataKeys = new HashSet<>();
        final String placeKey = "place";
        final KffDataObjectHandler mockKffDataObjectHandler = Mockito.mock(KffDataObjectHandler.class);

        alwaysCopyMetadataKeys.add("key_not_in_parent");

        Assertions.assertThrows(NullPointerException.class, () -> IBaseDataObjectHelper.addParentInformationToChild(null, childIbdo, nullifyFileType,
                alwaysCopyMetadataKeys, placeKey, mockKffDataObjectHandler));
        Assertions.assertThrows(NullPointerException.class, () -> IBaseDataObjectHelper.addParentInformationToChild(parentIbdo, null, nullifyFileType,
                alwaysCopyMetadataKeys, placeKey, mockKffDataObjectHandler));
        Assertions.assertThrows(NullPointerException.class, () -> IBaseDataObjectHelper.addParentInformationToChild(parentIbdo, childIbdo,
                nullifyFileType, null, placeKey, mockKffDataObjectHandler));
        Assertions.assertThrows(NullPointerException.class, () -> IBaseDataObjectHelper.addParentInformationToChild(parentIbdo, childIbdo,
                nullifyFileType, alwaysCopyMetadataKeys, null, mockKffDataObjectHandler));
        Assertions.assertThrows(NullPointerException.class, () -> IBaseDataObjectHelper.addParentInformationToChild(parentIbdo, childIbdo,
                nullifyFileType, alwaysCopyMetadataKeys, placeKey, null));

        childIbdo.setFileType("filetype");
        IBaseDataObjectHelper.addParentInformationToChild(parentIbdo, childIbdo,
                true, alwaysCopyMetadataKeys, placeKey, mockKffDataObjectHandler);
        Assertions.assertNull(childIbdo.getFileType());

        final IBaseDataObject spyChildIbdo = Mockito.spy(new BaseDataObject());

        Mockito.when(spyChildIbdo.getChannelSize()).thenThrow(IOException.class);
        IBaseDataObjectHelper.addParentInformationToChild(parentIbdo, spyChildIbdo,
                true, alwaysCopyMetadataKeys, placeKey, mockKffDataObjectHandler);
        Assertions.assertNull(spyChildIbdo.getParameter(SessionParser.ORIG_DOC_SIZE_KEY));

        final KffDataObjectHandler mockKffDataObjectHandler1 = Mockito.mock(KffDataObjectHandler.class);
        final IBaseDataObject childIbdo1 = new BaseDataObject();

        childIbdo1.setChannelFactory(InMemoryChannelFactory.create("0123456789".getBytes(StandardCharsets.US_ASCII)));
        Mockito.doThrow(NoSuchAlgorithmException.class).when(mockKffDataObjectHandler1).hash(Mockito.any(BaseDataObject.class), Mockito.anyBoolean());
        IBaseDataObjectHelper.addParentInformationToChild(parentIbdo, childIbdo1,
                true, alwaysCopyMetadataKeys, placeKey, mockKffDataObjectHandler1);
        Assertions.assertFalse(KffDataObjectHandler.hashPresent(childIbdo1));
    }

    @Test
    void testAddParentInformationToChildren() {
        final IBaseDataObject parentIbdo = ibdo1;
        final boolean nullifyFileType = false;
        final Set<String> alwaysCopyMetadataKeys = new HashSet<>();
        final String placeKey = "place";
        final KffDataObjectHandler mockKffDataObjectHandler = Mockito.mock(KffDataObjectHandler.class);

        Assertions.assertThrows(NullPointerException.class, () -> IBaseDataObjectHelper.addParentInformationToChildren(null, null, nullifyFileType,
                alwaysCopyMetadataKeys, placeKey, mockKffDataObjectHandler));
        Assertions.assertThrows(NullPointerException.class, () -> IBaseDataObjectHelper.addParentInformationToChildren(parentIbdo, null,
                nullifyFileType, null, placeKey, mockKffDataObjectHandler));
        Assertions.assertThrows(NullPointerException.class, () -> IBaseDataObjectHelper.addParentInformationToChildren(parentIbdo, null,
                nullifyFileType, alwaysCopyMetadataKeys, null, mockKffDataObjectHandler));
        Assertions.assertThrows(NullPointerException.class, () -> IBaseDataObjectHelper.addParentInformationToChildren(parentIbdo, null,
                nullifyFileType, alwaysCopyMetadataKeys, placeKey, null));

        IBaseDataObjectHelper.addParentInformationToChildren(parentIbdo, null, nullifyFileType, alwaysCopyMetadataKeys, placeKey,
                mockKffDataObjectHandler);

        final List<IBaseDataObject> children = new ArrayList<>();

        children.add(null);

        IBaseDataObjectHelper.addParentInformationToChildren(parentIbdo, children, nullifyFileType, alwaysCopyMetadataKeys, placeKey,
                mockKffDataObjectHandler);

        Assertions.assertTrue(true);
    }
}
