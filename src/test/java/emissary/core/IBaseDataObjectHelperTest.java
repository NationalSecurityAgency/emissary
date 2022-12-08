package emissary.core;

import emissary.core.channels.AbstractSeekableByteChannel;
import emissary.core.channels.InMemoryChannelFactory;
import emissary.core.channels.SeekableByteChannelFactory;
import emissary.kff.KffDataObjectHandler;
import emissary.parser.SessionParser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
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

    private void verifyDiff(final int expectedDifferences) {
        verifyDiff(expectedDifferences, false, false, false, false);
    }

    private void verifyDiff(final int expectedDifferences, final boolean checkData, final boolean checkTimestamp, final boolean checkInternalId,
            final boolean checkTransformHistory) {
        IBaseDataObjectHelper.diff(ibdo1, ibdo1, differences, checkData, checkTimestamp, checkInternalId, checkTransformHistory);
        assertEquals(0, differences.size());
        IBaseDataObjectHelper.diff(ibdo1, ibdo2, differences, checkData, checkTimestamp, checkInternalId, checkTransformHistory);
        assertEquals(expectedDifferences, differences.size());
        differences.clear();
        IBaseDataObjectHelper.diff(ibdo2, ibdo1, differences, checkData, checkTimestamp, checkInternalId, checkTransformHistory);
        assertEquals(expectedDifferences, differences.size());
        differences.clear();
    }

    private void verifyDiffList(final int expectedDifferences, final List<IBaseDataObject> list1, final List<IBaseDataObject> list2) {
        IBaseDataObjectHelper.diff(list1, list1, "test", differences, false, false, false, false);
        assertEquals(0, differences.size());
        IBaseDataObjectHelper.diff(list1, list2, "test", differences, false, false, false, false);
        assertEquals(expectedDifferences, differences.size());
        differences.clear();
    }

    private void verifyClone(final String methodName, final IBaseDataObject origObj, final Boolean isSame, final Boolean isEquals,
            final Boolean switchWithFullClone) {
        try {
            final Method method = IBaseDataObject.class.getMethod(methodName);
            final boolean isArrayType = method.getReturnType().getName().equals("[B");
            final IBaseDataObject cloneFalseObj = IBaseDataObjectHelper.clone(origObj, false);
            final IBaseDataObject cloneTrueObj = IBaseDataObjectHelper.clone(origObj, true);
            verifyCloneAssertions(method, origObj, cloneFalseObj, isSame, isEquals);
            if (switchWithFullClone) {
                if (isArrayType) {
                    verifyCloneAssertions(method, origObj, cloneTrueObj, isSame, isEquals == null ? null : !isEquals);
                } else {
                    verifyCloneAssertions(method, origObj, cloneTrueObj, isSame == null ? null : !isSame, isEquals == null ? null : !isEquals);
                }
            } else {
                verifyCloneAssertions(method, origObj, cloneTrueObj, isSame, isEquals);
            }
        } catch (final NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            fail("Test error - couldn't invoke specified method", e);
        }
    }

    private void verifyCloneAssertions(final Method method, final Object obj1, final Object obj2, final Boolean isSame, final Boolean isEquals)
            throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        final Object o1 = method.invoke(obj1);
        final Object o2 = method.invoke(obj2);

        if (isSame != null) {
            if (isSame) {
                assertSame(o1, o2);
            } else {
                assertNotSame(o1, o2);
            }
        }

        if (isEquals != null) {
            if (isEquals) {
                if (method.getReturnType().getName().equals("[B")) {
                    assertArrayEquals((byte[]) o1, (byte[]) o2);
                } else {
                    assertEquals(o1, o2);
                }
            } else {
                assertNotEquals(o1, o2);
            }
        }

    }

    @Test
    void testCloneArguments() {
        assertNotNull(IBaseDataObjectHelper.clone(new BaseDataObject(), false));
        checkThrowsNull(() -> IBaseDataObjectHelper.clone(null, false));
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
        verifyClone("getTransformHistory", ibdo1, IS_NOT_SAME, DONT_CHECK, EQUAL_WITHOUT_FULL_CLONE);

        final IBaseDataObject cloneFalseIbdo = IBaseDataObjectHelper.clone(ibdo1, false);
        assertEquals(ibdo1.getTransformHistory().getHistory(), cloneFalseIbdo.getTransformHistory().getHistory());

        final IBaseDataObject cloneTrueIbdo = IBaseDataObjectHelper.clone(ibdo1, true);
        assertEquals(ibdo1.getTransformHistory().getHistory(), cloneTrueIbdo.getTransformHistory().getHistory());
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
            helper.when(() -> IBaseDataObjectHelper.setPrivateFieldValue(any(), any(), any()))
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
        verifyClone("header", ibdo1, IS_NOT_SAME, IS_NOT_EQUALS, EQUAL_AFTER_FULL_CLONE);
    }

    @Test
    void testCloneFooter() {
        ibdo1.setFooter("footer".getBytes(StandardCharsets.US_ASCII));
        verifyClone("footer", ibdo1, IS_NOT_SAME, IS_NOT_EQUALS, EQUAL_AFTER_FULL_CLONE);
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
        verifyClone("getTransactionId", ibdo1, DONT_CHECK, IS_NOT_EQUALS, EQUAL_AFTER_FULL_CLONE);
    }

    private void checkThrowsNull(final Executable e) {
        assertThrows(NullPointerException.class, e);
    }

    @Test
    void testDiffArguments() {
        // Objects
        checkThrowsNull(() -> IBaseDataObjectHelper.diff(null, ibdo2, differences, false, false, false, false));
        checkThrowsNull(() -> IBaseDataObjectHelper.diff(ibdo1, null, differences, false, false, false, false));
        checkThrowsNull(() -> IBaseDataObjectHelper.diff(ibdo1, ibdo2, null, false, false, false, false));
        checkThrowsNull(() -> IBaseDataObjectHelper.diff(ibdoList1, ibdoList2, null, differences, false, false, false, false));
        checkThrowsNull(() -> IBaseDataObjectHelper.diff(ibdoList1, ibdoList2, "id", null, false, false, false, false));
        checkThrowsNull(() -> IBaseDataObjectHelper.diff(new Object(), new Object(), null, differences));
        checkThrowsNull(() -> IBaseDataObjectHelper.diff(new Object(), new Object(), "id", null));

        // Integers
        checkThrowsNull(() -> IBaseDataObjectHelper.diff(0, 0, null, differences));
        checkThrowsNull(() -> IBaseDataObjectHelper.diff(0, 0, "id", null));

        // Booleans
        checkThrowsNull(() -> IBaseDataObjectHelper.diff(false, false, null, differences));
        checkThrowsNull(() -> IBaseDataObjectHelper.diff(false, false, "id", null));

        // Maps
        checkThrowsNull(() -> IBaseDataObjectHelper.diff(null, new HashMap<>(), "id", differences));
        checkThrowsNull(() -> IBaseDataObjectHelper.diff(new HashMap<>(), null, "id", differences));
        checkThrowsNull(() -> IBaseDataObjectHelper.diff(new HashMap<>(), new HashMap<>(), null, differences));
        checkThrowsNull(() -> IBaseDataObjectHelper.diff(new HashMap<>(), new HashMap<>(), "id", null));
    }

    @Test
    void testDiffChannelFactory() {
        ibdo2.setData(new byte[1]);
        verifyDiff(1, true, false, false, false);

        ibdo1.setChannelFactory(InMemoryChannelFactory.create("0123456789".getBytes(StandardCharsets.US_ASCII)));
        ibdo2.setChannelFactory(InMemoryChannelFactory.create("9876543210".getBytes(StandardCharsets.US_ASCII)));
        verifyDiff(1, true, false, false, false);

        ibdo2.setChannelFactory(new SeekableByteChannelFactory() {
            @Override
            public SeekableByteChannel create() {
                return new AbstractSeekableByteChannel() {
                    @Override
                    protected void closeImpl() throws IOException {
                        throw new IOException("Test SBC that always throws IOException!");
                    }

                    @Override
                    protected int readImpl(ByteBuffer byteBuffer, int maxBytesToRead) throws IOException {
                        throw new IOException("Test SBC that always throws IOException!");
                    }

                    @Override
                    protected long sizeImpl() throws IOException {
                        throw new IOException("Test SBC that always throws IOException!");
                    }
                };
            }
        });

        verifyDiff(1, true, false, false, false);
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
        verifyDiff(1, false, false, false, true);
    }

    @Test
    void testDiffParameters() {
        ibdo1.putParameter("STRING", "string");
        ibdo1.putParameter("LIST", Arrays.asList("first", "second", "third"));
        verifyDiff(1);
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
        ibdo1.setCreationTimestamp(new Date(1234567890));
        verifyDiff(1, false, true, false, false);
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
        verifyDiff(1, false, false, true, false);
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
    void testAddParentInformationToChild() throws Exception {
        final IBaseDataObject parentIbdo = ibdo1;
        final IBaseDataObject childIbdo = ibdo2;
        final boolean nullifyFileType = false;
        final Set<String> alwaysCopyMetadataKeys = new HashSet<>();
        final String placeKey = "place";
        final KffDataObjectHandler mockKffDataObjectHandler = Mockito.mock(KffDataObjectHandler.class);

        alwaysCopyMetadataKeys.add("key_not_in_parent");

        checkThrowsNull(() -> IBaseDataObjectHelper.addParentInformationToChild(null, childIbdo, nullifyFileType,
                alwaysCopyMetadataKeys, placeKey, mockKffDataObjectHandler));
        checkThrowsNull(() -> IBaseDataObjectHelper.addParentInformationToChild(parentIbdo, null, nullifyFileType,
                alwaysCopyMetadataKeys, placeKey, mockKffDataObjectHandler));
        checkThrowsNull(() -> IBaseDataObjectHelper.addParentInformationToChild(parentIbdo, childIbdo,
                nullifyFileType, null, placeKey, mockKffDataObjectHandler));
        checkThrowsNull(() -> IBaseDataObjectHelper.addParentInformationToChild(parentIbdo, childIbdo,
                nullifyFileType, alwaysCopyMetadataKeys, null, mockKffDataObjectHandler));
        checkThrowsNull(() -> IBaseDataObjectHelper.addParentInformationToChild(parentIbdo, childIbdo,
                nullifyFileType, alwaysCopyMetadataKeys, placeKey, null));

        childIbdo.setFileType("filetype");
        IBaseDataObjectHelper.addParentInformationToChild(parentIbdo, childIbdo,
                true, alwaysCopyMetadataKeys, placeKey, mockKffDataObjectHandler);
        assertNull(childIbdo.getFileType());

        final IBaseDataObject spyChildIbdo = Mockito.spy(new BaseDataObject());

        Mockito.when(spyChildIbdo.getChannelSize()).thenThrow(IOException.class);
        IBaseDataObjectHelper.addParentInformationToChild(parentIbdo, spyChildIbdo,
                true, alwaysCopyMetadataKeys, placeKey, mockKffDataObjectHandler);
        assertNull(spyChildIbdo.getParameter(SessionParser.ORIG_DOC_SIZE_KEY));

        final KffDataObjectHandler mockKffDataObjectHandler1 = Mockito.mock(KffDataObjectHandler.class);
        final IBaseDataObject childIbdo1 = new BaseDataObject();

        childIbdo1.setChannelFactory(InMemoryChannelFactory.create("0123456789".getBytes(StandardCharsets.US_ASCII)));
        Mockito.doThrow(NoSuchAlgorithmException.class).when(mockKffDataObjectHandler1).hashData(Mockito.any(SeekableByteChannelFactory.class),
                Mockito.anyString());
        IBaseDataObjectHelper.addParentInformationToChild(parentIbdo, childIbdo1,
                true, alwaysCopyMetadataKeys, placeKey, mockKffDataObjectHandler1);
        assertFalse(KffDataObjectHandler.hashPresent(childIbdo1));
    }

    @Test
    void testAddParentInformationToChildren() {
        final IBaseDataObject parentIbdo = ibdo1;
        final boolean nullifyFileType = false;
        final Set<String> alwaysCopyMetadataKeys = new HashSet<>();
        final String placeKey = "place";
        final KffDataObjectHandler mockKffDataObjectHandler = Mockito.mock(KffDataObjectHandler.class);

        checkThrowsNull(() -> IBaseDataObjectHelper.addParentInformationToChildren(null, null, nullifyFileType,
                alwaysCopyMetadataKeys, placeKey, mockKffDataObjectHandler));
        checkThrowsNull(() -> IBaseDataObjectHelper.addParentInformationToChildren(parentIbdo, null,
                nullifyFileType, null, placeKey, mockKffDataObjectHandler));
        checkThrowsNull(() -> IBaseDataObjectHelper.addParentInformationToChildren(parentIbdo, null,
                nullifyFileType, alwaysCopyMetadataKeys, null, mockKffDataObjectHandler));
        checkThrowsNull(() -> IBaseDataObjectHelper.addParentInformationToChildren(parentIbdo, null,
                nullifyFileType, alwaysCopyMetadataKeys, placeKey, null));

        IBaseDataObjectHelper.addParentInformationToChildren(parentIbdo, null, nullifyFileType, alwaysCopyMetadataKeys, placeKey,
                mockKffDataObjectHandler);

        final List<IBaseDataObject> children = new ArrayList<>();

        children.add(null);

        IBaseDataObjectHelper.addParentInformationToChildren(parentIbdo, children, nullifyFileType, alwaysCopyMetadataKeys, placeKey,
                mockKffDataObjectHandler);

        assertTrue(true);
    }
}
