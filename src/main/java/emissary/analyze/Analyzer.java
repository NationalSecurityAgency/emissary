package emissary.analyze;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import emissary.core.IBaseDataObject;
import emissary.core.blob.IDataContainer;
import emissary.core.view.IViewManager;
import emissary.place.ServiceProviderPlace;

/**
 * Base class for analyzers
 */
public abstract class Analyzer extends ServiceProviderPlace {
    protected List<String> PREFERRED_VIEWS;
    protected String ANALYZED_DATA_NAME;

    /**
     * Create the place
     * 
     * @param configInfo the config file or resource
     * @param dir string key of the controlling directory place
     * @param placeLoc location of this place
     */
    public Analyzer(final String configInfo, final String dir, final String placeLoc) throws IOException {
        super(configInfo, dir, placeLoc);
        configureAnalyzer();
    }

    /**
     * Create the place
     * 
     * @param configInfo the config file or resource
     * @param placeLoc location of this place
     */
    public Analyzer(final String configInfo, final String placeLoc) throws IOException {
        // the second arg is arbitrary, but needs to look like this,
        // place.machine.port
        super(configInfo, placeLoc);
        configureAnalyzer();
    }

    public Analyzer(final InputStream configInfo) throws IOException {
        super(configInfo);
        configureAnalyzer();
    }

    public Analyzer() throws IOException {
        super();
        configureAnalyzer();
    }

    /**
     * Configure this analyzer
     * <ul>
     * <li>PREFERRED_VIEW list of views to try and get data from, default TEXT</li>
     * <li>ANALYZED_DATA_NAME name of view to store results in or 'base' for primary data, default null</li>
     * </ul>
     */
    protected void configureAnalyzer() {
        this.PREFERRED_VIEWS = configG.findEntries("PREFERRED_VIEW", "TEXT");
        this.ANALYZED_DATA_NAME = configG.findStringEntry("ANALYZED_DATA_NAME", null);
    }

    /**
     * Search for the first preferred view that is present or use the primary data if none
     * 
     * @param payload the payload to pull data from
     * @deprecated Use {@link #getPreferredExistingDataContainer(IBaseDataObject)}
     */
    @Deprecated
    protected byte[] getPreferredData(final IBaseDataObject payload) {
        IDataContainer preferredDataContainer = getPreferredExistingDataContainer(payload);
        return preferredDataContainer != null ? preferredDataContainer.data() : new byte[0];
    }

    /**
     * Search for the first preferred view that is present or use the primary data if none
     * 
     * @param payload the payload to pull data from
     */
    protected IDataContainer getPreferredExistingDataContainer(final IBaseDataObject payload) {
        IViewManager vm = payload.getViewManager();
        final Set<String> altViewNames = vm.getAlternateViewNames();

        return this.PREFERRED_VIEWS
                .stream()
                .filter(altViewNames::contains)
                .findFirst()
                .map(vm::getAlternateViewContainer)
                .orElse(payload.getDataContainer());
    }

    /**
     * Set the results back into the preferred data location
     * 
     * @param payload the data object to load
     * @param analyzedData the results of the analysis
     * @return true if the data was stored, false if not. See ANALYZED_DATA_NAME config element
     * @deprecated use {@link #getPreferredDataContainerForWriting(IBaseDataObject)}.
     */
    @Deprecated
    protected boolean setPreferredData(final IBaseDataObject payload, final byte[] analyzedData) {
        Optional<IDataContainer> odc = getPreferredDataContainerForWriting(payload);
        odc.ifPresent(dc -> dc.setData(analyzedData));
        return odc.isPresent();
    }

    /**
     * Get a data container for the preferred data location.
     * 
     * @param payload the payload to write data to
     * @return An optional data container to be written to, empty if ANALYZED_DATA_NAME is not set.
     */
    protected Optional<IDataContainer> getPreferredDataContainerForWriting(final IBaseDataObject payload) {
        if (this.ANALYZED_DATA_NAME != null) {
            if ("base".equals(this.ANALYZED_DATA_NAME)) {
                return Optional.of(payload.newDataContainer());
            } else {
                return Optional.of(payload.getViewManager().addAlternateView(this.ANALYZED_DATA_NAME));
            }
        }
        return Optional.empty();
    }
}
