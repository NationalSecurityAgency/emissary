package emissary.server.mvc.adapters;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

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
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Stuff for adapting the Directory calls to HTTP All of the outbound methods supply the TARGET_DIRECTORY parameter that
 * matches the machine/port they are going to. This really only required in testing scenarios but is so helpful that it
 * seems worth the work.
 *
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
            logger.debug("{}", e);
        }
    }

    /**
     * Process the addPlaces call coming remotely over HTTP request params onto the specified (local) directory place.
     *
     * @param req the inbound request object
     */
    public boolean inboundAddPlaces(final HttpServletRequest req) {
        return inboundAddPlaces(req, RequestUtil.getParameter(req, TARGET_DIRECTORY));
    }

    public boolean inboundAddPlaces(final HttpServletRequest req, final String dir) {
        if (disableAddPlaces) {
            return true;
        } else {
            final AddPlacesRequestBean bean = new AddPlacesRequestBean(req);
            final IRemoteDirectory directory = getLocalDirectory(dir);

            if (directory == null) {
                throw new IllegalArgumentException("No directory found using name " + dir);
            }

            MDC.put(MDCConstants.SERVICE_LOCATION, KeyManipulator.getServiceLocation(directory.getKey()));
            try {
                directory.irdAddPlaces(bean.getEntries(), bean.isPropagating());
            } finally {
                MDC.remove(MDCConstants.SERVICE_LOCATION);
            }
            return true;
        }
    }

    /**
     * Process the removePlaces call coming remotely over HTTP request params onto the specified (local) directory place.
     *
     * @param req the inbound request object
     */
    public boolean inboundRemovePlaces(final HttpServletRequest req) {
        final String dir = RequestUtil.getParameter(req, TARGET_DIRECTORY);
        final AddPlacesRequestBean bean = new AddPlacesRequestBean(req, false);
        final IRemoteDirectory directory = getLocalDirectory(dir);

        if (directory == null) {
            throw new IllegalArgumentException("No directory found using name " + dir);
        }

        MDC.put(MDCConstants.SERVICE_LOCATION, KeyManipulator.getServiceLocation(directory.getKey()));
        try {
            directory.irdRemovePlaces(bean.getKeys(), bean.isPropagating());
        } finally {
            MDC.remove(MDCConstants.SERVICE_LOCATION);
        }
        return true;
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
            BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "Not accepting remote add places");
            response.setEntity(EntityBuilder.create().setText("").setContentEncoding(MediaType.TEXT_PLAIN).build());
            return new EmissaryResponse(response);
        } else {
            final String parentDirectoryUrl = KeyManipulator.getServiceHostURL(parentDirectory);
            final HttpPost method = createHttpPost(parentDirectoryUrl, CONTEXT, "/RegisterPlace.action");

            final String parentLoc = KeyManipulator.getServiceLocation(parentDirectory);
            // Separate it out into lists
            final List<String> keyList = new ArrayList<String>();
            final List<String> descList = new ArrayList<String>();
            final List<Integer> costList = new ArrayList<Integer>();
            final List<Integer> qualityList = new ArrayList<Integer>();
            for (final DirectoryEntry d : entryList) {
                keyList.add(d.getKey());
                descList.add(d.getDescription());
                costList.add(Integer.valueOf(d.getCost()));
                qualityList.add(Integer.valueOf(d.getQuality()));
            }

            final List<NameValuePair> nvps = new ArrayList<NameValuePair>();
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
            method.setEntity(new UrlEncodedFormEntity(nvps, Charset.defaultCharset()));
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

        final List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        nvps.add(new BasicNameValuePair(TARGET_DIRECTORY, parentLoc));

        int count = 0;
        for (String k : key) {
            nvps.add(new BasicNameValuePair(ADD_KEY + (count++), k));
        }
        nvps.add(new BasicNameValuePair(ADD_PROPAGATION_FLAG, Boolean.toString(propagating)));
        method.setEntity(new UrlEncodedFormEntity(nvps, Charset.defaultCharset()));
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
        final List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        nvps.add(new BasicNameValuePair(TARGET_DIRECTORY, parentLoc));
        nvps.add(new BasicNameValuePair(FAILED_DIRECTORY_NAME, failKey));
        nvps.add(new BasicNameValuePair(ADD_PROPAGATION_FLAG, Boolean.toString(permanent)));
        method.setEntity(new UrlEncodedFormEntity(nvps, Charset.defaultCharset()));
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
        int count = 0;

        MDC.put(MDCConstants.SERVICE_LOCATION, KeyManipulator.getServiceLocation(localDirectory.getKey()));
        try {
            count = localDirectory.irdFailRemoteDirectory(remoteDir, RequestUtil.getBooleanParam(req, ADD_PROPAGATION_FLAG));
        } finally {
            MDC.remove(MDCConstants.SERVICE_LOCATION);
        }

        logger.debug("Modified " + count + " entries from " + dir + " due to failure of remote " + remoteDir);

        return true;
    }

    /**
     * Call addChildDirectory on locally specified directory
     *
     * @param req the inbound request object
     */
    public void inboundAddChildDirectory(final HttpServletRequest req) {

        final String parent = RequestUtil.getParameter(req, TARGET_DIRECTORY);
        final String child = RequestUtil.getParameter(req, DIRECTORY_NAME);

        if (child == null) {
            throw new IllegalArgumentException("Missing required parameters");
        }

        final IRemoteDirectory localDirectory = getLocalDirectory(parent);

        if (localDirectory == null) {
            throw new IllegalArgumentException("No parent directory found using name " + parent);
        }

        MDC.put(MDCConstants.SERVICE_LOCATION, KeyManipulator.getServiceLocation(localDirectory.getKey()));
        try {
            localDirectory.irdAddChildDirectory(child);
        } finally {
            MDC.remove(MDCConstants.SERVICE_LOCATION);
        }
    }

    /**
     * Send remote addChildDirectory call
     *
     * @param parent the directory we want to register with
     * @param child the calling directory
     */
    public EmissaryResponse outboundAddChildDirectory(final String parent, final String child) {
        final HttpPost method = createHttpPost(KeyManipulator.getServiceHostURL(parent), CONTEXT, "/AddChildDirectory.action");

        final String parentLoc = KeyManipulator.getServiceLocation(parent);

        final List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        nvps.add(new BasicNameValuePair(TARGET_DIRECTORY, parentLoc));
        nvps.add(new BasicNameValuePair(DIRECTORY_NAME, child));
        method.setEntity(new UrlEncodedFormEntity(nvps, Charset.defaultCharset()));
        return send(method);
    }

    /**
     * Request the XML directory entry markup from a remote directory peer or child and turn the response XML into a Map of
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
     * Request the XML directory entry markup from a remote directory peer or child and turn the response XML into a Map of
     * String,DirectoryEntryList for return.
     *
     * @param key the key of the remote directory to request the zone transfer from
     * @param myKey the key of the local dir requesting the zone or null if none
     * @param action the action to use in the request
     * @return DirectoryEntryList map from the remote side
     * @throws EmissaryException if remote returns an error
     */
    private DirectoryEntryMap zoneTransfer(final String key, final String myKey, final String action) throws EmissaryException {
        final HttpPost method = createHttpPost(KeyManipulator.getServiceHostURL(key), CONTEXT, action);

        final String parentLoc = KeyManipulator.getServiceLocation(key);
        final List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        nvps.add(new BasicNameValuePair(TARGET_DIRECTORY, parentLoc));

        if (myKey != null) {
            nvps.add(new BasicNameValuePair(DIRECTORY_NAME, myKey));
        }

        method.setEntity(new UrlEncodedFormEntity(nvps, Charset.defaultCharset()));

        DirectoryEntryMap map = null;
        EmissaryResponse ws = null;

        try {
            ws = send(method);
            // TODO Consider putting this method in the response
            if (ws.getStatus() != HttpStatus.SC_OK) {
                logger.debug("Unable to contact remote directory for " + "zone transfer: " + ws.getContentString());
            } else {
                map = DirectoryXmlContainer.buildEntryListMap(ws.getContentString());
            }
        } catch (Exception ex) {
            logger.debug("Unable to contact remote directory for " + "zone transfer " + key, ex);
        }

        if (map == null) {
            throw new EmissaryException("Unable to perform zone transfer to " + key + ": received map is null, isError=" + ws.getStatus()
                    + ", msgBody=" + ws.getContentString());
        }

        // This ensures each node only has knowledge of all DirectoryPlace and FilePickupPlace entries
        if (filterDirectoryEntryMap) {
            DirectoryEntryMap filtered = filterDirectoryEntryMap(map);
            return filtered;
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
     * doesn't in when there are multilpe configured Emissary nodes on the same local JVM through a single jetty with
     * multiple Listeners. This is a testing scenario but it is helpful to keep supporting it so we have good test coverage.
     *
     * @param name name of the local directory or null for default
     */
    private IRemoteDirectory getLocalDirectory(final String name) {
        return new IRemoteDirectory.Lookup().getLocalDirectory(name);
    }

    /**
     * Register caller as a peer in local directory
     *
     * @param req the inbound request object
     */
    public boolean inboundRegisterPeer(final HttpServletRequest req) {
        final String peerKey = RequestUtil.getParameter(req, DIRECTORY_NAME);
        final String localDir = RequestUtil.getParameter(req, TARGET_DIRECTORY);
        final IRemoteDirectory dir = getLocalDirectory(localDir);
        if (dir != null) {
            final Set<String> set = new HashSet<String>();
            set.add(KeyManipulator.getDefaultDirectoryKey(peerKey));

            MDC.put(MDCConstants.SERVICE_LOCATION, KeyManipulator.getServiceLocation(dir.getKey()));
            try {
                dir.irdAddPeerDirectories(set);
            } finally {
                MDC.remove(MDCConstants.SERVICE_LOCATION);
            }
            return true;
        }
        return false;
    }


    /**
     * Helper utility class to collect arguments for addPlaces call
     */
    static class AddPlacesRequestBean {
        List<DirectoryEntry> entries;
        List<String> keys;

        boolean permanent = false;

        AddPlacesRequestBean(final HttpServletRequest req) {
            this(req, true);
        }

        AddPlacesRequestBean(final HttpServletRequest req, final boolean requireAdd) {
            if (requireAdd) {
                loadBaseAddFields(req);
            } else {
                loadBaseFields(req);
            }
        }

        /**
         * Load fields pertaining to add
         */
        @SuppressWarnings("unchecked")
        private void loadBaseAddFields(final HttpServletRequest req) {
            final List<DirectoryEntry> l = (List<DirectoryEntry>) req.getAttribute(ADD_ENTRIES);
            if (l == null) {
                this.keys = new ArrayList<String>();
                this.entries = new ArrayList<DirectoryEntry>();

                // Get all the parameter sets in numbered order
                int count = 0;
                // until we get a null
                while (true) {
                    final String p = req.getParameter(ADD_KEY + count);
                    if (p == null) {
                        break; // no more keys
                    }
                    String d = req.getParameter(ADD_DESCRIPTION + count);
                    String c = req.getParameter(ADD_COST + count);
                    String q = req.getParameter(ADD_QUALITY + count);

                    // desc, cost, qual are only repeated optionally, use first if not enough
                    if (d == null) {
                        d = req.getParameter(ADD_DESCRIPTION + 0);
                    }
                    if (c == null) {
                        c = req.getParameter(ADD_COST + 0);
                    }
                    if (q == null) {
                        q = req.getParameter(ADD_QUALITY + 0);
                    }

                    final DirectoryEntry entry = new DirectoryEntry(p, d, Integer.parseInt(c), Integer.parseInt(q));
                    this.entries.add(entry);
                    count++;
                }
                logger.debug("Rebuilt " + this.entries.size() + " add entries from request");
            } else {
                this.entries = l; // already constructed
            }

            this.permanent = RequestUtil.getBooleanParam(req, ADD_PROPAGATION_FLAG);

            if (this.entries == null || this.entries.size() == 0) {
                throw new IllegalArgumentException("Missing required " + ADD_KEY);
            }
        }

        /**
         * Get fields for delete
         */
        private void loadBaseFields(final HttpServletRequest req) {
            this.keys = new ArrayList<String>();
            int count = 0;

            // until we get a null
            while (true) {
                final String p = req.getParameter(ADD_KEY + count);
                if (p == null) {
                    break;
                }
                this.keys.add(p);
                count++;
            }
        }

        /**
         * Gets the value of permanent
         *
         * @return the value of permanent
         */
        public boolean isPermanent() {
            return this.permanent;
        }

        /**
         * Alias for the boolean flag. Hack.
         */
        public boolean isPropagating() {
            return this.permanent;
        }

        /**
         * Sets the value of permanent
         *
         * @param argPermanent Value to assign to this.permanent
         */
        public void setPermanent(final boolean argPermanent) {
            this.permanent = argPermanent;
        }

        /**
         * Gets the value of key
         *
         * @return the value of key
         */
        public List<String> getKeys() {
            return this.keys;
        }

        /**
         * Sets the value of key
         *
         * @param argKey Value to assign to this.key
         */
        public void setKeys(final List<String> argKey) {
            this.keys = argKey;
        }

        /**
         * Get the entries
         */
        public List<DirectoryEntry> getEntries() {
            return this.entries;
        }

        /**
         * Set the entries
         */
        public void setEntries(final List<DirectoryEntry> l) {
            this.entries = l;
        }

    }
}
