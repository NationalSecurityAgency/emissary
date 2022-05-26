package emissary.server.mvc.adapters;

import java.util.ArrayList;
import java.util.List;

import emissary.client.EmissaryClient;
import emissary.client.EmissaryResponse;
import emissary.directory.KeyManipulator;
import emissary.pickup.WorkBundle;
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
    public static final String WORK_BUNDLE_ID = "tpId";
    public static final String WORK_BUNDLE_STATUS = "tpStatus";

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

}
