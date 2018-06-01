package emissary.place.sample;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import emissary.core.IBaseDataObject;
import emissary.place.ServiceProviderPlace;
import emissary.server.mvc.DocumentAction;

/**
 * Store payload objects that come from web submissions until they are retrieved by SUBMISSION_TOKEN value
 */
public class WebSubmissionPlace extends ServiceProviderPlace implements emissary.place.AgentsNotSupportedPlace {

    /** The hash of stored documents */
    protected final Map<String, List<IBaseDataObject>> map = new HashMap<String, List<IBaseDataObject>>();

    /** Matching output types will have data available */
    protected List<String> outputTypes;

    /** Matchine view names will have view data available */
    protected List<String> viewTypes;

    /**
     * Create and register with all defaults
     */
    public WebSubmissionPlace() throws IOException {
        super();
        configure();
    }

    /**
     * Create and register
     */
    public WebSubmissionPlace(String configInfo, String dir, String placeLoc) throws IOException {
        super(configInfo, dir, placeLoc);
        configure();
    }

    /**
     * Create for test
     */
    public WebSubmissionPlace(String configInfo) throws IOException {
        super(configInfo, "WebSubmissionPlace.www.example.com:8001");
        configure();
    }

    /**
     * Configure our stuff
     */
    protected void configure() {
        outputTypes = configG.findEntries("OUTPUT_DATA_TYPE", ".*");
        viewTypes = configG.findEntries("OUTPUT_VIEW_TYPE", ".*");
    }

    /**
     * Consume the data object list
     */
    @Override
    public List<IBaseDataObject> agentProcessHeavyDuty(List<IBaseDataObject> payloadList) {

        // Nuke the form that got us here
        for (IBaseDataObject payload : payloadList) {
            nukeMyProxies(payload);
        }

        // Sort the list of records
        Collections.sort(payloadList, new emissary.util.ShortNameComparator());

        // Grab the submission token
        String token = payloadList.get(0).getStringParameter(DocumentAction.SUBMISSION_TOKEN);

        // Store the payload
        if (token != null) {
            synchronized (map) {
                logger.debug("Storing  family tree payload " + token + " (" + payloadList.size() + ")");
                List<IBaseDataObject> clones = new ArrayList<IBaseDataObject>();
                for (IBaseDataObject d : payloadList) {
                    try {
                        clones.add(d.clone());
                    } catch (CloneNotSupportedException ex) {
                        logger.warn("Cannot clone payload", ex);
                        clones.add(d);
                    }
                }
                map.put(token, clones);
            }
        }

        return Collections.emptyList();
    }

    /**
     * Take something from the store, if found removed from map
     * 
     * @param token the web submission token
     * @return the stored family tree or null
     */
    public List<IBaseDataObject> take(String token) {
        List<IBaseDataObject> list = null;
        synchronized (map) {
            list = map.remove(token);
        }

        // Clear out unwanted data
        if (list != null) {
            for (IBaseDataObject d : list) {
                // Test current forms and file type
                if (!matchesAny(d.getAllCurrentForms(), outputTypes) && !matchesAny(d.getFileType(), outputTypes)) {
                    logger.debug("Clearing data " + d.getAllCurrentForms() + ", " + d.getFileType());
                    d.setData(null);
                }

                // Test alternate views
                for (String avname : d.getAlternateViewNames()) {
                    if (!matchesAny(avname, viewTypes)) {
                        logger.debug("Clearing alt view " + avname);
                        d.addAlternateView(avname, null);
                    }
                }
            }
        }
        return list;
    }

    /**
     * Match against all
     */
    private boolean matchesAny(List<String> forms, List<String> patterns) {
        for (String f : forms) {
            if (f == null) {
                continue;
            }

            for (String p : patterns) {
                if (f.matches(p)) {
                    logger.debug("Pattern match " + f + " with " + p);
                    return true;
                }
                logger.debug("Pattern no-match " + f + " with " + p);
            }
        }
        return false;
    }

    /**
     * Match against all
     */
    private boolean matchesAny(String form, List<String> patterns) {
        if (form == null) {
            return false;
        }

        for (String p : patterns) {
            if (form.matches(p)) {
                logger.debug("Pattern match " + form + " with " + p);
                return true;
            }
            logger.debug("Pattern no-match " + form + " with " + p);
        }
        return false;
    }

    /**
     * List the keys in the map
     */
    public synchronized List<String> keys() {
        return new ArrayList<String>(map.keySet());
    }

    /**
     * Test run
     */
    public static void main(String[] argv) {
        mainRunner(WebSubmissionPlace.class.getName(), argv);
    }
}
