package emissary.core;

import emissary.core.channels.SeekableByteChannelFactory;
import emissary.directory.KeyManipulator;
import emissary.kff.KffDataObjectHandler;
import emissary.parser.SessionParser;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Utility methods that assist with working with IBaseDataObject's.
 */
public final class IBaseDataObjectHelper {
    /**
     * A logger instance.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(IBaseDataObjectHelper.class);

    private static final String DIFF_NOT_NULL_MSG = "Required: differences not null";
    private static final String ID_NOT_NULL_MSG = "Required: identifier not null";
    private static final String ARE_NOT_EQUAL = " are not equal";

    private IBaseDataObjectHelper() {}

    /**
     * Clones an IBaseDataObject equivalently to emissary.core.BaseDataObject.clone(), which duplicates some attributes.
     * 
     * A "fullClone" duplicates all attributes.
     * 
     * @param iBaseDataObject the IBaseDataObject to be cloned.
     * @param fullClone specifies if all fields should be cloned.
     * @return the clone of the IBaseDataObject passed in.
     */
    public static IBaseDataObject clone(final IBaseDataObject iBaseDataObject, final boolean fullClone) {
        Validate.notNull(iBaseDataObject, "Required: iBaseDataObject not null");

        final BaseDataObject bdo = new BaseDataObject();

        final SeekableByteChannelFactory sbcf = iBaseDataObject.getChannelFactory();
        if (sbcf != null) {
            bdo.setChannelFactory(sbcf);
        }

        bdo.replaceCurrentForm(null);
        final List<String> allCurrentForms = iBaseDataObject.getAllCurrentForms();
        for (int i = 0; i < allCurrentForms.size(); i++) {
            bdo.enqueueCurrentForm(allCurrentForms.get(i));
        }
        bdo.setHistory(iBaseDataObject.getTransformHistory());
        bdo.putParameters(iBaseDataObject.getParameters());
        for (final Map.Entry<String, byte[]> entry : iBaseDataObject.getAlternateViews().entrySet()) {
            bdo.addAlternateView(entry.getKey(), entry.getValue());
        }
        bdo.setPriority(iBaseDataObject.getPriority());
        bdo.setCreationTimestamp((Date) iBaseDataObject.getCreationTimestamp().clone());
        if (iBaseDataObject.getExtractedRecords() != null) {
            bdo.setExtractedRecords(iBaseDataObject.getExtractedRecords());
        }
        if (iBaseDataObject.getFilename() != null) {
            bdo.setFilename(iBaseDataObject.getFilename());
        }

        if (fullClone) {
            try {
                setPrivateFieldValue(bdo, "internalId", iBaseDataObject.getInternalId());
            } catch (IllegalAccessException | NoSuchFieldException e) {
                // Ignore any problems setting the internal id.
            }
            final String processingError = iBaseDataObject.getProcessingError();
            if (processingError != null) {
                bdo.addProcessingError(processingError.substring(0, processingError.length() - 1));
            }
            bdo.setFontEncoding(iBaseDataObject.getFontEncoding());
            bdo.setNumChildren(iBaseDataObject.getNumChildren());
            bdo.setNumSiblings(iBaseDataObject.getNumSiblings());
            bdo.setBirthOrder(iBaseDataObject.getBirthOrder());
            bdo.setHeader(iBaseDataObject.header() == null ? null : iBaseDataObject.header().clone());
            bdo.setFooter(iBaseDataObject.footer() == null ? null : iBaseDataObject.footer().clone());
            bdo.setHeaderEncoding(iBaseDataObject.getHeaderEncoding());
            bdo.setClassification(iBaseDataObject.getClassification());
            bdo.setBroken(iBaseDataObject.getBroken());
            bdo.setOutputable(iBaseDataObject.isOutputable());
            bdo.setId(iBaseDataObject.getId());
            bdo.setWorkBundleId(iBaseDataObject.getWorkBundleId());
            bdo.setTransactionId(iBaseDataObject.getTransactionId());
        }

        return bdo;
    }

    /**
     * This method reflectively sets a private method that is not normally accessible. This method should only be used when
     * the field must be set and there is no other way to do it. Ideally the class would be modified so that this method
     * call would not be necessary.
     * 
     * @param bdo the BaseDataObject to set the field on.
     * @param fieldName the name of the field to be set.
     * @param object the object that the field is to be set to.
     * @throws IllegalAccessException if this {@code Field} object is enforcing Java language access control and the
     *         underlying field is either inaccessible or final.
     * @throws NoSuchFieldException if a field with the specified name is not found.
     */
    public static void setPrivateFieldValue(final BaseDataObject bdo, final String fieldName, final Object object)
            throws IllegalAccessException, NoSuchFieldException {
        Validate.notNull(bdo, "Required: bdo not null");
        Validate.notNull(fieldName, "Required: fieldName not null");

        final Field field = bdo.getClass().getDeclaredField(fieldName);

        field.setAccessible(true); // NOSONAR intentional visibility change
        field.set(bdo, object); // NOSONAR intentional visibility change
    }

    /**
     * This method compares two IBaseDataObject's and adds any differences to the provided string list.
     * 
     * @param ibdo1 the first IBaseDataObject to compare.
     * @param ibdo2 the second IBaseDataObject to compare.
     * @param differences the string list differences are to be added to.
     * @param checkData says whether the data in the two IBaseDataObjects should be compared.
     * @param checkTimestamp says whether the timestamps in the two IBaseDataObjects should be compared.
     * @param checkInternalId says whether the internalIds of the two IBaseDataObjects should be compared.
     * @param checkTransformHistory whether to check the transform history - usually false if comparing two places.
     */
    public static final void diff(final IBaseDataObject ibdo1, final IBaseDataObject ibdo2,
            final List<String> differences, final boolean checkData, final boolean checkTimestamp,
            final boolean checkInternalId, final boolean checkTransformHistory) {
        Validate.notNull(ibdo1, "Required: ibdo1 not null");
        Validate.notNull(ibdo2, "Required: ibdo2 not null");
        Validate.notNull(differences, DIFF_NOT_NULL_MSG);

        if (checkData) {
            final SeekableByteChannelFactory sbcf1 = ibdo1.getChannelFactory();
            final SeekableByteChannelFactory sbcf2 = ibdo2.getChannelFactory();

            if (sbcf1 != null && sbcf2 != null) {
                try (InputStream is1 = Channels.newInputStream(sbcf1.create());
                        InputStream is2 = Channels.newInputStream(sbcf2.create())) {
                    if (!IOUtils.contentEquals(is1, is2)) {
                        differences.add(String.format("Data not equal. 1.cs=%s 2.cs=%s",
                                ibdo1.getChannelSize(), ibdo2.getChannelSize()));
                    }
                } catch (IOException e) {
                    differences.add("Failed to compare data: " + e.getMessage());
                }
            } else if (sbcf1 == null && sbcf2 == null) {
                // Do nothing as they are considered equal.
            } else {
                differences.add(String.format("Data not equal. sbcf1=%s sbcf2=%s", sbcf1, sbcf2));
            }
        }

        diff(ibdo1.getFilename(), ibdo2.getFilename(), "filename", differences);
        diff(ibdo1.shortName(), ibdo2.shortName(), "shortName", differences);
        if (checkInternalId) {
            diff(ibdo1.getInternalId(), ibdo2.getInternalId(), "internalId", differences);
        }
        diff(ibdo1.currentForm(), ibdo2.currentForm(), "currentForm", differences);
        diff(ibdo1.getProcessingError(), ibdo2.getProcessingError(), "processingError", differences);

        if (checkTransformHistory) {
            diff(ibdo1.transformHistory(), ibdo2.transformHistory(), "transformHistory", differences);
        }

        diff(ibdo1.getFontEncoding(), ibdo2.getFontEncoding(), "fontEncoding", differences);
        diff(ibdo1.getParameters(), ibdo2.getParameters(), "parameters", differences);
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
        if (checkTimestamp) {
            diff(ibdo1.getCreationTimestamp(), ibdo2.getCreationTimestamp(), "creationTimestamp", differences);
        }
        diff(ibdo1.getExtractedRecords(), ibdo2.getExtractedRecords(), "extractedRecords", differences);
        diff(ibdo1.isOutputable(), ibdo2.isOutputable(), "outputable", differences);
        diff(ibdo1.getId(), ibdo2.getId(), "id", differences);
        diff(ibdo1.getWorkBundleId(), ibdo2.getWorkBundleId(), "workBundleId", differences);
        diff(ibdo1.getTransactionId(), ibdo2.getTransactionId(), "transactionId", differences);
    }

    /**
     * This method compares two lists of IBaseDataObject's and adds any differences to the provided string list.
     * 
     * @param ibdoList1 the first list of IBaseDataObjects to compare.
     * @param ibdoList2 the second list of IBaseDataObjects to compare.
     * @param identifier a string that helps identify the context of comparing these two list of IBaseDataObjects.
     * @param differences the string list differences are to be added to.
     * @param checkData says whether the data in the two IBaseDataObjects should be compared.
     * @param checkTimestamp says whether the timestamps in the two IBaseDataObjects should be compared.
     * @param checkInternalId says whether the internalIds of the two IBaseDataObjects should be compared.
     * @param checkTransformHistory whether to check the transform history - usually false if comparing two places
     */
    public static void diff(final List<IBaseDataObject> ibdoList1, final List<IBaseDataObject> ibdoList2,
            final String identifier, final List<String> differences, final boolean checkData,
            final boolean checkTimestamp, final boolean checkInternalId, final boolean checkTransformHistory) {
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

                diff(ibdoList1.get(i), ibdoList2.get(i), childDifferences, checkData, checkTimestamp, checkInternalId,
                        checkTransformHistory);

                final String prefix = identifier + " : " + i + " : ";
                while (!childDifferences.isEmpty()) {
                    differences.add(prefix + childDifferences.remove(0)); // NOSONAR Used correctly
                }
            }
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
     * Used to propagate needed parent information to a sprouted child. NOTE: This is taken from
     * emissary.place.MultiFileServerPlace.
     * 
     * @param parentIBaseDataObject the source of parameters to be copied
     * @param childIBaseDataObject the destination for parameters to be copied
     * @param nullifyFileType if true the child fileType is nullified after the copy
     * @param alwaysCopyMetadataKeys set of metadata keys to always copy from parent to child.
     * @param placeKey the place key to be added to the transform history.
     * @param kffDataObjectHandler the kffDataObjectHandler to use to create the kff hashes.
     */
    public static void addParentInformationToChild(final IBaseDataObject parentIBaseDataObject,
            final IBaseDataObject childIBaseDataObject, final boolean nullifyFileType,
            final Set<String> alwaysCopyMetadataKeys, final String placeKey,
            final KffDataObjectHandler kffDataObjectHandler) {
        Validate.notNull(parentIBaseDataObject, "Required: parentIBaseDataObject not null");
        Validate.notNull(childIBaseDataObject, "Required: childIBaseDataObject not null");
        Validate.notNull(alwaysCopyMetadataKeys, "Required: alwaysCopyMetadataKeys not null");
        Validate.notNull(placeKey, "Required: placeKey not null");
        Validate.notNull(kffDataObjectHandler, "Required: kffDataObjectHandler not null");

        // Copy over the classification
        if (parentIBaseDataObject.getClassification() != null) {
            childIBaseDataObject.setClassification(parentIBaseDataObject.getClassification());
        }

        // And some other things we configure to be always copied
        for (final String meta : alwaysCopyMetadataKeys) {
            final List<Object> parentVals = parentIBaseDataObject.getParameter(meta);

            if (parentVals != null) {
                childIBaseDataObject.putParameter(meta, parentVals);
            }
        }

        // Copy over the transform history up to this point
        childIBaseDataObject.setHistory(parentIBaseDataObject.getTransformHistory());
        childIBaseDataObject.appendTransformHistory(KeyManipulator.makeSproutKey(placeKey));
        try {
            childIBaseDataObject.putParameter(SessionParser.ORIG_DOC_SIZE_KEY,
                    Long.toString(childIBaseDataObject.getChannelSize()));
        } catch (IOException e) {
            // Do not add the ORIG_DOC_SIZE_KEY parameter.
        }

        // start over with no FILETYPE if so directed
        if (nullifyFileType) {
            childIBaseDataObject.setFileType(null);
        }

        // Set up the proper KFF/HASH information for the child
        // Change parent hit so it doesn't look like hit on the child
        KffDataObjectHandler.parentToChild(childIBaseDataObject);

        // Hash the new child data, overwrites parent hashes if any
        kffDataObjectHandler.hash(childIBaseDataObject);
    }

    /**
     * Used to propagate needed parent information to a sprouted child. NOTE: This is taken from
     * emissary.place.MultiFileServerPlace.
     * 
     * @param parent the source of parameters to be copied
     * @param children the destination for parameters to be copied
     * @param nullifyFileType if true the child fileType is nullified after the copy
     * @param alwaysCopyMetadataKeys set of metadata keys to always copy from parent to child.
     * @param placeKey the place key to be added to the transform history.
     * @param kffDataObjectHandler the kffDataObjectHandler to use to create the kff hashes.
     */
    public static void addParentInformationToChildren(final IBaseDataObject parent, @Nullable final List<IBaseDataObject> children,
            final boolean nullifyFileType, final Set<String> alwaysCopyMetadataKeys, final String placeKey,
            final KffDataObjectHandler kffDataObjectHandler) {
        Validate.notNull(parent, "Required: parent not null");
        Validate.notNull(alwaysCopyMetadataKeys, "Required: alwaysCopyMetadataKeys not null");
        Validate.notNull(placeKey, "Required: placeKey not null");
        Validate.notNull(kffDataObjectHandler, "Required: kffDataObjectHandler not null");

        if (children != null) {
            int birthOrder = 1;

            final int totalNumSiblings = children.size();
            for (final IBaseDataObject child : children) {
                if (child == null) {
                    LOGGER.warn("addParentInformation with null child");
                    continue;
                }
                addParentInformationToChild(parent, child, nullifyFileType, alwaysCopyMetadataKeys, placeKey,
                        kffDataObjectHandler);
                child.setBirthOrder(birthOrder++);
                child.setNumSiblings(totalNumSiblings);
            }
        }
    }
}
