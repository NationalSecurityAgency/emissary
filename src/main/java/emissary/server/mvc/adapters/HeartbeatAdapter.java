package emissary.server.mvc.adapters;

import java.util.ArrayList;
import java.util.List;

import emissary.client.EmissaryClient;
import emissary.client.EmissaryResponse;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.directory.KeyManipulator;
import emissary.place.IServiceProviderPlace;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;

/**
 * Stuff for adapting the Directory heartbeat calls to HTTP
 */
public class HeartbeatAdapter extends EmissaryClient {

    public static final String FROM_PLACE_NAME = "hbf";
    public static final String TO_PLACE_NAME = "hbt";

    /**
     * Process the heartbeat call coming remotely over HTTP request params onto the specified (local) directory place
     *
     * @return reference to the place when found, null otherwise
     */
    public IServiceProviderPlace inboundHeartbeat(final String fromName, final String toName) throws NamespaceException {
        if (toName == null) {
            throw new IllegalArgumentException("No place specified in msg from " + fromName);
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

        return place;
    }

    /**
     * Handle the packaging and sending of a hearbeat call to a remote directory
     *
     * @param fromPlace the key of the place location sending
     * @param toPlace the keyof the place location to receive
     * @return status of operation
     */
    public EmissaryResponse outboundHeartbeat(final String fromPlace, final String toPlace) {

        final String directoryUrl = KeyManipulator.getServiceHostURL(toPlace);
        final HttpPost method = new HttpPost(directoryUrl + CONTEXT + "/Heartbeat.action");

        final String loc = KeyManipulator.getServiceLocation(toPlace);

        final List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        nvps.add(new BasicNameValuePair(FROM_PLACE_NAME, fromPlace));
        nvps.add(new BasicNameValuePair(TO_PLACE_NAME, loc));
        method.setEntity(new UrlEncodedFormEntity(nvps, java.nio.charset.Charset.defaultCharset()));

        return send(method);
    }
}
