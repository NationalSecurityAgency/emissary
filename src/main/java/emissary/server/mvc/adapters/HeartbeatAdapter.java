package emissary.server.mvc.adapters;

import emissary.client.EmissaryClient;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.directory.DirectoryPlace;
import emissary.directory.KeyManipulator;
import emissary.place.IServiceProviderPlace;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stuff for adapting the Directory heartbeat calls to HTTP
 */
public class HeartbeatAdapter extends EmissaryClient {

    private static final Logger logger = LoggerFactory.getLogger(HeartbeatAdapter.class);

    public static final String FROM_PLACE_NAME = "hbf";
    public static final String TO_PLACE_NAME = "hbt";

    /**
     * Process the heartbeat call coming remotely over HTTP request params onto the specified (local) directory place
     *
     * @return reference to the place when found, null otherwise
     */
    @Nullable
    public IServiceProviderPlace inboundHeartbeat(final String fromName, final String toName) throws NamespaceException {
        if (toName == null) {
            throw new IllegalArgumentException("No place specified in msg from " + fromName);
        }

        logger.debug("Servicing inbound heartbeat for {} to {}", fromName, toName);

        final DirectoryPlace d = (DirectoryPlace) Namespace.lookup(toName);
        if (!d.isStaticPeer(KeyManipulator.getDefaultDirectoryKey(fromName))) {
            logger.warn("Contact attempted from {} but it is not a configured peer", fromName);
            return null;
        }

        IServiceProviderPlace place = null;

        try {
            place = (IServiceProviderPlace) Namespace.lookup(toName);
        } catch (NamespaceException ne) {
            throw ne;
        }

        if (place == null) {
            throw new IllegalArgumentException("No place found using name " + toName + " from " + fromName);
        }

        logger.debug("Heartbeat returning {}", place);

        return place;
    }
}
