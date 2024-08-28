package emissary.place;

import emissary.core.IBaseDataObject;
import emissary.core.ResourceException;
import emissary.util.GitRepositoryState;
import emissary.util.Version;

import java.io.IOException;
import java.io.InputStream;

public class VersionPlace extends ServiceProviderPlace {

    private static final String EMISSARY_VERSION = "EMISSARY_VERSION";
    private static final String EMISSARY_VERSION_HASH = "EMISSARY_VERSION_HASH";
    private boolean includeDate;
    private boolean useAbbrevHash;
    private final Version version = new Version();
    private String formattedVersion;
    private String versionHash;

    private GitRepositoryState gitRepositoryState;

    /**
     * Create the place from the specified config file or resource
     *
     * @param configInfo the config file or resource to use
     * @param dir the name of the controlling directory to register with
     * @param placeLoc string name of this place
     */
    public VersionPlace(String configInfo, String dir, String placeLoc) throws IOException {
        super(configInfo, dir, placeLoc);
        configurePlace();
    }

    /**
     * Create the place from the specified config stream data
     *
     * @param configInfo the config file or resource to use
     * @param dir the name of the controlling directory to register with
     * @param placeLoc string name of this place
     */
    public VersionPlace(InputStream configInfo, String dir, String placeLoc) throws IOException {
        super(configInfo, dir, placeLoc);
        configurePlace();
    }

    /**
     * Create the place from the specified config stream data
     *
     * @param configInfo the config file or resource to use
     */
    public VersionPlace(InputStream configInfo) throws IOException {
        super(configInfo);
        configurePlace();
    }

    /**
     * Create with the default configuration
     */
    public VersionPlace() throws IOException {
        super();
        configurePlace();
    }

    private void configurePlace() {
        this.gitRepositoryState = initGitRepositoryState();

        includeDate = configG.findBooleanEntry("INCLUDE_DATE", true);
        useAbbrevHash = configG.findBooleanEntry("USE_ABBREV_HASH", false);
        formattedVersion = getEmissaryVersion();
        versionHash = getEmissaryVersionHash();
    }

    @Override
    public void process(IBaseDataObject payload) throws ResourceException {
        payload.putParameter(EMISSARY_VERSION, formattedVersion);
        payload.putParameter(EMISSARY_VERSION_HASH, versionHash);
    }

    GitRepositoryState initGitRepositoryState() {
        return GitRepositoryState.getRepositoryState();
    }

    private String getEmissaryVersion() {
        if (includeDate) {
            // version with date & time information
            return version.getVersion() + "-" + version.getTimestamp().replaceAll("\\D", "");
        } else {
            // adds just version
            return version.getVersion();
        }
    }

    private String getEmissaryVersionHash() {
        if (useAbbrevHash) {
            // first 8 chars of commit hash
            return gitRepositoryState.getCommitIdAbbrev();
        } else {
            // full commit hash (default option)
            return gitRepositoryState.getCommitId();
        }
    }
}
