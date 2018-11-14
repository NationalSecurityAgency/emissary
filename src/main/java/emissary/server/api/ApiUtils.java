package emissary.server.api;

import java.util.Set;

import emissary.core.EmissaryException;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.directory.DirectoryPlace;
import emissary.directory.EmissaryNode;
import emissary.server.EmissaryServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApiUtils {
    private static final Logger logger = LoggerFactory.getLogger(ApiUtils.class);

    public static Set<String> lookupPeers() throws EmissaryException {
        return DirectoryPlace.lookup().getPeerDirectories();
    }

    public static String stripPeerString(String peer) {
        // convert *.*.*.http://remote-host:port/DirectoryPlace to
        // http://remote-host:port/api/version so we can then make the calls
        return peer.substring(6, peer.indexOf("DirectoryPlace"));
    }

    public static String getHostAndPort() {
        // TODO: look at using guava HostAndPort
        try {
            EmissaryServer emissaryServer = (EmissaryServer) Namespace.lookup("EmissaryServer");
            EmissaryNode localNode = emissaryServer.getNode();
            return localNode.getNodeName() + ":" + localNode.getNodePort();
        } catch (NamespaceException e) {
            logger.error("Couldn't find EmissaryServer", e);
            return "Namespace lookup error, host unknown";
        }
    }
}
