package emissary.analyze;

import emissary.core.IBaseDataObject;
import emissary.core.IBaseDataObjectHelper;
import emissary.place.ServiceProviderPlace;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Base class for analyzers
 */
public abstract class Analyzer extends ServiceProviderPlace {
    protected List<String> preferredViews;
    protected String analyzedDataName;
    protected List<Pattern> preferredViewPatternList;

    protected boolean findPreferredViewByRegex;


    /**
     * Create the place
     * 
     * @param configInfo the config file or resource
     * @param dir string key of the controlling directory place
     * @param placeLoc location of this place
     */
    protected Analyzer(final String configInfo, final String dir, final String placeLoc) throws IOException {
        super(configInfo, dir, placeLoc);
        configureAnalyzer();
    }

    /**
     * Create the place
     * 
     * @param configInfo the config file or resource
     * @param placeLoc location of this place
     */
    protected Analyzer(final String configInfo, final String placeLoc) throws IOException {
        // the second arg is arbitrary, but needs to look like this,
        // place.machine.port
        super(configInfo, placeLoc);
        configureAnalyzer();
    }

    protected Analyzer(final InputStream configInfo) throws IOException {
        super(configInfo);
        configureAnalyzer();
    }

    protected Analyzer() throws IOException {
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
        this.preferredViews = configG.findEntries("PREFERRED_VIEW", "TEXT");
        this.findPreferredViewByRegex = configG.findBooleanEntry("FIND_PREFERRED_VIEW_BY_REGEX", false);
        if (findPreferredViewByRegex) {
            preferredViewPatternList = preferredViews.stream().map(Pattern::compile).collect(Collectors.toList());
        }
        this.analyzedDataName = configG.findStringEntry("ANALYZED_DATA_NAME", null);
    }

    /**
     * Search for the first preferred view that is present or use the primary data if none
     * 
     * @param payload the payload to pull data from
     */
    protected byte[] getPreferredData(final IBaseDataObject payload) {

        if (findPreferredViewByRegex) {
            return IBaseDataObjectHelper.findPreferredDataByRegex(payload, preferredViewPatternList);
        } else {
            return IBaseDataObjectHelper.findPreferredData(payload, preferredViews);
        }
    }

    /**
     * Set the results back into the preferred data location
     * 
     * @param payload the data object to load
     * @param analyzedData the results of the analysis
     * @return true if the data was stored, false if not. See ANALYZED_DATA_NAME config element
     */
    protected boolean setPreferredData(final IBaseDataObject payload, final byte[] analyzedData) {
        if (this.analyzedDataName != null) {
            if ("base".equals(this.analyzedDataName)) {
                payload.setData(analyzedData);
            } else {
                payload.addAlternateView(this.analyzedDataName, analyzedData);
            }
            return true;
        }
        return false;
    }
}
