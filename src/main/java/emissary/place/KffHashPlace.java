package emissary.place;

import java.io.IOException;
import java.io.InputStream;

import emissary.core.IBaseDataObject;
import emissary.core.ResourceException;
import emissary.kff.KffDataObjectHandler;

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

    public KffHashPlace(String thePlaceLocation) throws IOException {
        super(thePlaceLocation);
    }

    public KffHashPlace(String configFile, String theDir, String thePlaceLocation) throws IOException {
        super(configFile, theDir, thePlaceLocation);
    }

    public KffHashPlace(InputStream configStream, String theDir, String thePlaceLocation) throws IOException {
        super(configStream, theDir, thePlaceLocation);
    }

    public KffHashPlace(InputStream configStream) throws IOException {
        super(configStream);
    }

    public KffHashPlace(String configFile, String placeLocation) throws IOException {
        super(configFile, placeLocation);
    }

    public KffHashPlace(InputStream configStream, String placeLocation) throws IOException {
        super(configStream, placeLocation);
    }

    @Override
    protected void setupPlace(String theDir, String placeLocation) throws IOException {
        super.setupPlace(theDir, placeLocation);
        initKff();
    }

    @Override
    public void process(IBaseDataObject payload) throws ResourceException {
        if (KffDataObjectHandler.hashPresent(payload) || TRUE.equalsIgnoreCase(payload.getStringParameter(SKIP_KFF_HASH))) {
            logger.debug("Skipping KffHash for IBDO {}", payload.getInternalId());
            return;
        }

        kff.hash(payload);
    }

}
