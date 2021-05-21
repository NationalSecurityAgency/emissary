package emissary.server.mvc.adapters;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import emissary.client.EmissaryClient;
import emissary.client.EmissaryResponse;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.directory.KeyManipulator;
import emissary.pickup.IPickUpSpace;
import emissary.pickup.WorkBundle;
import emissary.pickup.WorkSpace;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stuff for adapting the WorkSpace remote call to HTTP
 */
public class WorkSpaceAdapter extends EmissaryClient {

    private static final Logger logger = LoggerFactory.getLogger(WorkSpaceAdapter.class);

    public static final String CLIENT_NAME = "pickUpClientName";
    public static final String SPACE_NAME = "workSpaceName";
    public static final String WORK_BUNDLE_OBJ = "tpObj";
    public static final String WORK_BUNDLE_XML = "tpXml";
    public static final String WORK_BUNDLE_ID = "tpId";
    public static final String WORK_BUNDLE_STATUS = "tpStatus";
    public static final String DATA_IDENTIFIER = "tdataId";


    /**
     * Process the enque coming remotely over HTTP request params onto the specified (local) pickup client place
     */
    public boolean inboundEnque(final HttpServletRequest req) throws NamespaceException {

        logger.debug("TPA incoming elements! check prio={}", Thread.currentThread().getPriority());

        // Parse parameters
        final EnqueRequestBean bean = new EnqueRequestBean(req);

        // Look up the place reference
        final String nsName = KeyManipulator.getServiceLocation(bean.getPlace());
        final IPickUpSpace place = lookupPlace(nsName);
        if (place == null) {
            throw new IllegalArgumentException("No client place found using name " + bean.getPlace());
        }

        return place.enque(bean.getPaths());
    }

    private Object lookup(final String name) throws NamespaceException {
        final String nsName = KeyManipulator.getServiceLocation(name);
        try {
            return Namespace.lookup(nsName);
        } catch (NamespaceException ne) {
            logger.error("Could not lookup place using " + nsName, ne);
            throw ne;
        }
    }

    private IPickUpSpace lookupPlace(final String name) throws NamespaceException {
        return (IPickUpSpace) lookup(name);
    }

    private WorkSpace lookupSpace(final String name) throws NamespaceException {
        return (WorkSpace) lookup(name);
    }


    /**
     * Handle the packaging and sending of a enque call to a remote pickup client place
     * 
     * @param place the serviceLocation portion of the place
     * @return status of operation
     */
    public EmissaryResponse outboundEnque(final String place, final WorkBundle path) {

        final String placeUrl = KeyManipulator.getServiceHostURL(place);
        final HttpPost method = createHttpPost(placeUrl, CONTEXT, "/WorkSpaceClientEnqueue.action");
        final List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        nvps.add(new BasicNameValuePair(CLIENT_NAME, place));

        String pathData = null;
        try {
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            final ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(path);
            oos.close();
            try {
                pathData = bos.toString("8859_1");
            } catch (UnsupportedEncodingException e) {
                pathData = bos.toString();
            }
        } catch (Exception e) {
            logger.error("Cannot serialize WorkBundle object", e);
            throw new IllegalArgumentException("Cannot serialize WorkBundle object: " + e.getMessage());
        }

        nvps.add(new BasicNameValuePair(WORK_BUNDLE_OBJ, pathData));
        logger.debug("Sending {} file names to {} as {}", path.size(), place, path);

        method.setEntity(new UrlEncodedFormEntity(nvps, java.nio.charset.Charset.forName("8859_1")));
        return send(method);
    }

    /**
     * Outbound open tells a remote WorkSpace to start pulling data
     * 
     * @param place the remote place to contact
     * @param space the location of the work distributor
     */
    public EmissaryResponse outboundOpenWorkSpace(final String place, final String space) {

        final String placeUrl = KeyManipulator.getServiceHostURL(place);
        final HttpPost method = createHttpPost(placeUrl, CONTEXT, "/WorkSpaceClientOpenWorkSpace.action");

        final List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        nvps.add(new BasicNameValuePair(CLIENT_NAME, place));
        nvps.add(new BasicNameValuePair(SPACE_NAME, space));
        method.setEntity(new UrlEncodedFormEntity(nvps, java.nio.charset.Charset.defaultCharset()));
        // set a timeout in case a node is unresponsive
        method.setConfig(RequestConfig.custom().setConnectTimeout(60000).setSocketTimeout(60000).build());

        return send(method);
    }

    /**
     * Inbound open tells this WorkSpace to start pulling data
     */
    public boolean inboundOpenWorkSpace(final HttpServletRequest req) throws NamespaceException {

        final String placeName = RequestUtil.getParameter(req, CLIENT_NAME);
        final String spaceName = RequestUtil.getParameter(req, SPACE_NAME);

        // Look up the place reference
        final IPickUpSpace place = lookupPlace(placeName);
        if (place == null) {
            throw new IllegalArgumentException("No client place found using name " + placeName);
        }

        logger.info("Notified {} to open space at {}", placeName, spaceName);
        place.openSpace(spaceName);
        return true;
    }

    /**
     * Outbound take grabs a WorkBundle from remote WorkSpace
     * 
     * @param space the remote space to contact
     * @param place the name of the requesting place
     */
    public WorkBundle outboundWorkSpaceTake(final String space, final String place) {

        final String placeUrl = KeyManipulator.getServiceHostURL(space);
        final HttpPost method = createHttpPost(placeUrl, CONTEXT, "/WorkSpaceClientSpaceTake.action");

        final List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        nvps.add(new BasicNameValuePair(CLIENT_NAME, place));
        nvps.add(new BasicNameValuePair(SPACE_NAME, space));

        method.setEntity(new UrlEncodedFormEntity(nvps, java.nio.charset.Charset.defaultCharset()));
        final EmissaryResponse status = send(method);

        WorkBundle path = null;
        // TODO Look at putting this method in the EmissaryResponse
        if (status.getStatus() != HttpStatus.SC_OK) {
            logger.debug("Take from space {} was an error: {}", space, status.getContentString());
        } else {
            path = WorkBundle.buildWorkBundle(status.getContentString());
        }
        return path;
    }

    /**
     * Inbound call to get a bundle from the space
     * 
     * @param req the http request
     */
    public WorkBundle inboundSpaceTake(final HttpServletRequest req) throws NamespaceException {
        final String spaceName = RequestUtil.getParameter(req, SPACE_NAME);

        // Look up the place reference
        final WorkSpace space = lookupSpace(spaceName);
        if (space == null) {
            throw new IllegalArgumentException("No WorkSpace found using name " + spaceName);
        }

        String placeName = RequestUtil.getParameter(req, CLIENT_NAME);
        if (placeName == null) {
            placeName = req.getRemoteHost();
        }
        return space.take(placeName);
    }

    /**
     * Outbound notice that bundle was completed
     * 
     * @param space the remote space to contact
     * @param place the name of the notifying place
     * @param bundleId the id of the bundle that was completed
     * @param itWorked status of the processing
     * @return true if the message was sent
     */
    public boolean outboundBundleCompletion(final String space, final String place, final String bundleId, final boolean itWorked) {
        final String placeUrl = KeyManipulator.getServiceHostURL(space);
        final HttpPost method = createHttpPost(placeUrl, CONTEXT, "/WorkBundleCompleted.action");

        final List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        nvps.add(new BasicNameValuePair(CLIENT_NAME, place));
        nvps.add(new BasicNameValuePair(SPACE_NAME, space));
        nvps.add(new BasicNameValuePair(WORK_BUNDLE_ID, bundleId));
        nvps.add(new BasicNameValuePair(WORK_BUNDLE_STATUS, Boolean.toString(itWorked)));
        method.setEntity(new UrlEncodedFormEntity(nvps, java.nio.charset.Charset.defaultCharset()));
        final EmissaryResponse status = send(method);
        // TODO Look at putting this method in the EmissaryResponse
        return (status.getStatus() == HttpStatus.SC_OK);
    }

    /**
     * Inbound notice that a bundle was completed
     * 
     * @param req the http request
     */
    public boolean inboundBundleCompletion(final HttpServletRequest req) throws NamespaceException {
        final String spaceName = RequestUtil.getParameter(req, SPACE_NAME);

        // Look up the place reference
        final WorkSpace space = lookupSpace(spaceName);
        if (space == null) {
            throw new IllegalArgumentException("No WorkSpace found using name " + spaceName);
        }

        String placeName = RequestUtil.getParameter(req, CLIENT_NAME);
        if (placeName == null) {
            placeName = req.getRemoteHost();
        }

        final String bundleId = RequestUtil.getParameter(req, WORK_BUNDLE_ID);
        final boolean itWorked = RequestUtil.getBooleanParam(req, WORK_BUNDLE_STATUS);
        if (bundleId == null) {
            throw new IllegalArgumentException("Notice of bundle completion with no bundle id");
        }

        return space.workCompleted(placeName, bundleId, itWorked);
    }

    /**
     * Helper utility class to collect arguments for enqueue call
     */
    static class EnqueRequestBean {

        String place;
        WorkBundle paths;

        EnqueRequestBean(final HttpServletRequest req) {

            setPlace(RequestUtil.getParameter(req, CLIENT_NAME));
            if (getPlace() == null) {
                throw new IllegalArgumentException("No 'place' specified");
            }

            setPaths(RequestUtil.getParameter(req, WORK_BUNDLE_OBJ));

        }

        /**
         * Gets the value of place
         *
         * @return the value of place
         */
        public String getPlace() {
            return this.place;
        }

        /**
         * Sets the value of place
         *
         * @param argPlace Value to assign to this.place
         */
        public void setPlace(final String argPlace) {
            this.place = argPlace;
        }

        /**
         * Sets the WorkBundle object from serialized data
         */
        void setPaths(final String s) {
            try {
                final DataInputStream dis = new DataInputStream(new ByteArrayInputStream(s.getBytes("8859_1")));
                this.paths = WorkBundle.readFromStream(dis);
            } catch (Exception e) {
                logger.error("Cannot deserialize WorkBundle using {} bytes", s.length(), e);
                throw new IllegalArgumentException("Cannot deserialize WorkBundle");
            }
        }

        /**
         * Sets the value of Paths
         *
         * @param argPaths Value to assign to this.Paths
         */
        public void setPaths(final WorkBundle argPaths) {
            this.paths = argPaths;
        }

        /**
         * Gets the value of Paths
         *
         * @return the value of Paths
         */
        public WorkBundle getPaths() {
            return this.paths;
        }
    }
}
