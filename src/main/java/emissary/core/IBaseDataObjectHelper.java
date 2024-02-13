package emissary.core;

import emissary.core.channels.SeekableByteChannelFactory;
import emissary.directory.KeyManipulator;
import emissary.kff.KffDataObjectHandler;
import emissary.parser.SessionParser;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Utility methods that assist with working with IBaseDataObject's.
 */
public final class IBaseDataObjectHelper {
    /**
     * A logger instance.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(IBaseDataObjectHelper.class);

    private static class InternalIdBaseDataObject extends BaseDataObject {
        private InternalIdBaseDataObject(final UUID internalId) {
            this.internalId = internalId;
        }
    }

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

        final BaseDataObject bdo = fullClone ? new InternalIdBaseDataObject(iBaseDataObject.getInternalId()) : new BaseDataObject();

        final SeekableByteChannelFactory sbcf = iBaseDataObject.getChannelFactory();
        if (sbcf != null) {
            bdo.setChannelFactory(sbcf);
        }

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
        try {
            kffDataObjectHandler.hash(childIBaseDataObject, true);
        } catch (NoSuchAlgorithmException | IOException e) {
            // Do not add the hash parameters
        }
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

    /**
     * Search for the first preferred view by regular expression or use the primary data if none match
     *
     * @param payload the payload to pull data from
     * @param preferredViewNamePatterns the list of referred view regular expression patterns (null returns data)
     */
    public static byte[] findPreferredDataByRegex(final IBaseDataObject payload, List<Pattern> preferredViewNamePatterns) {
        Validate.isTrue(payload != null, "Required: payload != null");

        return Optional.ofNullable(preferredViewNamePatterns).orElse(Collections.emptyList()).stream()
                .map(preferredViewNamePattern -> findFirstAlternameViewNameByRegex(payload, preferredViewNamePattern))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(payload::getAlternateView)
                .findFirst().orElse(payload.data());
    }

    private static Optional<String> findFirstAlternameViewNameByRegex(IBaseDataObject payload, Pattern preferredViewNamePattern) {
        return payload.getAlternateViewNames().stream()
                .filter(altViewName -> preferredViewNamePattern.matcher(altViewName).find())
                .findFirst();
    }

    /**
     * Search for the first preferred view that is present or use the primary data if none
     *
     * @param payload the payload to pull data from
     * @param preferredViews the list of preferred views (null returns data)
     */
    public static byte[] findPreferredData(final IBaseDataObject payload, List<String> preferredViews) {
        Validate.isTrue(payload != null, "Required: payload != null");

        final Set<String> altViewNames = payload.getAlternateViewNames();

        if (preferredViews != null) {
            for (final String view : preferredViews) {
                if (altViewNames.contains(view)) {
                    return payload.getAlternateView(view);
                }
            }
        }

        return payload.data();
    }
}
