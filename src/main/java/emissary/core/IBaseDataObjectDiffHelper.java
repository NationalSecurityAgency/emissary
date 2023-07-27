package emissary.core;

import emissary.core.channels.SeekableByteChannelFactory;

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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

public class IBaseDataObjectDiffHelper {

    private static final String DIFF_NOT_NULL_MSG = "Required: differences not null";
    private static final String ID_NOT_NULL_MSG = "Required: identifier not null";
    private static final String ARE_NOT_EQUAL = " are not equal";

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
            diff(sbcf1, sbcf2, "sbcf", differences);
        }

        diff(ibdo1.getFilename(), ibdo2.getFilename(), "filename", differences);
        diff(ibdo1.shortName(), ibdo2.shortName(), "shortName", differences);
        if (options.checkInternalId()) {
            diff(ibdo1.getInternalId(), ibdo2.getInternalId(), "internalId", differences);
        }
        diff(ibdo1.currentForm(), ibdo2.currentForm(), "currentForm", differences);
        diff(ibdo1.getProcessingError(), ibdo2.getProcessingError(), "processingError", differences);

        if (options.checkTransformHistory()) {
            diff(ibdo1.transformHistory(), ibdo2.transformHistory(), "transformHistory", differences);
        }

        diff(ibdo1.getFontEncoding(), ibdo2.getFontEncoding(), "fontEncoding", differences);
        diff(sortParameters(ibdo1.getParameters()), sortParameters(ibdo2.getParameters()), "parameters", differences);
        diff(ibdo1.getNumChildren(), ibdo2.getNumChildren(), "numChildren", differences);
        diff(ibdo1.getNumSiblings(), ibdo2.getNumSiblings(), "numSiblings", differences);
        diff(ibdo1.getBirthOrder(), ibdo2.getBirthOrder(), "birthOrder", differences);
        diff(ibdo1.getAlternateViews(), ibdo2.getAlternateViews(), "alternateViews", differences);
        diff(ibdo1.header(), ibdo2.header(), "header", differences);
        diff(ibdo1.footer(), ibdo2.footer(), "footer", differences);
        diff(ibdo1.getHeaderEncoding(), ibdo2.getHeaderEncoding(), "headerEncoding", differences);
        diff(ibdo1.getClassification(), ibdo2.getClassification(), "classification", differences);
        diff(ibdo1.isBroken(), ibdo2.isBroken(), "broken", differences);
        diff(ibdo1.isFileTypeEmpty(), ibdo2.isFileTypeEmpty(), "fileTypeEmpty", differences);
        diff(ibdo1.getPriority(), ibdo2.getPriority(), "priority", differences);
        if (options.checkTimestamp()) {
            diff(ibdo1.getCreationTimestamp(), ibdo2.getCreationTimestamp(), "creationTimestamp", differences);
        }
        diff(ibdo1.isOutputable(), ibdo2.isOutputable(), "outputable", differences);
        diff(ibdo1.getId(), ibdo2.getId(), "id", differences);
        diff(ibdo1.getWorkBundleId(), ibdo2.getWorkBundleId(), "workBundleId", differences);
        diff(ibdo1.getTransactionId(), ibdo2.getTransactionId(), "transactionId", differences);

        // Special case - pass through DiffCheckConfiguration options. This also ensures the right method is called (Object vs
        // List<IBDO>)
        diff(ibdo1.getExtractedRecords(), ibdo2.getExtractedRecords(), "extractedRecords", differences, options);
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
            differences.add(String.format("%s%s: 1.s=%s 2.s=%s", identifier, ARE_NOT_EQUAL, ibdoList1Size, ibdoList2Size));
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

        if (sbcf1 != null && sbcf2 != null) {
            try (SeekableByteChannel sbc1 = sbcf1.create();
                    SeekableByteChannel sbc2 = sbcf2.create();
                    InputStream is1 = Channels.newInputStream(sbc1);
                    InputStream is2 = Channels.newInputStream(sbc2)) {
                if (!IOUtils.contentEquals(is1, is2)) {
                    differences.add(String.format("%s not equal. 1.cs=%s 2.cs=%s",
                            identifier, sbc1.size(), sbc2.size()));
                }
            } catch (IOException e) {
                differences.add(String.format("Failed to compare %s: %s", identifier, e.getMessage()));
            }
        } else if (sbcf1 == null && sbcf2 == null) {
            // Do nothing as they are considered equal.
        } else {
            differences.add(String.format("%s not equal. sbcf1=%s sbcf2=%s", identifier, sbcf1, sbcf2));
        }
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
            differences.add(String.format("%s%s: %s : %s", identifier, ARE_NOT_EQUAL, object1, object2));
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
            differences.add(identifier + ARE_NOT_EQUAL);
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
            differences.add(identifier + ARE_NOT_EQUAL);
        }
    }

    /**
     * This method compares two maps and adds any differences to the provided string list.
     *
     * @param map1 the first map to compare.
     * @param map2 the second map to compare.
     * @param identifier an identifier to describe the context of this map comparison.
     * @param differences the string list differences are to be added to.
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
     * This method is used to sort the given map of parameters by key.
     *
     * @param parameters the map of parameters from IBaseDataObject to sort
     * @return map of sorted parameters
     */
    public static Map<String, Collection<Object>> sortParameters(Map<String, Collection<Object>> parameters) {
        List<Entry<String, Collection<Object>>> list = new ArrayList<>(parameters.entrySet());
        list.sort(Entry.comparingByKey());

        Map<String, Collection<Object>> sortedParams = new HashMap<>();
        for (Entry<String, Collection<Object>> curParam : list) {
            sortedParams.put(curParam.getKey(), curParam.getValue());
        }

        return sortedParams;
    }
}
