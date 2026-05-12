package emissary.core;

import emissary.core.channels.SeekableByteChannelFactory;
import emissary.core.constants.IbdoXmlElementNames;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static emissary.core.constants.IbdoXmlElementNames.BROKEN;
import static emissary.core.constants.IbdoXmlElementNames.CLASSIFICATION;
import static emissary.core.constants.IbdoXmlElementNames.PARAMETER;

public class IBaseDataObjectDiffHelper {
    private static final String DIFF_NOT_NULL_MSG = "Required: differences not null";
    private static final String ID_NOT_NULL_MSG = "Required: identifier not null";
    private static final String ARE_NOT_EQUAL = " are not equal";
    private static final String SHORT_NAME = "shortName";
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
    public static void diff(final IBaseDataObject expected, final IBaseDataObject actual,
            final List<String> differences, final DiffCheckConfiguration options) {
        Validate.notNull(expected, "Required: \"expected\" ibdo not null");
        Validate.notNull(actual, "Required: \"actual\" ibdo not null");
        Validate.notNull(differences, DIFF_NOT_NULL_MSG);

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
}
