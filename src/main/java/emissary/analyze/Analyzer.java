package emissary.analyze;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

import emissary.core.IBaseDataObject;
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
     */
    protected byte[] getPreferredData(final IBaseDataObject payload) {
        final Set<String> altViewNames = payload.getAlternateViewNames();

        for (final String view : this.PREFERRED_VIEWS) {
            if (altViewNames.contains(view)) {
                return payload.getAlternateView(view);
            }
        }
        return payload.data();
    }

    /**
     * Set the results back into the preferred data location
     * 
     * @param payload the data object to load
     * @param analyzedData the results of the analysis
     * @return true if the data was stored, false if not. See ANALYZED_DATA_NAME config element
     */
    protected boolean setPreferredData(final IBaseDataObject payload, final byte[] analyzedData) {
        if (this.ANALYZED_DATA_NAME != null) {
            if ("base".equals(this.ANALYZED_DATA_NAME)) {
                payload.setData(analyzedData);
            } else {
                payload.addAlternateView(this.ANALYZED_DATA_NAME, analyzedData);
            }
            return true;
        }
        return false;
    }
}
