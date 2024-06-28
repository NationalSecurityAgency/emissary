package emissary.server.mvc;

import emissary.core.EmissaryException;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.directory.DirectoryEntry;
import emissary.directory.DirectoryPlace;
import emissary.directory.IDirectoryPlace;
import emissary.directory.KeyManipulator;
import emissary.server.mvc.adapters.DirectoryAdapter;
import emissary.server.mvc.adapters.RequestUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import org.glassfish.jersey.server.mvc.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

@Path("")
// context is emissary
public class DumpDirectoryAction {

    private static final Logger LOG = LoggerFactory.getLogger(DumpDirectoryAction.class);

    @GET
    @Path("/DumpDirectory.action")
    @Produces(MediaType.TEXT_HTML)
    @Template(name = "/dump_directory")
    public Map<String, Object> dumpDirectory(@Context HttpServletRequest request, @Nullable @QueryParam("targetDir") String targetDir) {
        Map<String, Object> map = new HashMap<>();
        IDirectoryPlace dir = null;
        List<String> errors = new ArrayList<>();

        String cleanTargetDirectory = RequestUtil.sanitizeParameter(targetDir);

        // get top level
        if (cleanTargetDirectory == null) {
            LOG.debug("Lookup is using default name since no {} was specified", DirectoryAdapter.TARGET_DIRECTORY);
            try {
                dir = DirectoryPlace.lookup();
            } catch (EmissaryException e) {
                LOG.error("DirectoryPlace lookup error", e);
                errors.add("DirectoryPlace lookup error: " + e.getMessage());
            }
        } else {
            LOG.debug("Lookup is using directory name {}", cleanTargetDirectory);
            try {
                dir = (IDirectoryPlace) Namespace.lookup(cleanTargetDirectory);
            } catch (NamespaceException e) {
                LOG.error("Namespace lookup error for {}", cleanTargetDirectory, e);
                errors.add("Namespace lookup error: " + e.getMessage());
            }
        }
        if (dir != null) {
            LOG.debug("Lookup returned {}", dir);
            map.put("directory-label", dir.toString());
        }

        int rowCount = 0;
        List<DirectoryInfo> entryKeys = new ArrayList<>();
        long now = System.currentTimeMillis();

        if (dir != null) {
            for (String dataId : dir.getEntryKeys()) {
                LOG.trace("dataId key is {}", dataId);
                List<DirectoryEntryInfo> list = new ArrayList<>();
                for (DirectoryEntry entry : dir.getEntryList(dataId)) {
                    LOG.trace("Found entry {}", entry);
                    list.add(new DirectoryEntryInfo(rowCount++ % 2 == 0 ? "even" : "odd", entry, now));
                }
                entryKeys.add(new DirectoryInfo(dataId, list));
            }
        }

        if (!entryKeys.isEmpty()) {
            map.put("entrykeys", entryKeys);
        } else {
            LOG.debug("Found no entry keys");
        }

        List<PeerInfo> peers = new ArrayList<>();
        if (dir != null) {
            for (String peerkey : dir.getPeerDirectories()) {
                peers.add(new PeerInfo(peerkey, dir.isRemoteDirectoryAvailable(peerkey)));
            }
        }

        if (!peers.isEmpty()) {
            map.put("peers", peers);
        }


        if (!errors.isEmpty()) {
            map.put("error", true);
            map.put("errors", errors);
        }

        return map;
    }


    // TODO: move these to proper response objects
    public static class PeerInfo {
        final String link;
        final String peerkey;
        final String status;

        public PeerInfo(String peerkey, boolean healthy) {
            this.peerkey = peerkey;
            this.link =
                    KeyManipulator.getServiceHostUrl(peerkey) + "emissary/DumpDirectory.action?targetDir="
                            + KeyManipulator.getServiceLocation(peerkey);
            this.status = healthy ? "" : "DOWN";
        }
    }

    public static class DirectoryInfo {
        final String dataId;
        final List<DirectoryEntryInfo> entrylist;

        public DirectoryInfo(String dataId, List<DirectoryEntryInfo> list) {
            this.dataId = dataId;
            this.entrylist = list;
        }
    }


    public static class DirectoryEntryInfo {
        final String stripe;
        final String key;
        final int cost;
        final int quality;
        final int expense;
        final String age;

        public DirectoryEntryInfo(String stripe, DirectoryEntry entry, long now) {
            this.stripe = stripe;
            this.key = entry.getKey();
            this.cost = entry.getCost();
            this.quality = entry.getQuality();
            this.expense = entry.getExpense();
            long ago = now - entry.getAge();
            long hh = ago / 3600000;
            long mm = (ago % 3600000) / 60000;
            long ss = (ago % 60000) / 1000;
            String hhs = (hh < 10 ? "0" : "") + hh;
            String mms = (mm < 10 ? "0" : "") + mm;
            String sss = (ss < 10 ? "0" : "") + ss;
            this.age = hhs + ":" + mms + ":" + sss;
        }
    }
}
