package emissary.core;

import emissary.core.channels.SeekableByteChannelFactory;
import emissary.core.constants.IbdoXmlElementNames;
import emissary.util.os.OSReleaseUtil;

import jakarta.annotation.Nullable;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.math.NumberUtils;
import org.jdom2.Attribute;
import org.jdom2.Element;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static emissary.core.IBaseDataObjectXmlCodecs.BASE64;
import static emissary.core.IBaseDataObjectXmlCodecs.BASE64_DECODER;
import static emissary.core.IBaseDataObjectXmlCodecs.ENCODING_ATTRIBUTE_NAME;
import static emissary.core.constants.IbdoXmlElementNames.ATTACHMENT_ELEMENT_PREFIX;
import static emissary.core.constants.IbdoXmlElementNames.BROKEN;
import static emissary.core.constants.IbdoXmlElementNames.CLASSIFICATION;
import static emissary.core.constants.IbdoXmlElementNames.CURRENT_FORM;
import static emissary.core.constants.IbdoXmlElementNames.DATA;
import static emissary.core.constants.IbdoXmlElementNames.EXTRACTED_RECORD_ELEMENT_PREFIX;
import static emissary.core.constants.IbdoXmlElementNames.EXTRACT_COUNT;
import static emissary.core.constants.IbdoXmlElementNames.FILE_TYPE;
import static emissary.core.constants.IbdoXmlElementNames.FONT_ENCODING;
import static emissary.core.constants.IbdoXmlElementNames.INDEX;
import static emissary.core.constants.IbdoXmlElementNames.NAME;
import static emissary.core.constants.IbdoXmlElementNames.NOMETA;
import static emissary.core.constants.IbdoXmlElementNames.NOVIEW;
import static emissary.core.constants.IbdoXmlElementNames.NUM_ATTACHMENTS;
import static emissary.core.constants.IbdoXmlElementNames.PARAMETER;
import static emissary.core.constants.IbdoXmlElementNames.SHORT_NAME;
import static emissary.core.constants.IbdoXmlElementNames.VALUE;
import static emissary.core.constants.IbdoXmlElementNames.VIEW;

public class IBaseDataObjectDiffHelper {

    // Centralized message template definitions
    private static final String DIFF_NOT_NULL_MSG = "Required: differences not null";
    private static final String ID_NOT_NULL_MSG = "Required: identifier not null";
    private static final String ARE_NOT_EQUAL = " are not equal";
    private static final String INTERNAL_ID = "internalId";
    private static final String TRANSFORM_HISTORY = "transformHistory";
    private static final String FILE_TYPE_EMPTY = "fileTypeEmpty";
    private static final String CREATION_TIMESTAMP = "creationTimestamp";
    private static final String EXTRACTED_RECORDS = "extractedRecords";

    private IBaseDataObjectDiffHelper() {}

    /**
     * This method compares two IBaseDataObject's and adds any differences to the provided string list.
     *
     * @param expected the first IBaseDataObject to compare.
     * @param actual the second IBaseDataObject to compare.
     * @param differences the string list differences are to be added to.
     * @param options {@link DiffCheckConfiguration} containing config to specify whether to check data etc.
     */
    /**
     * This method compares two IBaseDataObject's and adds any differences to the provided string list.
     *
     * @param expected the first IBaseDataObject to compare.
     * @param actual the second IBaseDataObject to compare.
     * @param differences the string list differences are to be added to.
     * @param options {@link DiffCheckConfiguration} containing config to specify whether to check data etc.
     */
    public static void diff(final IBaseDataObject expected, final IBaseDataObject actual,
            final List<String> differences, final DiffCheckConfiguration options) {
        Validate.notNull(expected, "Required: \"expected\" ibdo not null");
        Validate.notNull(actual, "Required: \"actual\" ibdo not null");
        Validate.notNull(differences, DIFF_NOT_NULL_MSG);
        Validate.notNull(options, "Required: options not null");
        Validate.isTrue(options.isStrict(), "Cannot invoke strict multi-payload diff() when configuration is set to lenient mode.");

        if (options.checkData()) {
            final SeekableByteChannelFactory sbcf1 = expected.getChannelFactory();
            final SeekableByteChannelFactory sbcf2 = actual.getChannelFactory();
            diff(sbcf1, sbcf2, IbdoXmlElementNames.DATA, differences);
        }

        diff(expected.getFilename(), actual.getFilename(), IbdoXmlElementNames.FILENAME, differences);
        diff(expected.shortName(), actual.shortName(), SHORT_NAME, differences);
        if (options.checkInternalId()) {
            diff(expected.getInternalId(), actual.getInternalId(), INTERNAL_ID, differences);
        }
        diff(expected.currentForm(), actual.currentForm(), IbdoXmlElementNames.CURRENT_FORM, differences);
        diff(expected.getProcessingError(), actual.getProcessingError(), IbdoXmlElementNames.PROCESSING_ERROR, differences);

        if (options.checkTransformHistory()) {
            diff(expected.transformHistory(), actual.transformHistory(), TRANSFORM_HISTORY, differences);
        }

        diff(expected.getFontEncoding(), actual.getFontEncoding(), IbdoXmlElementNames.FONT_ENCODING, differences);

        if (options.performDetailedParameterDiff()) {
            diff(convertMap(expected.getParameters()), convertMap(actual.getParameters()), PARAMETER, differences);
        } else if (options.performKeyValueParameterDiff()) {
            keyValueMapDiff(convertMap(expected.getParameters()), convertMap(actual.getParameters()), PARAMETER, differences);
        } else {
            minimalMapDiff(convertMap(expected.getParameters()), convertMap(actual.getParameters()), PARAMETER, differences);
        }

        diff(expected.getNumChildren(), actual.getNumChildren(), IbdoXmlElementNames.NUM_CHILDREN, differences);
        diff(expected.getNumSiblings(), actual.getNumSiblings(), IbdoXmlElementNames.NUM_SIBLINGS, differences);
        diff(expected.getBirthOrder(), actual.getBirthOrder(), IbdoXmlElementNames.BIRTH_ORDER, differences);
        diff(expected.getAlternateViews(), actual.getAlternateViews(), IbdoXmlElementNames.VIEW, differences);
        diff(expected.header(), actual.header(), IbdoXmlElementNames.HEADER, differences);
        diff(expected.footer(), actual.footer(), IbdoXmlElementNames.FOOTER, differences);
        diff(expected.getHeaderEncoding(), actual.getHeaderEncoding(), IbdoXmlElementNames.HEADER_ENCODING, differences);
        diff(expected.getClassification(), actual.getClassification(), CLASSIFICATION, differences);
        diff(expected.isBroken(), actual.isBroken(), BROKEN, differences);
        diff(expected.isFileTypeEmpty(), actual.isFileTypeEmpty(), FILE_TYPE_EMPTY, differences);
        diff(expected.getPriority(), actual.getPriority(), IbdoXmlElementNames.PRIORITY, differences);
        if (options.checkTimestamp()) {
            diff(expected.getCreationTimestamp(), actual.getCreationTimestamp(), CREATION_TIMESTAMP, differences);
        }
        diff(expected.isOutputable(), actual.isOutputable(), IbdoXmlElementNames.OUTPUTABLE, differences);
        diff(expected.getId(), actual.getId(), IbdoXmlElementNames.ID, differences);
        diff(expected.getWorkBundleId(), actual.getWorkBundleId(), IbdoXmlElementNames.WORK_BUNDLE_ID, differences);
        diff(expected.getTransactionId(), actual.getTransactionId(), IbdoXmlElementNames.TRANSACTION_ID, differences);

        // Special case - pass through DiffCheckConfiguration options. This also ensures the right method is called (Object vs
        // List<IBDO>)
        diff(expected.getExtractedRecords(), actual.getExtractedRecords(), EXTRACTED_RECORDS, differences, options);
    }

    /**
     * This method compares an IBaseDataObject and against an answer file adds any differences to the provided string list.
     *
     * @param actual the IBaseDataObject to compare.
     * @param differences the string list of differences are to be added to.
     * @param options {@link DiffCheckConfiguration} containing config to specify whether to check data etc.
     */
    public static void diff(final IBaseDataObject actual, final List<String> differences, final DiffCheckConfiguration options) {
        Validate.notNull(actual, "Required: \"actual\" ibdo not null");
        Validate.notNull(differences, DIFF_NOT_NULL_MSG);
        Validate.notNull(options, "Required: options not null");
        Validate.isTrue(!options.isStrict(), "Cannot invoke single-payload lenient diff() when configuration is set to strict mode.");

        Element expectationElement = options.getLenientExpectationElement();
        if (expectationElement != null) {
            checkCurrentForms(expectationElement, actual, differences);
            checkFields(expectationElement, actual, differences);
            checkMetadata(expectationElement, actual, differences);
            checkViews(expectationElement, actual, differences);
            checkExtractedRecords(expectationElement, actual, differences, options);
        }
    }

    /**
     * This method compares an IBaseDataObject and against an answer file adds any differences to the provided string list.
     *
     * @param actualIbdo the IBaseDataObject to compare.
     * @param actualChildren the list of child IBaseDataObjects to compare.
     * @param identifier a string that helps identify the context of comparing these two list of IBaseDataObjects.
     * @param parentDifferences the string list of parent differences are to be added to.
     * @param childDifferences the string list of child differences are to be added to.
     * @param options {@link DiffCheckConfiguration} containing config to specify whether to check data etc.
     */
    public static void diff(final IBaseDataObject actualIbdo, final List<IBaseDataObject> actualChildren, final String identifier,
            final List<String> parentDifferences, final List<String> childDifferences, final DiffCheckConfiguration options) {
        checkAttachmentCounts(options.getLenientExpectationElement(), actualChildren, parentDifferences);
        diff(actualIbdo, parentDifferences, options);
        diff(actualChildren, identifier, childDifferences, options);
    }

    /**
     * This method compares two lists of IBaseDataObject's and adds any differences to the provided string list.
     *
     * @param expected the first list of IBaseDataObjects to compare.
     * @param actual the second list of IBaseDataObjects to compare.
     * @param identifier a string that helps identify the context of comparing these two list of IBaseDataObjects.
     * @param differences the string list differences are to be added to.
     * @param options {@link DiffCheckConfiguration} containing config to specify whether to check data etc.
     */
    public static void diff(final List<IBaseDataObject> expected, final List<IBaseDataObject> actual,
            final String identifier, final List<String> differences, final DiffCheckConfiguration options) {
        Validate.notNull(identifier, ID_NOT_NULL_MSG);
        Validate.notNull(differences, DIFF_NOT_NULL_MSG);
        Validate.notNull(options, "Required: options not null");
        Validate.isTrue(options.isStrict(), "Cannot invoke strict multi-list diff() when configuration is set to lenient mode.");

        final int expectedSize = (expected == null) ? 0 : expected.size();
        final int actualSize = (actual == null) ? 0 : actual.size();

        if (expectedSize != actualSize) {
            differences.add(String.format("%s list size mismatch -> Expected: %d, Actual: %d", identifier, expectedSize, actualSize));
        } else if (expected != null && actual != null) {
            final List<String> childDifferences = new ArrayList<>();
            for (int i = 0; i < expected.size(); i++) {
                childDifferences.clear();

                diff(expected.get(i), actual.get(i), childDifferences, options);

                final String prefix = String.format("%s[index %d] : ", identifier, i);
                while (!childDifferences.isEmpty()) {
                    differences.add(prefix + childDifferences.remove(0)); // NOSONAR Used correctly
                }
            }
        }
    }

    /**
     * This method compares a list of IBaseDataObjects and anwser file and adds any differences to the provided string list.
     *
     * @param actual the list of IBaseDataObjects to compare.
     * @param identifier a string that helps identify the context of comparing these two list of IBaseDataObjects.
     * @param differences the string list of differences are to be added to.
     * @param options {@link DiffCheckConfiguration} containing config to specify whether to check data etc.
     */
    public static void diff(final List<IBaseDataObject> actual, final String identifier,
            final List<String> differences, final DiffCheckConfiguration options) {
        Validate.notNull(identifier, ID_NOT_NULL_MSG);
        Validate.notNull(differences, DIFF_NOT_NULL_MSG);
        Validate.notNull(options, "Required: options not null");
        Validate.isTrue(!options.isStrict(), "Cannot invoke single-list lenient diff() when configuration is set to strict mode.");

        Element rootXml = options.getLenientExpectationElement();
        if (rootXml != null && actual != null) {
            final List<String> childDifferences = new ArrayList<>();
            for (int i = 0; i < actual.size(); i++) {
                childDifferences.clear();

                Element attel = getChildAnswers(rootXml, ATTACHMENT_ELEMENT_PREFIX, i + 1, differences);
                if (attel != null) {

                    diff(attel, actual.get(i), childDifferences, options);

                    final String prefix = String.format("%s[index %d] : ", identifier, i);
                    for (String diff : childDifferences) {
                        differences.add(prefix + diff);
                    }
                }
            }
        }
    }

    protected static void diff(Element attel, final IBaseDataObject actual, final List<String> differences, final DiffCheckConfiguration options) {
        DiffCheckConfiguration childOpts = DiffCheckConfiguration.from(options)
                .setLenientExpectationElement(attel)
                .build();

        checkAttachmentCounts(attel, null, differences);
        diff(actual, differences, childOpts);
    }

    /**
     * This method compares two {@link SeekableByteChannelFactory} (SBCF) objects and adds any differences to the provided
     * string list.
     *
     * @param expected the first SBCF to compare.
     * @param actual the second SBCF to compare.
     * @param identifier an identifier to describe the context of this SBCF comparison.
     * @param differences the string list differences are to be added to.
     */
    public static void diff(final SeekableByteChannelFactory expected, final SeekableByteChannelFactory actual,
            final String identifier, final List<String> differences) {
        Validate.notNull(identifier, ID_NOT_NULL_MSG);
        Validate.notNull(differences, DIFF_NOT_NULL_MSG);

        if (expected != null && actual != null) {
            try (SeekableByteChannel sbc1 = expected.create();
                    SeekableByteChannel sbc2 = actual.create();
                    InputStream is1 = Channels.newInputStream(sbc1);
                    InputStream is2 = Channels.newInputStream(sbc2)) {
                if (!IOUtils.contentEquals(is1, is2)) {
                    differences.add(String.format("%s content mismatch -> Expected size: %d, Actual size: %d",
                            identifier, sbc1.size(), sbc2.size()));
                }
            } catch (IOException e) {
                differences.add(String.format("IO Error comparing %s: %s", identifier, e.getMessage()));
            }
        } else if (expected == null && actual == null) {
            // Do nothing as they are considered equal.
        } else {
            differences.add(String.format("%s not equal. Expected SeekableByteChannelFactory=%s Actual SeekableByteChannelFactory=%s", identifier,
                    expected, actual));
        }
    }

    /**
     * This method compares two Objects and adds any differences to the provided string list.
     *
     * @param expected the first Object to compare.
     * @param actual the second Object to compare.
     * @param identifier an identifier to describe the context of this Object comparison.
     * @param differences the string list differences are to be added to.
     */
    public static void diff(final Object expected, final Object actual, final String identifier,
            final List<String> differences) {
        Validate.notNull(identifier, ID_NOT_NULL_MSG);
        Validate.notNull(differences, DIFF_NOT_NULL_MSG);

        if (!Objects.deepEquals(expected, actual)) {
            differences.add(String.format("%s%s -> Expected: %s, Actual: %s", identifier, ARE_NOT_EQUAL, expected, actual));
        }
    }

    /**
     * This method compares two integers and adds any differences to the provided string list.
     *
     * @param expected the first integer to compare.
     * @param actual the second integer to compare.
     * @param identifier an identifier to describe the context of this integer comparison.
     * @param differences the string list differences are to be added to.
     */
    public static void diff(final int expected, final int actual, final String identifier,
            final List<String> differences) {
        Validate.notNull(identifier, ID_NOT_NULL_MSG);
        Validate.notNull(differences, DIFF_NOT_NULL_MSG);

        if (expected != actual) {
            differences.add(String.format("%s value mismatch -> Expected: %d, Actual: %d", identifier, expected, actual));
        }
    }

    /**
     * This method compares two booleans and adds any differences to the provided string list.
     *
     * @param expected the first boolean to compare.
     * @param actual the second boolean to compare.
     * @param identifier an identifier to describe the context of this boolean comparison.
     * @param differences the string list differences are to be added to.
     */
    public static void diff(final boolean expected, final boolean actual, final String identifier,
            final List<String> differences) {
        Validate.notNull(identifier, ID_NOT_NULL_MSG);
        Validate.notNull(differences, DIFF_NOT_NULL_MSG);

        if (expected != actual) {
            differences.add(String.format("%s boolean mismatch -> Expected: %b, Actual: %b", identifier, expected, actual));
        }
    }

    /**
     * This method compares two maps and adds any differences to the provided string list.
     *
     * @param expected the first map to compare.
     * @param actual the second map to compare.
     * @param identifier an identifier to describe the context of this map comparison.
     * @param differences the string list of differences to be added to.
     */
    public static void diff(final Map<String, byte[]> expected, final Map<String, byte[]> actual, final String identifier,
            final List<String> differences) {
        Validate.notNull(expected, "Required: \"expected\" map not null!");
        Validate.notNull(actual, "Required: \"actual\" map not null!");
        Validate.notNull(identifier, ID_NOT_NULL_MSG);
        Validate.notNull(differences, DIFF_NOT_NULL_MSG);

        if (expected.size() != actual.size() ||
                !expected.entrySet().stream().allMatch(e -> Arrays.equals(e.getValue(), actual.get(e.getKey())))) {
            differences.add(identifier + ARE_NOT_EQUAL);
        }
    }

    /**
     * This method compares two maps and adds only the key/value pairs that differ to the provided string list.
     * 
     * @param expected the first map to compare.
     * @param actual the second map to compare.
     * @param identifier an identifier to describe the context of this map comparison.
     * @param differences the string list of differences to be added to.
     */
    public static void keyValueMapDiff(final Map<String, Collection<String>> expected, final Map<String, Collection<String>> actual,
            final String identifier, final List<String> differences) {
        Map<String, Collection<String>> missingInActual = new HashMap<>(expected);
        Map<String, Collection<String>> extraInActual = new HashMap<>(actual);
        Map<String, String> mismatchedValues = new HashMap<>();

        for (Entry<String, Collection<String>> entry : expected.entrySet()) {
            String key = entry.getKey();
            if (actual.containsKey(key)) {
                Collection<String> expectedColl = entry.getValue();
                Collection<String> actualColl = actual.get(key);

                boolean areEqual = (expectedColl == null && actualColl == null) ||
                        (expectedColl != null && actualColl != null &&
                                new ArrayList<>(expectedColl).equals(new ArrayList<>(actualColl)));

                if (!areEqual) {
                    mismatchedValues.put(key, String.format("Expected: %s, Actual: %s", expectedColl, actualColl));
                }

                missingInActual.remove(key);
                extraInActual.remove(key);
            }
        }

        if (!missingInActual.isEmpty() || !extraInActual.isEmpty() || !mismatchedValues.isEmpty()) {
            StringBuilder sb = new StringBuilder(identifier).append(" map differences:");
            if (!missingInActual.isEmpty()) {
                sb.append(" | Missing keys: ").append(missingInActual.keySet());
            }
            if (!extraInActual.isEmpty()) {
                sb.append(" | Unexpected keys: ").append(extraInActual.keySet());
            }
            if (!mismatchedValues.isEmpty()) {
                sb.append(" | Value mismatches: ").append(mismatchedValues);
            }
            differences.add(sb.toString());
        }
    }

    /**
     * This method compares two maps and adds only the keys that differ to the provided string list.
     * 
     * @param expected the first map to compare.
     * @param actual the second map to compare.
     * @param identifier an identifier to describe the context of this map comparison.
     * @param differences the string list of differences to be added to.
     */
    public static void minimalMapDiff(final Map<String, Collection<String>> expected, final Map<String, Collection<String>> actual,
            final String identifier, final List<String> differences) {
        final Set<Entry<String, Collection<String>>> expectedEntries = new HashSet<>(expected.entrySet());
        final Set<Entry<String, Collection<String>>> actualEntries = new HashSet<>(actual.entrySet());
        final Set<String> expectedKeys = new TreeSet<>(expected.keySet());
        final Set<String> actualKeys = new TreeSet<>(actual.keySet());

        for (Entry<String, Collection<String>> p1Entry : expectedEntries) {
            if (actualEntries.contains(p1Entry)) {
                expectedKeys.remove(p1Entry.getKey());
                actualKeys.remove(p1Entry.getKey());
            }
        }

        if (!expectedKeys.isEmpty() || !actualKeys.isEmpty()) {
            differences.add(String.format("%s key set mismatch -> Expected: %s, Actual: %s", identifier, expectedKeys, actualKeys));
        }
    }

    /**
     * This method converts the IBDO parameter map of Object values to a map of String values for better comparison.
     * 
     * @param map IBDO parameter map
     * @return a map that has only Strings as it values.
     */
    public static Map<String, Collection<String>> convertMap(final Map<String, Collection<Object>> map) {
        Map<String, Collection<String>> newMap = new TreeMap<>();

        for (Map.Entry<String, Collection<Object>> e : map.entrySet()) {
            final List<String> list = new ArrayList<>();

            for (Object o : e.getValue()) {
                list.add(String.valueOf(o));
            }

            newMap.put(e.getKey(), list);
        }

        return newMap;
    }

    protected static Element getChildAnswers(Element parent, String name, int index, List<String> differences) {
        Element el = parent.getChildren().stream()
                .filter(c -> c.getName().equalsIgnoreCase(name) && String.valueOf(index).equals(c.getAttributeValue(INDEX))
                        && verifyOs(c, differences))
                .findFirst()
                .orElse(null);
        if (el == null) {
            el = parent.getChildren().stream()
                    .filter(c -> c.getName().equalsIgnoreCase(name + index) && verifyOs(c, differences))
                    .findFirst()
                    .orElse(null);
        }
        return el;
    }

    protected static void checkAttachmentCounts(Element el, @Nullable List<IBaseDataObject> attachments, List<String> differences) {
        int payloadSize = attachments != null ? attachments.size() : 0;
        Element numAttEl = null;
        long numAttElements = 0;

        for (Element child : el.getChildren()) {
            if (verifyOs(child, differences)) {
                String name = child.getName();
                if (name.equals(NUM_ATTACHMENTS) && numAttEl == null) {
                    numAttEl = child;
                } else if (name.startsWith(ATTACHMENT_ELEMENT_PREFIX)) {
                    numAttElements++;
                }
            }
        }

        if (numAttEl != null) {
            int numAtt = NumberUtils.toInt(numAttEl.getValue(), -1);
            if (numAtt != payloadSize) {
                differences
                        .add(String.format("Expected <numAttachments> %d not equal to number of attachments in payload (%d).", numAtt, payloadSize));
            }
        } else if (numAttElements > 0) {
            if (numAttElements != payloadSize) {
                differences.add(
                        String.format("Expected <att#> count %d not equal to number of attachments in payload (%d).", numAttElements, payloadSize));
            }
        } else if (payloadSize > 0) {
            differences.add(String.format("%d attachments in payload with no count in answer xml. Add matching <numAttachments>", payloadSize));
        }
    }

    protected static void checkCurrentForms(Element el, IBaseDataObject payload, List<String> differences) {
        for (Element currentForm : el.getChildren(CURRENT_FORM)) {
            if (verifyOs(currentForm, differences)) {
                String cf = currentForm.getTextTrim();
                if (cf != null) {
                    Attribute index = currentForm.getAttribute(INDEX);
                    if (index != null) {
                        try {
                            int idxVal = index.getIntValue();
                            String actualForm = payload.currentFormAt(idxVal);
                            if (!cf.equals(actualForm)) {
                                differences.add(String.format("Current form '%s' not found at position [%d]. Actual: %s, All: %s", cf, idxVal,
                                        actualForm, payload.getAllCurrentForms()));
                            }
                        } catch (Exception e) {
                            differences.add("Invalid index integer value parsing currentForm rule: " + index.getValue());
                        }
                    } else if (payload.searchCurrentForm(cf) <= -1) {
                        differences.add(String.format("Current form '%s' not found in payload forms: %s", cf, payload.getAllCurrentForms()));
                    }
                }
            }
        }
    }

    protected static void checkFields(Element el, IBaseDataObject payload, List<String> differences) {
        for (Element child : el.getChildren(FILE_TYPE)) {
            if (verifyOs(child, differences)) {
                diff(child.getTextTrim(), payload.getFileType(), "Expected File Type", differences);
            }
        }
        for (Element child : el.getChildren(CLASSIFICATION)) {
            if (verifyOs(child, differences)) {
                diff(child.getTextTrim(), payload.getClassification(), "Classification mismatch", differences);
            }
        }
        for (Element child : el.getChildren(SHORT_NAME)) {
            if (verifyOs(child, differences)) {
                diff(child.getTextTrim(), payload.shortName(), "Shortname", differences);
            }
        }
        for (Element child : el.getChildren(FONT_ENCODING)) {
            if (verifyOs(child, differences)) {
                diff(child.getTextTrim(), payload.getFontEncoding(), "Font encoding", differences);
            }
        }
        for (Element child : el.getChildren(BROKEN)) {
            if (verifyOs(child, differences)) {
                diff(child.getTextTrim(), Boolean.toString(payload.isBroken()), "Broken status", differences);
            }
        }

        for (Element child : el.getChildren("currentFormSize")) {
            if (verifyOs(child, differences)) {
                diff(NumberUtils.toInt(child.getValue(), -1), payload.currentFormSize(), "Current form size", differences);
            }
        }
        for (Element child : el.getChildren("dataLength")) {
            if (verifyOs(child, differences)) {
                diff(NumberUtils.toInt(child.getValue(), -1), payload.dataLength(), "Data length", differences);
            }
        }

        for (Element child : el.getChildren("procError")) {
            if (verifyOs(child, differences)) {
                String expectedError = child.getTextTrim();
                if (StringUtils.isNotBlank(expectedError)) {
                    String actualError = payload.getProcessingError();
                    if (actualError == null) {
                        differences.add(String.format("Expected processing error '%s', but got null", expectedError));
                    } else {
                        String normalizedActual = actualError.replace("\n", ";");
                        diff(expectedError, normalizedActual, "Processing Error mismatch", differences);
                    }
                }
            }
        }
    }

    protected static void checkMetadata(Element el, IBaseDataObject payload, List<String> differences) {
        for (Element meta : el.getChildren(PARAMETER)) {
            if (verifyOs(meta, differences)) {
                String key = meta.getChildTextTrim(NAME);
                if (key == null) {
                    differences.add(String.format("The element %s missing a child name element", PARAMETER));
                } else {
                    checkStringValue(meta, payload.getStringParameter(key), differences);
                }
            }
        }

        for (Element meta : el.getChildren(NOMETA)) {
            if (verifyOs(meta, differences)) {
                String key = meta.getChildTextTrim(NAME);
                if (key == null) {
                    differences.add(String.format("The element %s missing a child name element", NOMETA));
                } else if (payload.hasParameter(key)) {
                    differences.add(
                            String.format("Metadata element '%s' should not exist, but has value of '%s'", key, payload.getStringParameter(key)));
                }
            }
        }
    }

    protected static void checkViews(Element el, IBaseDataObject payload, List<String> differences) {
        List<Element> dataElements = el.getChildren(DATA);
        if (!dataElements.isEmpty()) {
            String primaryDataStr = payload.data() != null ? new String(payload.data(), StandardCharsets.UTF_8) : "";
            for (Element dataEl : dataElements) {
                if (verifyOs(dataEl, differences)) {
                    int length = NumberUtils.toInt(dataEl.getChildTextTrim("length"), -1);
                    if (length > -1 && length != payload.dataLength()) {
                        differences.add(String.format("Data length mismatch -> Expected: %d, Actual: %d", length, payload.dataLength()));
                    }
                    checkStringValue(dataEl, primaryDataStr, differences);
                }
            }
        }

        for (Element view : el.getChildren(VIEW)) {
            if (verifyOs(view, differences)) {
                String viewName = view.getChildTextTrim(NAME);
                byte[] viewData = payload.getAlternateView(viewName);
                if (viewData == null) {
                    differences.add(String.format("Alternate View '%s' is missing from payload", viewName));
                } else {
                    String lengthStr = view.getChildTextTrim("length");
                    if (lengthStr != null && Integer.parseInt(lengthStr) != viewData.length) {
                        differences.add(String.format("Length of Alternate View '%s' mismatch -> Expected: %s, Actual: %d", viewName, lengthStr,
                                viewData.length));
                    }
                    checkStringValue(view, new String(viewData, StandardCharsets.UTF_8), differences);
                }
            }
        }

        for (Element view : el.getChildren(NOVIEW)) {
            if (verifyOs(view, differences)) {
                String viewName = view.getChildTextTrim(NAME);
                if (payload.getAlternateView(viewName) != null) {
                    differences.add(String.format("Alternate View '%s' is present, but was flagged as forbidden (noview)", viewName));
                }
            }
        }
    }

    protected static void checkExtractedRecords(Element el, IBaseDataObject payload, List<String> differences, final DiffCheckConfiguration options) {
        List<IBaseDataObject> extractedChildren = payload.hasExtractedRecords()
                ? Objects.requireNonNullElse(payload.getExtractedRecords(), List.of())
                : List.of();
        int payloadSize = extractedChildren.size();

        List<Element> validChildren = el.getChildren().stream()
                .filter(child -> verifyOs(child, differences))
                .collect(Collectors.toList());

        Optional<Element> extractCountEl = validChildren.stream()
                .filter(child -> EXTRACT_COUNT.equals(child.getName()))
                .findFirst();

        long numExtractElements = validChildren.stream()
                .filter(child -> child.getName().startsWith(EXTRACTED_RECORD_ELEMENT_PREFIX))
                .count();

        validateExtractCounts(extractCountEl.orElse(null), numExtractElements, payloadSize, differences);

        for (int i = 0; i < payloadSize; i++) {
            int extNum = i + 1;
            Element extel = getChildAnswers(el, EXTRACTED_RECORD_ELEMENT_PREFIX, extNum, differences);
            if (extel == null) {
                continue;
            }

            List<String> childDiffs = new ArrayList<>();
            diff(extel, extractedChildren.get(i), childDiffs, options);

            String prefix = String.format("extract%d :: ", extNum);
            childDiffs.stream()
                    .map(diff -> prefix + diff)
                    .forEach(differences::add);
        }
    }

    protected static void validateExtractCounts(@Nullable Element extractCountEl, long numExtractElements, int payloadSize,
            List<String> differences) {
        int extractCount = extractCountEl != null ? NumberUtils.toInt(extractCountEl.getValue(), -1) : -1;
        if (extractCount > -1) {
            if (extractCount != payloadSize) {
                differences
                        .add(String.format("Expected <extractCount> %d not equal to number of extracts in payload (%d).", extractCount, payloadSize));
            }
        } else if (numExtractElements > 0) {
            if (numExtractElements != payloadSize) {
                differences.add(String.format("Expected <extract#> count %d not equal to number of extracts in payload (%d).", numExtractElements,
                        payloadSize));
            }
        } else if (payloadSize > 0) {
            differences.add(String.format("%d extracts in payload with no count in answer xml. Add matching <extractCount>", payloadSize));
        }
    }

    protected static void checkStringValue(Element meta, String data, List<String> differences) {
        Element valueElement = meta.getChild(VALUE);
        String value = (valueElement != null) ? valueElement.getText() : null;
        if (value == null || "null".equalsIgnoreCase(value)) {
            return;
        }

        String encoding = valueElement.getAttributeValue(ENCODING_ATTRIBUTE_NAME);
        String matchMode = (encoding != null && !encoding.isEmpty())
                ? encoding
                : meta.getAttributeValue("matchMode", "equals");
        String key = meta.getChildTextTrim(NAME);

        String truncatedData = truncate(data);

        switch (matchMode.toLowerCase(Locale.getDefault())) {
            case "equals":
                if (!Objects.equals(value, data)) {
                    differences.add(formatErr(meta, key, "does not equal", truncatedData, truncate(value)));
                }
                break;
            case "index":
            case "contains":
                if (data == null || !data.contains(value)) {
                    differences.add(formatErr(meta, key, "does not contain", truncatedData, truncate(value)));
                }
                break;
            case "!index":
            case "!contains":
                if (data != null && data.contains(value)) {
                    differences.add(formatErr(meta, key, "should not contain", truncatedData, truncate(value)));
                }
                break;
            case "match":
                if (data == null || !data.matches(value)) {
                    differences.add(formatErr(meta, key, "does not match regex", truncatedData, truncate(value)));
                }
                break;
            case BASE64:
                try {
                    value = new String(BASE64_DECODER.decode(value));
                    if (!Objects.equals(value, data)) {
                        differences.add(formatErr(meta, key, "Base64 mismatch", truncatedData, truncate(value)));
                    }
                } catch (RuntimeException e) {
                    differences.add(String.format("%s element '%s': Base64 decoding failed.", meta.getName(), key));
                }
                break;
            case "collection":
                handleCollectionMatch(meta, data, value, key, differences);
                break;
            default:
                differences.add(String.format("Problematic matchMode '%s' for test '%s' in %s", matchMode, key, meta.getName()));
                break;
        }
    }

    protected static void handleCollectionMatch(Element meta, String data, String value, String key, List<String> differences) {
        Attribute sepAttr = meta.getAttribute("collectionSeparator");
        String separator = (sepAttr != null) ? sepAttr.getValue() : ",";

        List<String> expectedValues = Arrays.asList((value != null ? value : "").split(separator));
        List<String> actualValues = Arrays.asList((data != null ? data : "").split(separator));

        if (!CollectionUtils.isEqualCollection(expectedValues, actualValues)) {
            differences.add(String.format("%s element '%s': collections not equal. Sep: '%s' | Expected: %s, Actual: %s",
                    meta.getName(), key, separator, expectedValues, actualValues));
        }
    }

    protected static boolean verifyOs(Element element, List<String> differences) {
        Attribute specifiedOs = element.getAttribute("os-release");
        Attribute specifiedVersion = element.getAttribute("os-version");

        if (specifiedOs == null) {
            // No OS restriction, safe to run
            return true;
        }

        String os = specifiedOs.getValue().toLowerCase(Locale.getDefault());
        boolean isMatchingOs;
        switch (os) {
            case "ubuntu":
                isMatchingOs = OSReleaseUtil.isUbuntu();
                break;
            case "centos":
                isMatchingOs = OSReleaseUtil.isCentOs();
                break;
            case "rhel":
                isMatchingOs = OSReleaseUtil.isRhel();
                break;
            case "mac":
                isMatchingOs = OSReleaseUtil.isMac();
                break;
            default:
                differences.add(String.format("Unsupported or mistyped os-release target '%s' found in element <%s>",
                        specifiedOs.getValue(), element.getName()));
                return false;
        }

        if (!isMatchingOs) {
            return false;
        }

        // Check version match if specified
        if (specifiedVersion != null) {
            return specifiedVersion.getValue().equals(OSReleaseUtil.getMajorReleaseVersion());
        }

        return true;
    }

    protected static String formatErr(Element meta, String key, String reason, String actual, String expected) {
        return String.format("%s element '%s' problem: '%s' %s '%s'", meta.getName(), key, actual, reason, expected);
    }

    protected static String truncate(String input) {
        return truncate(input, 150);
    }

    protected static String truncate(String input, int maxLength) {
        if (input == null || input.length() <= maxLength) {
            return input;
        }
        return input.substring(0, maxLength);
    }

}
