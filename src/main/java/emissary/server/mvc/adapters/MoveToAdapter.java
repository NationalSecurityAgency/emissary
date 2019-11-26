package emissary.server.mvc.adapters;

import java.io.IOException;
import java.nio.charset.Charset;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

import emissary.client.EmissaryClient;
import emissary.client.EmissaryResponse;
import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.EmissaryException;
import emissary.core.IMobileAgent;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.directory.DirectoryEntry;
import emissary.directory.KeyManipulator;
import emissary.log.MDCConstants;
import emissary.place.IServiceProviderPlace;
import emissary.pool.AgentPool;
import emissary.util.PayloadUtil;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Facilitate moving agents over HTTP
 */
public class MoveToAdapter extends EmissaryClient {

    private static final Logger logger = LoggerFactory.getLogger(MoveToAdapter.class);

    public static final String PLACE_NAME = "mtPlaceName";
    public static final String AGENT_OBJECT = "mtAgentObject";
    public static final String AGENT_SERIAL = "mtAgentSerialized";
    public static final String MOVE_ERROR_COUNT = "agentMoveErrorCount";
    public static final String ITINERARY_ITEM = "agentItineraryItem";

    private static String COOKIE_NAME = "PLACE";
    private static String COOKIE_DOMAIN = "www.example.com";
    private static String COOKIE_PATH = "/";
    private static String VIRTUAL_MOVETO_PROTOCOL = "http";
    private static String VIRTUAL_MOVETO_ADDR = null;

    // Initialize the VIRTUAL_MOVETO_ADDR IP and port once on class load
    static {
        reconfigure();
    }

    /**
     * Configure static stuff for this class Config items read here are
     * 
     * <ul>
     * <li>VIRTUAL_MOVETO_ADDR: default null for redirecting moveTo calls when dynamic loadbalancing is available</li>
     * <li>VIRTUAL_MOVETO_PROTOCOL: default http protocol for loadbalancing redirect request</li>
     * <li>VIRTUAL_COOKIE_NAME: name of coookie holding real destination place, default=PLACE</li>
     * <li>VIRTUAL_COOKIE_DOMAIN: name of cookie domain for redirect cookie, default=www.example.com</li>
     * <li>VIRTUAL_COOKIE_PATH: path set on redirect cookie, default=/</li>
     * </ul>
     */
    public static void reconfigure() {
        try {
            final Configurator conf = ConfigUtil.getConfigInfo(AgentPool.class);
            VIRTUAL_MOVETO_ADDR = conf.findStringEntry("VIRTUAL_MOVETO_ADDR", null);
            VIRTUAL_MOVETO_PROTOCOL = conf.findStringEntry("VIRTUAL_MOVETO_PROTOCOL", "http");
            COOKIE_NAME = conf.findStringEntry("VIRTUAL_COOKIE_NAME", COOKIE_NAME);
            COOKIE_DOMAIN = conf.findStringEntry("VIRTUAL_COOKIE_DOMAIN", COOKIE_DOMAIN);
            COOKIE_PATH = conf.findStringEntry("VIRTUAL_COOKIE_PATH", COOKIE_PATH);
        } catch (IOException e) {
            logger.warn("Cannot read config file, virtual hosting capability not available " + e.getMessage());
        }
    }

    /**
     * Public constructor
     */
    public MoveToAdapter() {}

    /**
     * moveTo call arriving on this server, translate to real call
     * 
     * @param req the HttpRequest with all needed parameters
     */
    public boolean inboundMoveTo(final HttpServletRequest req) throws RemoteException, NamespaceException {

        final MoveToRequestBean bean = new MoveToRequestBean(req);
        String placeKey = KeyManipulator.getServiceLocation(bean.getPlaceName());

        // Rewrite the placeKey to ignore the machine:port part of the key
        // since we may have come here on a redirect from the load
        // balancing module and it may not be the right host name anymore
        if (VIRTUAL_MOVETO_ADDR != null) {
            placeKey = KeyManipulator.getServiceClassname(bean.getPlaceName());
        }

        final IServiceProviderPlace place = (IServiceProviderPlace) Namespace.lookup(placeKey);
        if (place == null) {
            throw new NamespaceException("Nothing found for " + bean.getPlaceName() + " using " + placeKey + " as the lookup key");
        }

        try {
            emissary.pool.PayloadLauncher.launch(bean.getPayload(), place, bean.getErrorCount(), bean.getItineraryItems());
        } catch (EmissaryException ex) {
            logger.debug("Cannot launch incoming payload", ex);
            throw new RemoteException("Cannot launch payload", ex);
        } finally {
            MDC.remove(MDCConstants.SERVICE_LOCATION);
        }

        return true;
    }

    /**
     * Send a moveTo call to a remote machine
     * 
     * @param place the four-tuple of the place we are heading to
     * @param agent the MobileAgent that is moving
     * @return status of operation including body if successful
     */
    public EmissaryResponse outboundMoveTo(final String place, final IMobileAgent agent) {

        String url = null;

        // Move to actions can be load-balanced out to
        // a virtual IP address:port if so configured
        if (VIRTUAL_MOVETO_ADDR != null) {
            url = VIRTUAL_MOVETO_PROTOCOL + "://" + VIRTUAL_MOVETO_ADDR + "/";
        } else {
            url = KeyManipulator.getServiceHostURL(place);
        }
        url += CONTEXT + "/MoveTo.action";

        final HttpPost method = new HttpPost(url);
        method.setHeader("Content-type", "application/x-www-form-urlencoded; charset=ISO-8859-1");
        final List<NameValuePair> nvps = new ArrayList<NameValuePair>();

        nvps.add(new BasicNameValuePair(PLACE_NAME, place));
        nvps.add(new BasicNameValuePair(MOVE_ERROR_COUNT, Integer.toString(agent.getMoveErrorCount())));

        final DirectoryEntry[] iq = agent.getItineraryQueueItems();
        for (int j = 0; j < iq.length; j++) {
            nvps.add(new BasicNameValuePair(ITINERARY_ITEM, iq[j].getKey()));
        }

        try {
            // This is an 8859_1 String
            final String agentData = PayloadUtil.serializeToString(agent.getPayloadForTransport());
            nvps.add(new BasicNameValuePair(AGENT_SERIAL, agentData));
        } catch (IOException iox) {
            // TODO This will probably need looked at when redoing the moveTo
            logger.error("Cannot serialize agent data", iox);
            BasicHttpResponse response =
                    new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR, "Cannot serialize agent data");
            response.setEntity(EntityBuilder.create().setText("").setContentEncoding(MediaType.TEXT_PLAIN).build());
            return new EmissaryResponse(response);
        }

        method.setEntity(new UrlEncodedFormEntity(nvps, Charset.forName("8859_1")));

        // Add a cookie to the outbound header if we are posting
        // to the virtual IP for load balancing
        if (VIRTUAL_MOVETO_ADDR != null) {
            final BasicClientCookie cookie = new BasicClientCookie(COOKIE_NAME, KeyManipulator.getServiceClassname(place));
            cookie.setDomain(VIRTUAL_MOVETO_ADDR.substring(0, VIRTUAL_MOVETO_ADDR.indexOf(":")));
            cookie.setPath(COOKIE_PATH);
            return send(method, cookie);
        }
        return send(method);
    }


    static class MoveToRequestBean {
        String placeName;
        Object payload;
        int errorCount;
        List<DirectoryEntry> itineraryItems = null;

        MoveToRequestBean() {}

        MoveToRequestBean(final HttpServletRequest req) {
            setPlaceName(RequestUtil.getParameter(req, PLACE_NAME));
            if (getPlaceName() == null) {
                throw new IllegalArgumentException("Missing place name");
            }

            final String agentData = RequestUtil.getParameter(req, AGENT_SERIAL);
            if (agentData == null) {
                throw new IllegalArgumentException("Missing serialized agent data");
            }
            setPayload(agentData);

            setErrorCount(RequestUtil.getIntParam(req, MOVE_ERROR_COUNT, 0));

            final String[] p = req.getParameterValues(ITINERARY_ITEM);
            if (p != null && p.length > 0) {
                this.itineraryItems = new ArrayList<DirectoryEntry>();
                for (int i = 0; i < p.length; i++) {
                    this.itineraryItems.add(new DirectoryEntry(p[i]));
                }
            }
        }

        void setPayload(final String s) {
            this.payload = PayloadUtil.deserialize(s);
        }

        /**
         * Get the place name for arrival
         */
        String getPlaceName() {
            return this.placeName;
        }

        /**
         * Set the place name
         * 
         * @param argPlaceName value of placeName
         */
        void setPlaceName(final String argPlaceName) {
            this.placeName = argPlaceName;
        }

        /**
         * Get the agent's payload data. The actual type will depend on the particular implementation of IMobileAgent that
         * serialized the data to us.
         */
        Object getPayload() {
            return this.payload;
        }

        void setErrorCount(final int c) {
            this.errorCount = c;
        }

        int getErrorCount() {
            return this.errorCount;
        }

        int getItineraryItemCount() {
            return this.itineraryItems == null ? 0 : this.itineraryItems.size();
        }

        /**
         * Return the transferred itinerary items as a list
         * 
         * @return list of DirectoryEntry
         */
        @SuppressWarnings("unchecked")
        List<DirectoryEntry> getItineraryItems() {
            return this.itineraryItems == null ? Collections.EMPTY_LIST : this.itineraryItems;
        }
    }
}
