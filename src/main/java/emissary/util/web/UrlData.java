package emissary.util.web;

import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The consolidated output from a web page retrieval
 *
 * @author mjf, 2000-08-01
 */
public class UrlData {

    String password;
    List<UrlRequestProperty> props;
    String referer;
    int responseCode;
    byte[] theContent;
    String theUrl;
    int theMethod;
    String userName;
    String userAgent;

    /*
     * The request properties array sent into this constructor is copied. Note that a deep copy is not performed. If the
     * properties inside the list are changed, this class will see the changes.
     */
    public UrlData(final String theUrl, final byte[] theContent, final int responseCode, final List<UrlRequestProperty> props) {
        this.theUrl = theUrl;
        this.theContent = theContent;
        this.responseCode = responseCode;
        this.theMethod = Url.GET;
        this.props = new ArrayList<>(props);
    }

    public UrlData() {}

    public UrlData(final String theUrl) {
        this.theUrl = theUrl;
    }

    /**
     * Get the value of responseCode.
     * 
     * @return Value of responseCode.
     */
    public int getResponseCode() {
        return this.responseCode;
    }

    /**
     * Set the value of responseCode.
     * 
     * @param v Value to assign to responseCode.
     */
    public void setResponseCode(final int v) {
        this.responseCode = v;
    }

    /**
     * Get the value of theUrl.
     * 
     * @return Value of theUrl.
     */
    public String getTheUrl() {
        return this.theUrl;
    }

    /**
     * Set the value of theUrl.
     * 
     * @param v Value to assign to theUrl.
     */
    public void setTheUrl(final String v) {
        this.theUrl = v;
    }

    /**
     * Get the value of theContent.
     * 
     * @return Value of theContent.
     */
    public byte[] getTheContent() {
        return this.theContent;
    }

    public int getContentLength() {
        return (this.theContent == null) ? 0 : this.theContent.length;
    }

    /**
     * Set the value of theContent.
     * 
     * @param v Value to assign to theContent.
     */
    public void setTheContent(final byte[] v) {
        this.theContent = v;
    }

    /**
     * Get the value of referer.
     * 
     * @return Value of referer.
     */
    public String getReferer() {
        return this.referer;
    }

    /**
     * Set the value of referer.
     * 
     * @param v Value to assign to referer.
     */
    public void setReferer(final String v) {
        this.referer = v;
    }

    /**
     * Get the value of userAgent.
     * 
     * @return Value of userAgent.
     */
    public String getUserAgent() {
        return this.userAgent;
    }

    /**
     * Set the value of userAgent.
     * 
     * @param v Value to assign to userAgent.
     */
    public void setUserAgent(final String v) {
        this.userAgent = v;
    }

    /**
     * Get the value of userName.
     * 
     * @return Value of userName.
     */
    public String getUserName() {
        return this.userName;
    }

    /**
     * Set the value of userName.
     * 
     * @param v Value to assign to userName.
     */
    public void setUserName(final String v) {
        this.userName = v;
    }

    /**
     * Get the value of password.
     * 
     * @return Value of password.
     */
    public String getPassword() {
        return this.password;
    }

    /**
     * Set the value of password.
     * 
     * @param v Value to assign to password.
     */
    public void setPassword(final String v) {
        this.password = v;
    }

    /**
     * Get the value of theMethod.
     * 
     * @return Value of theMethod.
     */
    public int getTheMethod() {
        return this.theMethod;
    }

    /**
     * Get the value of props.
     * 
     * @return Value of props.
     */
    @Nullable
    public List<UrlRequestProperty> getProps() {
        return this.props == null ? null : new ArrayList<>(this.props);
    }

    public int getNumberOfProperties() {
        return (this.props == null) ? 0 : this.props.size();
    }

    /**
     * Set the value of props.
     * 
     * @param v Value to assign to props.
     */
    public void setProps(final List<UrlRequestProperty> v) {
        this.props = new ArrayList<>(v);
    }

    /**
     * Add one more property to this request
     * 
     * @param v the new UrlRequestProperty to add
     */
    public void addProp(@Nullable final UrlRequestProperty v) {

        if (v == null) {
            return;
        }

        if (this.props == null) {
            this.props = new ArrayList<>();
        }

        this.props.add(v);
    }

    /**
     * Add a bunch more properties to this request
     * 
     * @param v array of new UrlRequestProperty to add
     */
    public void addProps(@Nullable final List<UrlRequestProperty> v) {
        if (v == null) {
            return;
        }
        if (v.isEmpty()) {
            return;
        }

        if (this.props == null) {
            this.props = new ArrayList<>();
        }

        this.props.addAll(v);
    }

    /**
     * Set the value of theMethod.
     * 
     * @param v Value to assign to theMethod.
     */
    public void setTheMethod(final int v) {
        this.theMethod = v;
    }

    /**
     * As a helper to the toString methods
     */
    private StringBuilder toStringBuilder(final boolean headers) {
        final StringBuilder sb = new StringBuilder();
        sb.append(Url.theMethodString[this.theMethod]).append(": ").append(this.theUrl).append(" ").append(this.responseCode).append("/")
                .append(this.theContent.length);
        if (headers && (this.props != null)) {
            for (UrlRequestProperty prop : this.props) {
                sb.append(prop.toString());
            }
        }
        sb.append("\n\n").append(new String(this.theContent)).append("\n");
        return sb;
    }

    @Override
    public String toString() {
        return this.toStringBuilder(false).toString();
    }

    public String toString(final boolean headers) {
        return this.toStringBuilder(headers).toString();
    }
}
