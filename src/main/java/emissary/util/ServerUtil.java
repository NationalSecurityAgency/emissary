package emissary.util;

import java.util.Set;

import com.google.common.collect.Sets;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.pickup.file.FilePickUpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerUtil {

    private static final Logger LOG = LoggerFactory.getLogger(ServerUtil.class);

    public static void pauseServer() throws NamespaceException {
        LOG.debug("Pausing Emissary QueServer(s)");
        getPickupClients().forEach(client -> {
            client.pauseQueueServer();
            LOG.info("Emissary QueServer set to state Paused for {}", client);
        });
    }

    public static void unpauseServer() throws NamespaceException {
        LOG.debug("Unpausing Emissary QueServer(s)");
        getPickupClients().forEach(client -> {
            client.unpauseQueueServer();
            LOG.info("Emissary QueServer set to state Unpaused for {}", client);
        });
    }

    public static Set<FilePickUpClient> getPickupClients() throws NamespaceException {
        Set<FilePickUpClient> clients = Sets.newHashSet();
        for (String key : Namespace.keySet()) {
            Object obj = Namespace.lookup(key);
            if (obj instanceof FilePickUpClient) {
                LOG.debug("Namespace search for PickupClients found: {}", obj);
                clients.add((FilePickUpClient) obj);
            }
        }
        return clients;
    }
}
