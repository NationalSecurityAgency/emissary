package emissary.place;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

import emissary.core.IBaseDataObject;
import emissary.directory.KeyManipulator;
import emissary.kff.KffDataObjectHandler;
import emissary.pickup.PickUpPlace;
import emissary.util.DataUtil;
import emissary.util.TypeEngine;

/**
 * A hybrid of the MultiFileServerPlace and the FilePickupPlace. Knows how to sprout agents using the MoveSpool
 */
public abstract class MultiFileServerPlace extends PickUpPlace implements IMultiFileServerPlace {
    protected TypeEngine typeEngine;
    protected Set<?> NON_PROPAGATING_METADATA_VALS;

    public MultiFileServerPlace() throws IOException {
        super();
        configureAbstractPlace();
    }

    public MultiFileServerPlace(String configInfo, String dir, String placeLoc) throws IOException {
        super(configInfo, dir, placeLoc);
        configureAbstractPlace();
    }


    public MultiFileServerPlace(String configInfo, String placeLoc) throws IOException {
        super(configInfo, placeLoc);
        configureAbstractPlace();
    }

    public MultiFileServerPlace(InputStream configStream, String theDir, String thePlaceLocation) throws IOException {
        super(configStream, theDir, thePlaceLocation);
        configureAbstractPlace();
    }

    protected MultiFileServerPlace(InputStream configStream) throws IOException {
        super(configStream);
        configureAbstractPlace();
    }

    public MultiFileServerPlace(InputStream configStream, String thePlaceLocation) throws IOException {
        super(configStream, thePlaceLocation);
        configureAbstractPlace();
    }

    /**
     * Setup base configuration items related to attachment processing
     * <ul>
     * <li>NON_PROPAGATING_METADATA: items to skip when copying</li>
     * </ul>
     */
    private void configureAbstractPlace() {
        typeEngine = new TypeEngine(configG);
        NON_PROPAGATING_METADATA_VALS = configG.findEntriesAsSet("NON_PROPAGATING_METADATA");
    }

    /**
     * Setup place specific configuration information
     * 
     * @throws IOException when there is an error loading a configuration
     */
    protected abstract void configurePlace() throws IOException;

    /**
     * Validate that we should process this data. Will reject null data or empty objects. Generally, all
     * MultiFileServerPlaces should invoke this method before executing {@link #process(IBaseDataObject)} or
     * {@link #processHeavyDuty(IBaseDataObject)}. Override if your Place wants to do more validation before bothering to
     * process a payload.
     * 
     * @param d payload to validate
     * @return true if d is not null and not empty
     */
    public boolean shouldProcess(IBaseDataObject d) {
        return DataUtil.isNotEmpty(d);
    }

    /**
     * Used to propagate needed parent information to all children in the list without nullifying the child fileType
     * 
     * @param parent the source of parameters to be copied
     * @param children the destination for parameters to be copied
     */
    protected void addParentInformation(IBaseDataObject parent, List<IBaseDataObject> children) {
        addParentInformation(parent, children, false);
    }

    /**
     * Used to propagate needed parent information to all children in the list
     * 
     * @param parent the source of parameters to be copied
     * @param children the destination for parameters to be copied
     * @param nullifyFileType if true the child fileType is nullified after the copy
     */
    protected void addParentInformation(IBaseDataObject parent, List<IBaseDataObject> children, boolean nullifyFileType) {
        int birthOrder = 1;
        if (children != null) {
            int totalNumSiblings = children.size();
            for (IBaseDataObject child : children) {
                if (child == null) {
                    logger.warn("addParentInformation with null child!");
                    continue;
                }
                addParentInformation(parent, child, nullifyFileType);
                child.setBirthOrder(birthOrder++);
                child.setNumSiblings(totalNumSiblings);
            }
        }
    }

    /**
     * Used to propagate needed parent information to a sprouted child without nullifying fileType
     * 
     * @param parent the source of parameters to be copied
     * @param child the destination for parameters to be copied
     */
    protected void addParentInformation(IBaseDataObject parent, IBaseDataObject child) {
        addParentInformation(parent, child, false);
    }

    /**
     * Used to propagate needed parent information to a sprouted child
     * 
     * @param parent the source of parameters to be copied
     * @param child the destination for parameters to be copied
     * @param nullifyFileType if true the child fileType is nullified after the copy
     */
    protected void addParentInformation(IBaseDataObject parent, IBaseDataObject child, boolean nullifyFileType) {
        if (parent == null) {
            logger.warn("addParentInformation with null parent!");
            return;
        }

        if (child == null) {
            logger.warn("addParentInformation with null child!");
            return;
        }

        // Copy over the classification
        if (parent.getClassification() != null) {
            child.setClassification(parent.getClassification());
        }

        // And some other things we configure to be always copied
        for (String meta : ALWAYS_COPY_METADATA_VALS) {
            List<Object> parentVals = parent.getParameter(meta);
            if (parentVals != null && parentVals.size() > 0) {
                child.putParameter(meta, parentVals);
            }
        }


        // Copy over the transform history up to this point
        if (parent.transformHistory() != null) {
            child.setHistory(parent.transformHistory());
        }
        child.appendTransformHistory(KeyManipulator.makeSproutKey(myKey));
        child.putParameter(emissary.parser.SessionParser.ORIG_DOC_SIZE_KEY, Integer.toString(child.data().length));

        // start over with no FILETYPE if so directed
        if (nullifyFileType) {
            child.setFileType(null);
        }

        // Set up the proper KFF/HASH information for the child
        setKffDetails(child);
    }

    /**
     * Set up the new child's kff details
     * 
     * @param child the new data object, with it's parent parameters copied in
     */
    protected void setKffDetails(IBaseDataObject child) {

        // Change parent hit so it doesn't look like hit on the child
        KffDataObjectHandler.parentToChild(child);

        // Hash the the new child data, overwrites parent hashes if any
        kff.hash(child);
    }
}
