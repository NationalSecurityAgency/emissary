package emissary.util.web;

/*
 $Id$
 */
/**
 * Hold onto fielded data that should be posted to a URL
 * in x-www-urlencoded format.
 *
 * For convenience also keep it around in unencoded format
 *
 * @author MJF, 2000-08-01
 */
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpPostParameters {

    private static final Logger logger = LoggerFactory.getLogger(HttpPostParameters.class);

    private final StringBuilder thePostData = new StringBuilder();
    private final StringBuilder thePostDataEncoded = new StringBuilder();

    /**
     * Create an empty parameter list
     */
    public HttpPostParameters() {}

    /**
     * Create and add one parameter
     * 
     * @param s the field name
     * @param v the value
     */
    public HttpPostParameters(final String s, final String v) {
        this.add(s, v);
    }

    /**
     * Add a field and value
     * 
     * @param s the field name
     * @param v the value (can be null)
     */
    public void add(final String s, final String v) {
        if (this.thePostDataEncoded.length() != 0) {
            this.thePostData.append("&");
            this.thePostDataEncoded.append("&");
        }

        this.thePostData.append(s).append("=");
        try {
            this.thePostDataEncoded.append(URLEncoder.encode(s, "UTF-8")).append("=");
        } catch (NullPointerException e) {
            logger.error("Null first parameter to add method.", e);
        } catch (UnsupportedEncodingException e) {
            logger.error("Bad encoding UTF-8", e);
        }
        if (v != null) {
            this.thePostData.append(v);
            try {
                this.thePostDataEncoded.append(URLEncoder.encode(v, "UTF-8"));
            } catch (UnsupportedEncodingException e1) {
                logger.error("Bad encoding UTF-8", e1);
            }
        }
    }

    /**
     * Get the length of the encoded stuff for the Content-length header
     */
    public int length() {
        return this.thePostDataEncoded.length();
    }

    /**
     * Get the x-www-urlencoded string
     */
    public String toPostString() {
        return this.thePostDataEncoded.toString();
    }

    /**
     * Get the x-www-urlencoded string with '?' preceeding
     */
    public String toGetString() {
        return "?" + this.thePostDataEncoded.toString();
    }

    /**
     * Get the plain old data
     */
    @Override
    public String toString() {
        return this.thePostData.toString();
    }
}
