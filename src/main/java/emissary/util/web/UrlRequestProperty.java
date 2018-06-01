package emissary.util.web;

/*
 $Id$
 */

/**
 * Provides a way to group Request properties either going into the emissary.util.Url class to override headers on the
 * connection (e.g. Referer, User-Agent) or to get the headers on a page we retrieved
 *
 * @author mjf, 2000-08-01
 */
public class UrlRequestProperty {

    protected String key;

    protected String value;

    public UrlRequestProperty() {}

    public UrlRequestProperty(final String key, final String value) {
        this.key = key;
        this.value = value;
    }

    public UrlRequestProperty(final String key, final int value) {
        this.key = key;
        this.value = "" + value;
    }

    /**
     * Get the value of key.
     * 
     * @return Value of key.
     */
    public String getKey() {
        return this.key;
    }

    /**
     * Set the value of key.
     * 
     * @param v Value to assign to key.
     */
    public void setKey(final String v) {
        this.key = v;
    }

    /**
     * Get the value of value.
     * 
     * @return Value of value.
     */
    public String getValue() {
        return this.value;
    }

    /**
     * Set the value of value.
     * 
     * @param v Value to assign to value.
     */
    public void setValue(final String v) {
        this.value = v;
    }

    /**
     * Set the value of value
     * 
     * @param v value to assign to value
     */
    public void setValue(final int v) {
        this.value = "" + v;
    }

    /**
     * Set up the base64 encoded string for the Auth header
     */
    public void setAuthHeader(final String user, final String password) {
        this.key = "Authorization";
        // String catvalue = user + ":" + password;
        // value = emissary.util.Base64.encode(catvalue.getBytes());
    }

    /**
     * get as a String
     */
    @Override
    public String toString() {
        return this.key + ": " + this.value;
    }
}
