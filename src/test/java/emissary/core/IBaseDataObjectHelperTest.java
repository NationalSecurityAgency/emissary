package emissary.core;

import emissary.core.channels.InMemoryChannelFactory;
import emissary.kff.KffDataObjectHandler;
import emissary.parser.SessionParser;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mockito;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

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

class IBaseDataObjectHelperTest extends UnitTest {

    private IBaseDataObject ibdo1;
    private IBaseDataObject ibdo2;

    private static final Boolean IS_SAME = true;
    private static final Boolean IS_EQUALS = true;
    private static final Boolean IS_NOT_SAME = false;
    private static final Boolean IS_NOT_EQUALS = false;
    @Nullable
    private static final Boolean DONT_CHECK = null;
    private static final Boolean EQUAL_WITHOUT_FULL_CLONE = false;
    private static final Boolean EQUAL_AFTER_FULL_CLONE = true;

    @BeforeEach
    void setup() {
        ibdo1 = new BaseDataObject();
        ibdo2 = new BaseDataObject();
    }

    private static void verifyClone(final String methodName, final IBaseDataObject origObj, final Boolean isSame, final Boolean isEquals,
            final Boolean switchWithFullClone) {
        try {
            final Method method = IBaseDataObject.class.getMethod(methodName);
            final boolean isArrayType = "[B".equals(method.getReturnType().getName());
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

    private static void verifyCloneAssertions(final Method method, final Object obj1, final Object obj2, @Nullable final Boolean isSame,
            @Nullable final Boolean isEquals)
            throws IllegalAccessException, InvocationTargetException {
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
                if ("[B".equals(method.getReturnType().getName())) {
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

    private static void checkThrowsNull(final Executable e) {
        assertThrows(NullPointerException.class, e);
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
        Mockito.doThrow(NoSuchAlgorithmException.class).when(mockKffDataObjectHandler1).hash(Mockito.any(BaseDataObject.class), Mockito.anyBoolean());
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

    @Test
    void testAddParentInformationToChildExcluding() throws Exception {
        final IBaseDataObject parentIbdo = ibdo1;
        final IBaseDataObject childIbdo = ibdo2;
        final Pattern excludedParameters = Pattern.compile("FILETYPE|WILDCARDED_.*");

        checkThrowsNull(() -> IBaseDataObjectHelper.addParentInformationToChildExcluding(null, childIbdo, excludedParameters));
        checkThrowsNull(() -> IBaseDataObjectHelper.addParentInformationToChildExcluding(parentIbdo, null, excludedParameters));
        checkThrowsNull(() -> IBaseDataObjectHelper.addParentInformationToChildExcluding(parentIbdo, childIbdo, null));

        parentIbdo.setFileType("filetype");
        parentIbdo.setParameter("NON_EXCLUDED", "hereIam");
        parentIbdo.setParameter("WILDCARDED_KEYNAME", "anyValueWillDo");
        IBaseDataObjectHelper.addParentInformationToChildExcluding(parentIbdo, childIbdo, excludedParameters);
        assertNull(childIbdo.getFileType(), "Child should not contain simple excluded parameter from parent");
        assertNull(childIbdo.getParameter("WILDCARDED_KEYNAME"), "Child should not contain wildcard excluded parameter from parent");
        assertEquals(parentIbdo.getParameter("NON_EXCLUDED").get(0), childIbdo.getParameter("NON_EXCLUDED").get(0),
                "Child should have non-excluded parent parameter");
    }

    @Test
    void testFindPreferredDataByRegex() {
        final List<Pattern> emptyPreferredViews = new ArrayList<>();

        assertThrows(IllegalArgumentException.class, () -> IBaseDataObjectHelper.findPreferredDataByRegex(null, emptyPreferredViews));

        byte[] view1Bytes = "altview test bytes".getBytes(StandardCharsets.UTF_8);
        ibdo1.addAlternateView("view1", view1Bytes);

        ibdo1.addAlternateView("notpreferred", "test bytes".getBytes(StandardCharsets.UTF_8));

        byte[] primaryViewBytes = "primary view test bytes".getBytes(StandardCharsets.UTF_8);
        ibdo1.setData(primaryViewBytes);

        byte[] preferredData = IBaseDataObjectHelper.findPreferredDataByRegex(ibdo1, compileAsPatterns("view1", "view2", "view3"));
        assertEquals(view1Bytes, preferredData);

        preferredData = IBaseDataObjectHelper.findPreferredDataByRegex(ibdo1, compileAsPatterns("view1"));
        assertEquals(view1Bytes, preferredData, "Expected to find an alternate view and return alternate view bytes");

        preferredData = IBaseDataObjectHelper.findPreferredDataByRegex(ibdo1, compileAsPatterns("^view[12]"));
        assertEquals(view1Bytes, preferredData, "Expected to find an alternate view by regex and return alternate view bytes");

        preferredData = IBaseDataObjectHelper.findPreferredDataByRegex(ibdo1, compileAsPatterns("view_1", "view2", "view3"));
        assertEquals(primaryViewBytes, preferredData, "Expected to not find an alternate view and return the default primary view bytes");

        preferredData = IBaseDataObjectHelper.findPreferredDataByRegex(ibdo1, compileAsPatterns("^view[4]"));
        assertEquals(primaryViewBytes, preferredData, "Expected to not find an alternate view by regex and return the default primary view bytes");

        preferredData = IBaseDataObjectHelper.findPreferredDataByRegex(ibdo1, null);
        assertEquals(primaryViewBytes, preferredData, "Expected a null pattern to return the default primary view bytes");
    }

    static List<Pattern> compileAsPatterns(String... values) {
        return Stream.of(values).map(Pattern::compile).collect(Collectors.toList());
    }

    @Test
    void testFindPreferredData() {
        final List<String> emptyPreferredViews = new ArrayList<>();

        assertThrows(IllegalArgumentException.class, () -> IBaseDataObjectHelper.findPreferredData(null, emptyPreferredViews));

        final List<String> preferredViews = List.of("view1", "view2", "view3");
        final byte[] view1Bytes = "altview test bytes".getBytes(StandardCharsets.UTF_8);
        final byte[] primaryViewBytes = "primary view test bytes".getBytes(StandardCharsets.UTF_8);

        ibdo1.setData(primaryViewBytes);

        assertEquals(primaryViewBytes, IBaseDataObjectHelper.findPreferredData(ibdo1, null));
        assertEquals(primaryViewBytes, IBaseDataObjectHelper.findPreferredData(ibdo1, preferredViews));

        ibdo1.addAlternateView("view1", view1Bytes);
        ibdo1.addAlternateView("notpreferred", "test bytes".getBytes(StandardCharsets.UTF_8));

        assertEquals(view1Bytes, IBaseDataObjectHelper.findPreferredData(ibdo1, preferredViews));
    }
}
