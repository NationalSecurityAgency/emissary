package emissary.id;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Response object from an identification engine. Contains a list of types for the currentForm and a list of properties
 * for the parameters
 */
public class Identification implements Serializable {
    // Serializable
    static final long serialVersionUID = 9212068103720124108L;

    protected List<String> types = new ArrayList<String>();
    protected Map<String, String> props = new TreeMap<String, String>();

    /**
     * Create a new Identification object
     */
    public Identification() {}

    /**
     * Create a new identification for the specified type string
     * 
     * @param type the type string
     */
    public Identification(final String type) {
        this.types.add(type);
    }

    /**
     * Add a type to the list
     * 
     * @param type the new type
     */
    public void addType(final String type) {
        this.types.add(type);
    }

    /**
     * Add new types to the list
     * 
     * @param additionalTypes the new types
     */
    public void addTypes(final Collection<String> additionalTypes) {
        this.types.addAll(additionalTypes);
    }

    /**
     * Remove any types and set the type to the value specified
     * 
     * @param type the new type value
     */
    public void setType(final String type) {
        this.types.clear();
        addType(type);
    }

    /**
     * Remove any types and set theh values specified
     * 
     * @param theTypes the new type values
     */
    public void setTypes(final Collection<String> theTypes) {
        this.types.clear();
        addTypes(theTypes);
    }

    /**
     * Get a count of types
     */
    public int getTypeCount() {
        return this.types.size();
    }

    /**
     * Get a list of the types present
     */
    public List<String> getTypes() {
        return new ArrayList<String>(this.types);
    }

    /**
     * Get the first type or null if none
     */
    public String getFirstType() {
        if (this.types.isEmpty()) {
            return null;
        } else {
            return this.types.get(0);
        }
    }

    /**
     * Get a comma separated string list of all the types
     */
    public String getTypeString() {
        final StringBuilder b = new StringBuilder();
        for (final String t : this.types) {
            if (b.length() > 0) {
                b.append(',');
            }
            b.append(t);
        }
        return b.toString();
    }

    /**
     * Add the key value pair to the properties list
     */
    public void addProperty(final String key, final String value) {
        this.props.put(key, value);
    }

    /**
     * Return a map of all the properties
     */
    public Map<String, String> getProperties() {
        return new HashMap<String, String>(this.props);
    }

    /**
     * Get the value for the specified property
     * 
     * @param key the property name
     */
    public String getProperty(final String key) {
        return this.props.get(key);
    }

    /**
     * Get the value of the specified property and remove it from this identification
     * 
     * @param key the property name
     */
    public String popProperty(final String key) {
        final String val = this.props.get(key);
        if (val != null) {
            this.props.remove(key);
        }
        return val;
    }

    /**
     * Remove the first type from the list and return it
     * 
     * @return the top top or null if none
     */
    public String popType() {
        if (this.types.isEmpty()) {
            return null;
        } else {
            return this.types.remove(0);
        }
    }

    /**
     * Add the type to the front of the list and return it
     */
    public void pushType(String s) {
        this.types.add(0, s);
    }

    /**
     * String representation of this object
     */
    @Override
    public String toString() {
        return this.types.toString();
    }
}
