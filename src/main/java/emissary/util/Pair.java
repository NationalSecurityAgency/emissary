package emissary.util;

import java.io.Serializable;

/**
 * Pair of string,object
 */
public class Pair implements Serializable {

    // Serializable
    static final long serialVersionUID = 6629601505549278155L;

    String key;
    Object value;

    public Pair(final String s1, final Object s2) {
        this.key = s1;
        this.value = s2;
    }

    /**
     * Gets the value of key
     *
     * @return the value of key
     */
    public String getKey() {
        return this.key;
    }

    /**
     * Sets the value of key
     *
     * @param argKey Value to assign to this.key
     */
    public void setKey(final String argKey) {
        this.key = argKey;
    }

    /**
     * Gets the value of value
     *
     * @return the value of value
     */
    public Object getValue() {
        return this.value;
    }

    /**
     * Sets the value of value
     *
     * @param argValue Value to assign to this.value
     */
    public void setValue(final Object argValue) {
        this.value = argValue;
    }

    @Override
    public String toString() {
        return this.key + ": " + this.value.toString();
    }
}
