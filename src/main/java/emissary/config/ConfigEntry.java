/*
  $Id$
 */

package emissary.config;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.Serializable;

/**
 * ConfigEntry class stores key-value pairs of configuration info.
 */
public class ConfigEntry implements Serializable {

    // Serializable
    static final long serialVersionUID = 876727639402334458L;

    private String key;
    private String value;

    public ConfigEntry() {}

    /**
     * Create a new ConfigEntry object setting the key and associated value.
     */
    public ConfigEntry(final String theKey, final String theValue) {
        this.key = theKey;
        this.value = theValue;
    }

    /**
     * Set the key of the ConfigEntry object.
     */
    public void setKey(final String theKey) {
        this.key = theKey;
    }

    /**
     * Set the value of the ConfigEntry object.
     */
    public void setValue(final String theValue) {
        this.value = theValue;
    }

    /**
     * Get the key
     */
    public String getKey() {
        return this.key;
    }

    /**
     * Get the value
     */
    public String getValue() {
        return this.value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ConfigEntry entry = (ConfigEntry) o;

        return new EqualsBuilder().append(key, entry.key).append(value, entry.value).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(key).append(value).toHashCode();
    }
}
