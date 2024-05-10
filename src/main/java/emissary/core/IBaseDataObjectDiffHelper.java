package emissary.core;

import emissary.core.channels.SeekableByteChannelFactory;
import emissary.core.constants.IbdoXmlElementNames;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class IBaseDataObjectDiffHelper {
    private static final String DIFF_NOT_NULL_MSG = "Required: differences not null";
    private static final String ID_NOT_NULL_MSG = "Required: identifier not null";
    private static final String ARE_NOT_EQUAL = " elements are not equal";
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
     * @param ibdo1 the first IBaseDataObject to compare.
     * @param ibdo2 the second IBaseDataObject to compare.
     * @param differences the string list differences are to be added to.
     * @param options {@link DiffCheckConfiguration} containing config to specify whether to check data etc.
     */
    public static void diff(final IBaseDataObject ibdo1, final IBaseDataObject ibdo2,
            final List<String> differences, final DiffCheckConfiguration options) {
        Validate.notNull(ibdo1, "Required: ibdo1 not null");
        Validate.notNull(ibdo2, "Required: ibdo2 not null");
        Validate.notNull(differences, DIFF_NOT_NULL_MSG);

        if (options.checkData()) {
            final SeekableByteChannelFactory sbcf1 = ibdo1.getChannelFactory();
            final SeekableByteChannelFactory sbcf2 = ibdo2.getChannelFactory();
            diff(sbcf1, sbcf2, IbdoXmlElementNames.DATA, differences);
        }

        diff(ibdo1.getFilename(), ibdo2.getFilename(), IbdoXmlElementNames.FILENAME, differences);
        diff(ibdo1.shortName(), ibdo2.shortName(), SHORT_NAME, differences);
        if (options.checkInternalId()) {
            diff(ibdo1.getInternalId(), ibdo2.getInternalId(), INTERNAL_ID, differences);
        }
        diff(ibdo1.currentForm(), ibdo2.currentForm(), IbdoXmlElementNames.CURRENT_FORM, differences);
        diff(ibdo1.getProcessingError(), ibdo2.getProcessingError(), IbdoXmlElementNames.PROCESSING_ERROR, differences);

        if (options.checkTransformHistory()) {
            diff(ibdo1.transformHistory(), ibdo2.transformHistory(), TRANSFORM_HISTORY, differences);
        }

        diff(ibdo1.getFontEncoding(), ibdo2.getFontEncoding(), IbdoXmlElementNames.FONT_ENCODING, differences);

        if (options.performDetailedParameterDiff()) {
            diff(convertMap(ibdo1.getParameters()), convertMap(ibdo2.getParameters()), IbdoXmlElementNames.PARAMETER, differences);
        } else if (options.performKeyValueParameterDiff()) {
            keyValueMapDiff(convertMap(ibdo1.getParameters()), convertMap(ibdo2.getParameters()), IbdoXmlElementNames.PARAMETER, differences);
        } else {
            minimalMapDiff(convertMap(ibdo1.getParameters()), convertMap(ibdo2.getParameters()), IbdoXmlElementNames.PARAMETER, differences);
        }

        diff(ibdo1.getNumChildren(), ibdo2.getNumChildren(), IbdoXmlElementNames.NUM_CHILDREN, differences);
        diff(ibdo1.getNumSiblings(), ibdo2.getNumSiblings(), IbdoXmlElementNames.NUM_SIBLINGS, differences);
        diff(ibdo1.getBirthOrder(), ibdo2.getBirthOrder(), IbdoXmlElementNames.BIRTH_ORDER, differences);
        diff(ibdo1.getAlternateViews(), ibdo2.getAlternateViews(), IbdoXmlElementNames.VIEW, differences);
        diff(ibdo1.header(), ibdo2.header(), IbdoXmlElementNames.HEADER, differences);
        diff(ibdo1.footer(), ibdo2.footer(), IbdoXmlElementNames.FOOTER, differences);
        diff(ibdo1.getHeaderEncoding(), ibdo2.getHeaderEncoding(), IbdoXmlElementNames.HEADER_ENCODING, differences);
        diff(ibdo1.getClassification(), ibdo2.getClassification(), IbdoXmlElementNames.CLASSIFICATION, differences);
        diff(ibdo1.isBroken(), ibdo2.isBroken(), IbdoXmlElementNames.BROKEN, differences);
        diff(ibdo1.isFileTypeEmpty(), ibdo2.isFileTypeEmpty(), FILE_TYPE_EMPTY, differences);
        diff(ibdo1.getPriority(), ibdo2.getPriority(), IbdoXmlElementNames.PRIORITY, differences);
        if (options.checkTimestamp()) {
            diff(ibdo1.getCreationTimestamp(), ibdo2.getCreationTimestamp(), CREATION_TIMESTAMP, differences);
        }
        diff(ibdo1.isOutputable(), ibdo2.isOutputable(), IbdoXmlElementNames.OUTPUTABLE, differences);
        diff(ibdo1.getId(), ibdo2.getId(), IbdoXmlElementNames.ID, differences);
        diff(ibdo1.getWorkBundleId(), ibdo2.getWorkBundleId(), IbdoXmlElementNames.WORK_BUNDLE_ID, differences);
        diff(ibdo1.getTransactionId(), ibdo2.getTransactionId(), IbdoXmlElementNames.TRANSACTION_ID, differences);

        // Special case - pass through DiffCheckConfiguration options. This also ensures the right method is called (Object vs
        // List<IBDO>)
        diff(ibdo1.getExtractedRecords(), ibdo2.getExtractedRecords(), EXTRACTED_RECORDS, differences, options);
    }

    /**
     * This method compares two lists of IBaseDataObject's and adds any differences to the provided string list.
     *
     * @param ibdoList1 the first list of IBaseDataObjects to compare.
     * @param ibdoList2 the second list of IBaseDataObjects to compare.
     * @param identifier a string that helps identify the context of comparing these two list of IBaseDataObjects.
     * @param differences the string list differences are to be added to.
     * @param options {@link DiffCheckConfiguration} containing config to specify whether to check data etc.
     */
    public static void diff(final List<IBaseDataObject> ibdoList1, final List<IBaseDataObject> ibdoList2,
            final String identifier, final List<String> differences, final DiffCheckConfiguration options) {
        Validate.notNull(identifier, ID_NOT_NULL_MSG);
        Validate.notNull(differences, DIFF_NOT_NULL_MSG);

        final int ibdoList1Size = (ibdoList1 == null) ? 0 : ibdoList1.size();
        final int ibdoList2Size = (ibdoList2 == null) ? 0 : ibdoList2.size();

        if (ibdoList1Size != ibdoList2Size) {
            differences.add(String.format("%s%s%sIBaseDataObject List Size 1: %s%sIBaseDataObject List Size 2: %s",
                    identifier, ARE_NOT_EQUAL, StringUtils.LF, ibdoList1Size, StringUtils.LF, ibdoList2Size));
        } else if (ibdoList1 != null && ibdoList2 != null) {
            final List<String> childDifferences = new ArrayList<>();
            for (int i = 0; i < ibdoList1.size(); i++) {
                childDifferences.clear();

                diff(ibdoList1.get(i), ibdoList2.get(i), childDifferences, options);

                final String prefix = identifier + " : " + i + " : ";
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
     * @param sbcf1 the first SBCF to compare.
     * @param sbcf2 the second SBCF to compare.
     * @param identifier an identifier to describe the context of this SBCF comparison.
     * @param differences the string list differences are to be added to.
     */
    public static void diff(final SeekableByteChannelFactory sbcf1, final SeekableByteChannelFactory sbcf2,
            final String identifier, final List<String> differences) {
        Validate.notNull(identifier, ID_NOT_NULL_MSG);
        Validate.notNull(differences, DIFF_NOT_NULL_MSG);

        if (!(sbcf1 == null && sbcf2 == null)) {
            final String sbcfName = "SeekableByteChannelFactory";
            try {
                if (sbcf1 == null)
                    differences.add(diffNotEqStr(identifier, sbcfName,
                            null, IOUtils.toString(Channels.newInputStream(sbcf2.create()), StandardCharsets.UTF_8)));
                else if (sbcf2 == null)
                    differences.add(diffNotEqStr(identifier, sbcfName,
                            IOUtils.toString(Channels.newInputStream(sbcf1.create()), StandardCharsets.UTF_8), null));
                else {
                    SeekableByteChannel sbc1 = sbcf1.create();
                    SeekableByteChannel sbc2 = sbcf2.create();
                    InputStream is1 = Channels.newInputStream(sbc1);
                    InputStream is2 = Channels.newInputStream(sbc2);
                    if (!IOUtils.contentEquals(is1, is2)) {
                        differences.add(diffNotEqStr(identifier, sbcfName,
                                IOUtils.toString(Channels.newInputStream(sbcf1.create()), StandardCharsets.UTF_8),
                                IOUtils.toString(Channels.newInputStream(sbcf2.create()), StandardCharsets.UTF_8)));
                    }
                }
            } catch (IOException e) {
                differences.add(String.format("Failed to compare %s: %s", identifier, e.getMessage()));
            }
        } // if both null, do nothing as they're considered equal

    }

    /**
     * This method compares two Objects and adds any differences to the provided string list.
     *
     * @param object1 the first Object to compare.
     * @param object2 the second Object to compare.
     * @param identifier an identifier to describe the context of this Object comparison.
     * @param differences the string list differences are to be added to.
     */
    public static void diff(final Object object1, final Object object2, final String identifier,
            final List<String> differences) {
        Validate.notNull(identifier, ID_NOT_NULL_MSG);
        Validate.notNull(differences, DIFF_NOT_NULL_MSG);

        if (!Objects.deepEquals(object1, object2)) {
            differences.add(diffNotEqStr(identifier, "Object", object1, object2));
        }
    }

    /**
     * This method compares two integers and adds any differences to the provided string list.
     *
     * @param integer1 the first integer to compare.
     * @param integer2 the second integer to compare.
     * @param identifier an identifier to describe the context of this integer comparison.
     * @param differences the string list differences are to be added to.
     */
    public static void diff(final int integer1, final int integer2, final String identifier,
            final List<String> differences) {
        Validate.notNull(identifier, ID_NOT_NULL_MSG);
        Validate.notNull(differences, DIFF_NOT_NULL_MSG);

        if (integer1 != integer2) {
            differences.add(diffNotEqStr(identifier, "Integer", integer1, integer2));
        }
    }

    /**
     * This method compares two booleans and adds any differences to the provided string list.
     *
     * @param boolean1 the first boolean to compare.
     * @param boolean2 the second boolean to compare.
     * @param identifier an identifier to describe the context of this boolean comparison.
     * @param differences the string list differences are to be added to.
     */
    public static void diff(final boolean boolean1, final boolean boolean2, final String identifier,
            final List<String> differences) {
        Validate.notNull(identifier, ID_NOT_NULL_MSG);
        Validate.notNull(differences, DIFF_NOT_NULL_MSG);

        if (boolean1 != boolean2) {
            differences.add(diffNotEqStr(identifier, "Boolean", boolean1, boolean2));
        }
    }

    /**
     * This method compares two maps and adds any differences to the provided string list.
     *
     * @param map1 the first map to compare.
     * @param map2 the second map to compare.
     * @param identifier an identifier to describe the context of this map comparison.
     * @param differences the string list of differences to be added to.
     */
    public static void diff(final Map<String, byte[]> map1, final Map<String, byte[]> map2, final String identifier,
            final List<String> differences) {
        Validate.notNull(map1, "Required: map1 not null!");
        Validate.notNull(map2, "Required: map2 not null!");
        Validate.notNull(identifier, ID_NOT_NULL_MSG);
        Validate.notNull(differences, DIFF_NOT_NULL_MSG);

        if (map1.size() != map2.size() ||
                !map1.entrySet().stream().allMatch(e -> Arrays.equals(e.getValue(), map2.get(e.getKey())))) {
            differences.add(identifier + ARE_NOT_EQUAL);
        }
    }

    /**
     * This method compares two maps and adds only the key/value pairs that differ to the provided string list.
     * 
     * @param parameter1 the first map to compare.
     * @param parameter2 the second map to compare.
     * @param identifier an identifier to describe the context of this map comparison.
     * @param differences the string list of differences to be added to.
     */
    public static void keyValueMapDiff(final Map<String, Collection<String>> parameter1, final Map<String, Collection<String>> parameter2,
            final String identifier, final List<String> differences) {
        final Set<Entry<String, Collection<String>>> p1Entries = new HashSet<>(parameter1.entrySet());
        final Set<Entry<String, Collection<String>>> p2Entries = new HashSet<>(parameter2.entrySet());
        final Map<String, Collection<String>> p1 = new HashMap<>(parameter1);
        final Map<String, Collection<String>> p2 = new HashMap<>(parameter2);

        for (Entry<String, Collection<String>> p1Entry : p1Entries) {
            if (p2Entries.contains(p1Entry)) {
                p1.remove(p1Entry.getKey());
                p2.remove(p1Entry.getKey());
            }
        }

        if (!p1.isEmpty() || !p2.isEmpty()) {
            differences.add(diffNotEqStr(identifier, "Key/Value Set", p1, p2));
        }
    }

    /**
     * This method compares two maps and adds only the keys that differ to the provided string list.
     * 
     * @param parameter1 the first map to compare.
     * @param parameter2 the second map to compare.
     * @param identifier an identifier to describe the context of this map comparison.
     * @param differences the string list of differences to be added to.
     */
    public static void minimalMapDiff(final Map<String, Collection<String>> parameter1, final Map<String, Collection<String>> parameter2,
            final String identifier, final List<String> differences) {
        final Set<Entry<String, Collection<String>>> p1Entries = new HashSet<>(parameter1.entrySet());
        final Set<Entry<String, Collection<String>>> p2Entries = new HashSet<>(parameter2.entrySet());
        final Set<String> p1Keys = new TreeSet<>(parameter1.keySet());
        final Set<String> p2Keys = new TreeSet<>(parameter2.keySet());

        for (Entry<String, Collection<String>> p1Entry : p1Entries) {
            if (p2Entries.contains(p1Entry)) {
                p1Keys.remove(p1Entry.getKey());
                p2Keys.remove(p1Entry.getKey());
            }
        }

        if (!p1Keys.isEmpty() || !p2Keys.isEmpty()) {
            differences.add(diffNotEqStr(identifier, "Minimal Map Key Set", p1Keys, p2Keys));
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
                list.add(o.toString());
            }

            newMap.put(e.getKey(), list);
        }

        return newMap;
    }

    public static String diffNotEqStr(String id, String objName, Object comparand1, Object comparand2) {
        String comp1Msg = String.format("%s 1: %s%s", objName, comparand1, StringUtils.LF);
        String comp2Msg = String.format("%s 2: %s%s", objName, comparand2, StringUtils.LF);
        return id + ARE_NOT_EQUAL + StringUtils.LF + comp1Msg + comp2Msg;
    }
}
