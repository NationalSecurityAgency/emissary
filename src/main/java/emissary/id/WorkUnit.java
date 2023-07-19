package emissary.id;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Basic unit of communication between an IdPlace and an IdEngine
 */
public class WorkUnit implements Serializable {
    // Serializable
    static final long serialVersionUID = -990149336476881472L;

    protected String filename;
    protected byte[] data;
    protected byte[] header;
    protected byte[] footer;
    protected String currentForm;
    protected Map<String, String> params = new HashMap<>();


    public WorkUnit() {}

    public WorkUnit(String filename, byte[] data) {
        setFilename(filename);
        setData(data);
    }

    public WorkUnit(String filename, byte[] data, String currentForm) {
        this(filename, data);
        setCurrentForm(currentForm);
    }

    public WorkUnit(String f, byte[] hd, byte[] data, byte[] ft, String currentForm) {
        setFilename(f);
        setHeader(hd);
        setData(data);
        setFooter(ft);
        setCurrentForm(currentForm);
    }

    /**
     * Gets the value of filename
     *
     * @return the value of filename
     */
    public String getFilename() {
        return this.filename;
    }

    /**
     * Sets the value of filename
     *
     * @param argFilename Value to assign to this.filename
     */
    public void setFilename(String argFilename) {
        this.filename = argFilename;
    }

    /**
     * Gets the value of data
     *
     * @return the value of data
     */
    public byte[] getData() {
        return this.data;
    }

    /**
     * Sets the value of data
     *
     * @param argData Value to assign to this.data
     */
    public void setData(byte[] argData) {
        this.data = argData;
    }

    /**
     * Gets the value of currentForm
     *
     * @return the value of currentForm
     */
    public String getCurrentForm() {
        return this.currentForm;
    }

    /**
     * Sets the value of currentForm
     *
     * @param argCurrentForm Value to assign to this.currentForm
     */
    public void setCurrentForm(String argCurrentForm) {
        this.currentForm = argCurrentForm;
    }

    /**
     * Gets the value of header[]
     *
     * @return the value of header[]
     */
    public byte[] getHeader() {
        return this.header;
    }

    /**
     * Sets the value of header[]
     *
     * @param argHeader Value to assign to this.header[]
     */
    public void setHeader(byte[] argHeader) {
        this.header = argHeader;
    }

    /**
     * Gets the value of footer[]
     *
     * @return the value of footer[]
     */
    public byte[] getFooter() {
        return this.footer;
    }

    /**
     * Sets the value of footer[]
     *
     * @param argFooter Value to assign to this.footer[]
     */
    public void setFooter(byte[] argFooter) {
        this.footer = argFooter;
    }

    /**
     * Add a parameter to control the work
     */
    public void addParameter(String key, String value) {
        this.params.put(key, value);
    }

    /**
     * Add a bunch of parameters to control the work
     */
    public void addParameters(Map<String, String> m) {
        this.params.putAll(m);
    }

    /**
     * Retrieve a parameter
     */
    public String getParameter(String key) {
        return this.params.get(key);
    }

    /**
     * Get all parameters
     */
    public Map<String, String> getParameters() {
        return new HashMap<>(this.params);
    }
}
