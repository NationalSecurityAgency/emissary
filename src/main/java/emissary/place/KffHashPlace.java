package emissary.place;

import emissary.core.IBaseDataObject;
import emissary.core.ResourceException;
import emissary.kff.KffDataObjectHandler;

import java.io.IOException;
import java.io.InputStream;

/**
 * Hashing place to hash payload unless hashes are set or skip flag is set. This place is intended to execute in the
 * Study phase to hash all new content that hasn't been explicitly skipped.
 * 
 * <p>
 * For performance reasons, we've needed lightweight 'chunkers' that understand the parsing boundaries of input formats
 * which then delegate to a place for full parsing.
 */
public class KffHashPlace extends ServiceProviderPlace {

    private static final String TRUE = "TRUE";

    public static final String SKIP_KFF_HASH = "SKIP_KFF_HASH";

    private boolean useSbc = false;

    private boolean createMurmurHash = false;
    private String murmurHashParamName = "HASH_ID";

    public KffHashPlace(String thePlaceLocation) throws IOException {
        super(thePlaceLocation);
        configurePlace();
    }

    public KffHashPlace(String configFile, String theDir, String thePlaceLocation) throws IOException {
        super(configFile, theDir, thePlaceLocation);
        configurePlace();
    }

    public KffHashPlace(InputStream configStream, String theDir, String thePlaceLocation) throws IOException {
        super(configStream, theDir, thePlaceLocation);
        configurePlace();
    }

    public KffHashPlace(InputStream configStream) throws IOException {
        super(configStream);
        configurePlace();
    }

    public KffHashPlace(String configFile, String placeLocation) throws IOException {
        super(configFile, placeLocation);
        configurePlace();
    }

    public KffHashPlace(InputStream configStream, String placeLocation) throws IOException {
        super(configStream, placeLocation);
        configurePlace();
    }

    @Override
    protected void setupPlace(String theDir, String placeLocation) throws IOException {
        super.setupPlace(theDir, placeLocation);
        initKff();
    }

    protected void configurePlace() {
        useSbc = configG.findBooleanEntry("USE_SBC", useSbc);
        createMurmurHash = configG.findBooleanEntry("CREATE_MURMUR_HASH", createMurmurHash);
        murmurHashParamName = configG.findStringEntry("MURMUR_HASH_PARAM_NAME", murmurHashParamName);
    }

    @Override
    public void process(IBaseDataObject payload) throws ResourceException {
        if (KffDataObjectHandler.hashPresent(payload) || TRUE.equalsIgnoreCase(payload.getParameterAsString(SKIP_KFF_HASH))) {
            logger.debug("Skipping KffHash for IBDO {}", payload.getInternalId());
            return;
        }

        kff.hash(payload, useSbc, createMurmurHash, murmurHashParamName);
    }

}
