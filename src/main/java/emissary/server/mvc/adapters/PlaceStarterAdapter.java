package emissary.server.mvc.adapters;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import emissary.admin.PlaceStarter;
import emissary.client.EmissaryClient;
import emissary.client.EmissaryResponse;
import emissary.place.IServiceProviderPlace;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapt createPlace calls (remote construction) to HTTP
 */
public class PlaceStarterAdapter extends EmissaryClient {

    private static final Logger logger = LoggerFactory.getLogger(PlaceStarterAdapter.class);

    // Some parameter names for dealing with HTTP messages
    public static final String CP_CLASS_NAME = "sppClassName";
    public static final String CP_LOCATION = "sppLocation";
    public static final String CP_DIRECTORY = "sppDirectory";
    public static final String CP_PLACE_NAME = "sppPlaceName";
    public static final String PLACE_OBJECT = "sppPlaceObject";

    private static final String CREATE_PLACE_ENDPOINT = "/CreatePlace.action";

    String className;
    String location;
    String directory;
    String placeName;
    IServiceProviderPlace thePlace;

    public PlaceStarterAdapter(final String classNameArg, final String locationArg, final String directoryArg) {
        setClassName(classNameArg);
        setLocation(locationArg);
        setDirectory(directoryArg);
    }

    public PlaceStarterAdapter(final HttpServletRequest req) {

        setClassName(RequestUtil.getParameter(req, CP_CLASS_NAME));
        setLocation(RequestUtil.getParameter(req, CP_LOCATION));
        setDirectory(RequestUtil.getParameter(req, CP_DIRECTORY));

        if (getClassName() == null || getLocation() == null) {
            throw new IllegalArgumentException("Missing required parameter: " + "(c,l) = (" + getClassName() + "," + getLocation() + ")");
        }
    }

    /**
     * Process the createPlace call coming remotely over HTTP
     */
    public IServiceProviderPlace inboundCreatePlace() {

        // Instantiate the place
        final IServiceProviderPlace spp = PlaceStarter.createPlace(getLocation(), getClassName(), getDirectory());

        logger.info("Created a new place of class " + getClassName() + " at " + getLocation());

        return spp;
    }

    /**
     * Send place creation message remotely
     */
    public EmissaryResponse outboundCreatePlace() {

        final String placeUrl = getLocation().substring(0, getLocation().lastIndexOf("/") + 1);
        // Derive url from place location
        logger.debug("Creating post method for createPlace using URL {}{}{}", placeUrl, CONTEXT, CREATE_PLACE_ENDPOINT);

        final HttpPost method = createHttpPost(placeUrl, CONTEXT, CREATE_PLACE_ENDPOINT);
        final List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair(CP_CLASS_NAME, getClassName()));
        nvps.add(new BasicNameValuePair(CP_LOCATION, getLocation()));
        if (getDirectory() != null) {
            nvps.add(new BasicNameValuePair(CP_DIRECTORY, getDirectory()));
        }
        method.setEntity(new UrlEncodedFormEntity(nvps, java.nio.charset.Charset.defaultCharset()));
        return send(method);
    }


    /**
     * Gets the value of className
     *
     * @return the value of className
     */
    public String getClassName() {
        return this.className;
    }

    /**
     * Sets the value of className
     *
     * @param argClassName Value to assign to this.className
     */
    public void setClassName(final String argClassName) {
        this.className = argClassName;
    }

    /**
     * Gets the value of location
     *
     * @return the value of location
     */
    public String getLocation() {
        return this.location;
    }

    /**
     * Sets the value of location
     *
     * @param argLocation Value to assign to this.location
     */
    public void setLocation(final String argLocation) {
        this.location = argLocation;
    }

    /**
     * Gets the value of directory
     *
     * @return the value of directory
     */
    public String getDirectory() {
        return this.directory;
    }

    /**
     * Sets the value of directory
     *
     * @param argDirectory Value to assign to this.directory
     */
    public void setDirectory(final String argDirectory) {
        this.directory = argDirectory;
    }

    /**
     * Gets the value of placeName
     *
     * @return the value of placeName
     */
    public String getPlaceName() {
        return this.placeName;
    }

    /**
     * Sets the value of placeName
     *
     * @param argPlaceName Value to assign to this.placeName
     */
    public void setPlaceName(final String argPlaceName) {
        this.placeName = argPlaceName;
    }

    /**
     * Gets the value of thePlace
     *
     * @return the value of thePlace
     */
    public IServiceProviderPlace getThePlace() {
        return this.thePlace;
    }

    /**
     * Sets the value of thePlace
     *
     * @param argThePlace Value to assign to this.thePlace
     */
    public void setThePlace(final IServiceProviderPlace argThePlace) {
        this.thePlace = argThePlace;
    }

}
