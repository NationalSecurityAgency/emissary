package emissary.place;

import emissary.core.IBaseDataObject;
import emissary.core.IBaseDataObjectHelper;
import emissary.pickup.PickUpPlace;
import emissary.util.DataUtil;
import emissary.util.TypeEngine;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A hybrid of the MultiFileServerPlace and the FilePickupPlace. Knows how to sprout agents using the MoveSpool
 */
public abstract class MultiFileServerPlace extends PickUpPlace implements IMultiFileServerPlace {
    protected TypeEngine typeEngine;
    protected Set<?> nonPropagatingMetadataValues;

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
        nonPropagatingMetadataValues = configG.findEntriesAsSet("NON_PROPAGATING_METADATA");
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
        IBaseDataObjectHelper.addParentInformationToChildren(parent, children, false, ALWAYS_COPY_METADATA_VALS, myKey, kff);
    }

    /**
     * Used to propagate needed parent information to all children in the list
     * 
     * @param parent the source of parameters to be copied
     * @param children the destination for parameters to be copied
     * @param nullifyFileType if true the child fileType is nullified after the copy
     */
    protected void addParentInformation(IBaseDataObject parent, @Nullable List<IBaseDataObject> children, boolean nullifyFileType) {
        IBaseDataObjectHelper.addParentInformationToChildren(parent, children, nullifyFileType, ALWAYS_COPY_METADATA_VALS, myKey, kff);
    }

    /**
     * Used to propagate needed parent information to a sprouted child without nullifying fileType
     * 
     * @param parent the source of parameters to be copied
     * @param child the destination for parameters to be copied
     */
    protected void addParentInformation(IBaseDataObject parent, IBaseDataObject child) {
        IBaseDataObjectHelper.addParentInformationToChild(parent, child, false, ALWAYS_COPY_METADATA_VALS, myKey, kff);
    }

    /**
     * Used to propagate needed parent information to a sprouted child
     * 
     * @param parent the source of parameters to be copied
     * @param child the destination for parameters to be copied
     * @param nullifyFileType if true the child fileType is nullified after the copy
     */
    protected void addParentInformation(@Nullable IBaseDataObject parent, @Nullable IBaseDataObject child, boolean nullifyFileType) {
        IBaseDataObjectHelper.addParentInformationToChild(parent, child, nullifyFileType, ALWAYS_COPY_METADATA_VALS, myKey, kff);
    }
}
