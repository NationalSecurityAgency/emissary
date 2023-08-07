package emissary.server.mvc.adapters;

import emissary.client.EmissaryClient;
import emissary.client.EmissaryResponse;
import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.EmissaryException;
import emissary.directory.DirectoryEntry;
import emissary.directory.DirectoryEntryMap;
import emissary.directory.DirectoryXmlContainer;
import emissary.directory.IRemoteDirectory;
import emissary.directory.KeyManipulator;
import emissary.log.MDCConstants;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

/**
 * Stuff for adapting the Directory calls to HTTP All the outbound methods supply the TARGET_DIRECTORY parameter that
 * matches the machine/port they are going to. This really only required in testing scenarios but is so helpful that it
 * seems worth the work.
 * <p>
 * A few of the outbound methods have no corresponding inbound methods because their answer is supplied by a jsp/worker
 * combination without any need to call into the directory on the remote side.
 */
public class DirectoryAdapter extends EmissaryClient {

    private static final Logger logger = LoggerFactory.getLogger(DirectoryAdapter.class);
    public static final String TARGET_DIRECTORY = "targetDir";
    public static final String PROXY_KEY_PARAMETER = "proxy";

    public static final String ADD_KEY = "dirAddKey";
    public static final String ADD_DESCRIPTION = "dirAddDesc";
    public static final String ADD_COST = "dirAddCost";
    public static final String ADD_QUALITY = "dirAddQual";
    public static final String ADD_PROPAGATION_FLAG = "dirAddPropFlag";
    public static final String FAILED_DIRECTORY_NAME = "dirFailName";
    public static final String DIRECTORY_NAME = "directoryName";
    public static final String ADD_ENTRIES = "dirAddEntries";
    public static final String DIRECTORY_KEY = "EMISSARY_DIRECTORY_SERVICES::STUDY";
    public static final String FILE_PICKUP_KEY = "INITIAL::INPUT";
    // These two parameters will cause each node to only have copies of its own places.
    // Greatly speeds up performance when not using the moveTo() functionality.
    private static boolean disableAddPlaces = true;
    private static boolean filterDirectoryEntryMap = true;


    protected static void configure() {
        try {
            final Configurator c = ConfigUtil.getConfigInfo(DirectoryAdapter.class);
            disableAddPlaces = c.findBooleanEntry("DISABLE_ADD_PLACES", true);
            filterDirectoryEntryMap = c.findBooleanEntry("FILTER_DIRECTORY_ENTRY_MAP", true);
        } catch (IOException e) {
            logger.info("Failed to find or read DirectoryAdapter config. Using default values.");
            logger.debug(e.toString());
        }
    }

    /**
     * Handle the packaging and sending of an addPlaces call to a remote directory. Sends multiple keys on the same place
     * with the same cost/quality and description if the description, cost and quality lists are only size 1. Uses a
     * distinct description/cost/quality for each key when there are enough values
     *
     * @param parentDirectory the url portion of the parent directory location
     * @param entryList the list of directory entries to add
     * @param propagating true if going downstream
     * @return status of operation
     */
    public EmissaryResponse outboundAddPlaces(final String parentDirectory, final List<DirectoryEntry> entryList, final boolean propagating) {
        if (disableAddPlaces) {
            BasicClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "Not accepting remote add places");
            response.setEntity(EntityBuilder.create().setText("").setContentEncoding(MediaType.TEXT_PLAIN).build());
            return new EmissaryResponse(response);
        } else {
            final String parentDirectoryUrl = KeyManipulator.getServiceHostURL(parentDirectory);
            final HttpPost method = createHttpPost(parentDirectoryUrl, CONTEXT, "/RegisterPlace.action");

            final String parentLoc = KeyManipulator.getServiceLocation(parentDirectory);
            // Separate it out into lists
            final List<String> keyList = new ArrayList<>();
            final List<String> descList = new ArrayList<>();
            final List<Integer> costList = new ArrayList<>();
            final List<Integer> qualityList = new ArrayList<>();
            for (final DirectoryEntry d : entryList) {
                keyList.add(d.getKey());
                descList.add(d.getDescription());
                costList.add(d.getCost());
                qualityList.add(d.getQuality());
            }

            final List<NameValuePair> nvps = new ArrayList<>();
            nvps.add(new BasicNameValuePair(TARGET_DIRECTORY, parentLoc));

            for (int count = 0; count < keyList.size(); count++) {
                nvps.add(new BasicNameValuePair(ADD_KEY + count, keyList.get(count)));
                // possibly use the single desc/cost/qual for each key
                if (descList.size() > count) {
                    String desc = descList.get(count);
                    if (desc == null) {
                        desc = "No description provided";
                    }
                    nvps.add(new BasicNameValuePair(ADD_DESCRIPTION + count, desc));
                }
                if (costList.size() > count) {
                    nvps.add(new BasicNameValuePair(ADD_COST + count, costList.get(count).toString()));
                }
                if (qualityList.size() > count) {
                    nvps.add(new BasicNameValuePair(ADD_QUALITY + count, qualityList.get(count).toString()));
                }
            }
            nvps.add(new BasicNameValuePair(ADD_PROPAGATION_FLAG, Boolean.toString(propagating)));
            method.setEntity(new UrlEncodedFormEntity(nvps, StandardCharsets.UTF_8));
            return send(method);
        }
    }

    /**
     * Handle the packaging and sending of an removePlaces call to a remote directory
     *
     * @param directory the url portion of the remote directory location
     * @param key list of keys to remove (four-tuples)
     * @param propagating true if doing down stream
     * @return status of operation
     */
    public EmissaryResponse outboundRemovePlaces(final String directory, final List<String> key, final boolean propagating) {
        final String directoryUrl = KeyManipulator.getServiceHostURL(directory);
        final HttpPost method = createHttpPost(directoryUrl, CONTEXT, "/DeregisterPlace.action");

        final String parentLoc = KeyManipulator.getServiceLocation(directory);

        final List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair(TARGET_DIRECTORY, parentLoc));

        int count = 0;
        for (String k : key) {
            nvps.add(new BasicNameValuePair(ADD_KEY + (count++), k));
        }
        nvps.add(new BasicNameValuePair(ADD_PROPAGATION_FLAG, Boolean.toString(propagating)));
        method.setEntity(new UrlEncodedFormEntity(nvps, StandardCharsets.UTF_8));
        return send(method);
    }

    /**
     * Handle the packaging and sending of an addPlaces call to a remote directory
     *
     * @param directory the url portion of the destination directory
     * @param failKey key of the directory that failed
     * @param permanent true from normal deregistration
     * @return status of operation
     */
    public EmissaryResponse outboundFailDirectory(final String directory, final String failKey, final boolean permanent) {
        final String directoryUrl = KeyManipulator.getServiceHostURL(directory);
        final HttpPost method = createHttpPost(directoryUrl, CONTEXT, "/FailDirectory.action");

        final String parentLoc = KeyManipulator.getServiceLocation(directory);
        final List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair(TARGET_DIRECTORY, parentLoc));
        nvps.add(new BasicNameValuePair(FAILED_DIRECTORY_NAME, failKey));
        nvps.add(new BasicNameValuePair(ADD_PROPAGATION_FLAG, Boolean.toString(permanent)));
        method.setEntity(new UrlEncodedFormEntity(nvps, StandardCharsets.UTF_8));
        return send(method);
    }

    /**
     * Process the failDirectory call coming remotely over HTTP request params onto the specified (local) directory place.
     *
     * @param req the inbound request object
     */
    public boolean inboundFailDirectory(final HttpServletRequest req) {

        final String dir = RequestUtil.getParameter(req, TARGET_DIRECTORY);
        final IRemoteDirectory localDirectory = getLocalDirectory(dir);

        if (localDirectory == null) {
            throw new IllegalArgumentException("No local directory found using name " + dir);
        }

        final String remoteDir = RequestUtil.getParameter(req, FAILED_DIRECTORY_NAME);
        int count;

        MDC.put(MDCConstants.SERVICE_LOCATION, KeyManipulator.getServiceLocation(localDirectory.getKey()));
        try {
            count = localDirectory.irdFailDirectory(remoteDir, RequestUtil.getBooleanParam(req, ADD_PROPAGATION_FLAG));
        } finally {
            MDC.remove(MDCConstants.SERVICE_LOCATION);
        }

        logger.debug("Modified {} entries from {} due to failure of remote {}", count, dir, remoteDir);

        return true;
    }

    /**
     * Request the XML directory entry markup from a remote directory peer and turn the response XML into a Map of
     * String,DirectoryEntryList for return
     *
     * @param key the key of the remote directory to request the zone transfer from
     * @param myKey the key of the local requesting the zone or null if none
     * @return DirectoryEntryList map from the remote side
     * @throws EmissaryException if remote returns an error
     */
    public DirectoryEntryMap outboundZoneTransfer(final String key, final String myKey) throws EmissaryException {
        return zoneTransfer(key, myKey, "/TransferDirectory.action");
    }

    /**
     * Request the XML directory entry markup from a remote directory peer and turn the response XML into a
     * DirectoryEntryMap for return. Register the caller as a peer of the destination as part of the transfer.
     *
     * @param key the key of the remote directory to request the zone transfer from
     * @param peerKey the key of the peer requesting the zone or null if none
     * @return DirectoryEntryList map from the remote side
     * @throws EmissaryException if remote returns an error
     */
    public DirectoryEntryMap outboundRegisterPeer(final String key, final String peerKey) throws EmissaryException {
        return zoneTransfer(key, peerKey, "/RegisterPeer.action");
    }

    /**
     * Request the XML directory entry markup from a remote directory peer and turn the response XML into a Map of
     * String,DirectoryEntryList for return.
     *
     * @param key the key of the remote directory to request the zone transfer from
     * @param myKey the key of the local dir requesting the zone or null if none
     * @param action the action to use in the request
     * @return DirectoryEntryList map from the remote side
     * @throws EmissaryException if remote returns an error
     */
    private DirectoryEntryMap zoneTransfer(final String key, @Nullable final String myKey, final String action) throws EmissaryException {
        final HttpPost method = createHttpPost(KeyManipulator.getServiceHostURL(key), CONTEXT, action);

        final String parentLoc = KeyManipulator.getServiceLocation(key);
        final List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair(TARGET_DIRECTORY, parentLoc));

        if (myKey != null) {
            nvps.add(new BasicNameValuePair(DIRECTORY_NAME, myKey));
        }

        method.setEntity(new UrlEncodedFormEntity(nvps, StandardCharsets.UTF_8));

        DirectoryEntryMap map = null;
        EmissaryResponse ws = null;

        try {
            ws = send(method);
            // TODO Consider putting this method in the response
            if (ws.getStatus() != HttpStatus.SC_OK) {
                logger.debug("Unable to contact remote directory for zone transfer: {}", ws.getContentString());
            } else {
                map = DirectoryXmlContainer.buildEntryListMap(ws.getContentString());
            }
        } catch (Exception ex) {
            logger.debug("Unable to contact remote directory for " + "zone transfer " + key, ex);
        }

        if (map == null) {
            String errMsg = "Unable to perform zone transfer to " + key + ": received map is null";
            if (ws != null) {
                errMsg += ", isError=" + ws.getStatus() + ", msgBody=" + ws.getContentString();
            }
            throw new EmissaryException(errMsg);
        }

        // This ensures each node only has knowledge of all DirectoryPlace and FilePickupPlace entries
        if (filterDirectoryEntryMap) {
            return filterDirectoryEntryMap(map);
        } else {
            return map;
        }
    }

    private DirectoryEntryMap filterDirectoryEntryMap(DirectoryEntryMap map) {
        DirectoryEntryMap filtered = new DirectoryEntryMap();
        if (map.containsKey(DIRECTORY_KEY)) {
            filtered.put(DIRECTORY_KEY, map.get(DIRECTORY_KEY));
        }
        if (map.containsKey(FILE_PICKUP_KEY)) {
            filtered.put(FILE_PICKUP_KEY, map.get(FILE_PICKUP_KEY));
        }
        return filtered;
    }

    /**
     * Look up the local directory using one of two methods. The easier method almost always works, the case where it
     * doesn't in when there are multiple configured Emissary nodes on the same local JVM through a single jetty with
     * multiple Listeners. This is a testing scenario, but it is helpful to keep supporting it, so we have good test
     * coverage.
     *
     * @param name name of the local directory or null for default
     */
    private IRemoteDirectory getLocalDirectory(final String name) {
        return new IRemoteDirectory.Lookup().getLocalDirectory(name);
    }
}
