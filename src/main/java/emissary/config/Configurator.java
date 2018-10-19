package emissary.config;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface for dealing with configured properties
 */
public interface Configurator {

    // Values for findStringMatchMap(String,boolean)
    boolean PRESERVE_CASE = true;
    boolean IGNORE_CASE = false;

    /**
     * Find all entries matching name
     *
     * @param param the name of the config entry
     * @param dflt default value when none found
     * @return list of string values of matching entries
     */
    List<String> findEntries(String param, String dflt);

    /**
     * Find all entries matching name
     *
     * @param param the name of the config entry
     * @return list of string values of matching entries
     */
    List<String> findEntries(String param);

    /**
     * Return a set with all parameter values as members
     */
    Set<String> findEntriesAsSet(String param);

    /**
     * Find all entry keys that begin with specified string
     *
     * @param param the leading key startsWith
     * @return the values of matching items
     */
    List<ConfigEntry> findStringMatchEntries(String param);

    /**
     * Find entries beginning with the specified string and put them into a list with the specified part of the name
     * stripped off like #findStringMatchMap
     *
     * @param theParameter key to match with a startsWith
     * @return list of ConfigEntry
     */
    List<ConfigEntry> findStringMatchList(final String theParameter);

    /**
     * Find all entry keys that begin with the specified string Remaining key portion is upper cased
     *
     * @param param the leading key startsWith
     * @return map with remaining key portion as key, value as value
     */
    Map<String, String> findStringMatchMap(String param);

    /**
     * Find all entry keys that begin with the specified string
     *
     * @param param the leading key startsWith
     * @param preserveCase Case of key is preserved when true
     * @return map with remaining key portion as key, value as value
     */
    Map<String, String> findStringMatchMap(String param, boolean preserveCase);

    /**
     * Find all entry keys that begin with the specified string Remaining key portion is upper cased
     *
     * @param theParameter the parameter the leading key startsWith
     * @return map with remaining key portion as key, value as a set of multiple values
     */
    Map<String, Set<String>> findStringMatchMultiMap(final String theParameter);


    /**
     * Find the first config entry matching with default value when none
     *
     * @param param the config entry name
     * @param dflt the default value when no entry found
     * @return string value of entry or default when no entry
     */
    String findStringEntry(String param, String dflt);

    /**
     * Find the first config entry matching the name
     *
     * @param param the config entry name
     * @return string value of entry or null when no entry
     */
    String findStringEntry(String param);

    /**
     * Find the last config entry (newest) matching the name
     *
     * @param param the config entry name
     * @return string value of entry or empty string when none
     */
    String findLastStringEntry(String param);

    /**
     * Return a long from a string entry representing either an int, a long, a double, with or without a final letter
     * designation such as "m" or "M" for megabytes, "g" or "G" for gigabytes, etc. Legal designations are bBkKmMgGTt or
     * just a number.
     *
     * @param theParameter the config entry name
     * @param dflt the default value when nothing found in config
     * @return the long value of the size parameter
     */
    long findSizeEntry(String theParameter, long dflt);

    /**
     * Return the string canonical file name of a matching entry
     *
     * @param theParameter the key to match
     * @param dflt the string to use as a default when no matches are found
     * @return the first matching value run through File.getCanonicalPath
     */
    String findCanonicalFileNameEntry(String theParameter, String dflt);

    /**
     * Find config entry as an int
     *
     * @param param name of config entry
     * @param dflt default value when none found
     * @return int value or dflt when none found or not an integer
     */
    int findIntEntry(String param, int dflt);

    /**
     * Find config entry as a long
     *
     * @param param name of config entry
     * @param dflt default value when none found
     * @return long value or dflt when none found or not a long
     */
    long findLongEntry(String param, long dflt);

    /**
     * Find config entry as an double
     *
     * @param param name of config entry
     * @param dflt default value when none found
     * @return double value or dflt when none found or not a double
     */
    double findDoubleEntry(String param, double dflt);

    /**
     * Find config entry as an boolean
     *
     * @param param name of config entry
     * @param dflt default value when none found
     * @return boolean value or dflt when none found or not a boolean
     */
    boolean findBooleanEntry(String param, boolean dflt);


    /**
     * Find config entry as an boolean
     *
     * @param param name of config entry
     * @param dflt default value when none found
     * @return boolean value or dflt when none found or not a boolean
     */
    boolean findBooleanEntry(String param, String dflt);

    /**
     * Get the names of all entries for this config. This set is not backed by the configuration and any changes to it are
     * not relflected in the configuration.
     */
    Set<String> entryKeys();

    /**
     * Get all of the entries for this config This is a copy and changes to it are not reflected in the configuration
     */
    List<ConfigEntry> getEntries();

    /**
     * Add an entry to this config
     *
     * @param key the name of the entry
     * @param value the value
     * @return the new entry or null if it fails
     */
    ConfigEntry addEntry(String key, String value);

    /**
     * Add a list of entries for the same key
     *
     * @param key the name of the entry
     * @param values the values
     * @return the new entries or null if it fails
     */
    List<ConfigEntry> addEntries(String key, List<String> values);

    /**
     * Remove all entries by the given name
     *
     * @param key the name of the entry or entries
     * @param value the value
     */
    void removeEntry(String key, String value);

    /**
     * Merge in a new configuration set with this one. New things are supposed to override older things in the sense of
     * findStringEntry which only picks the top of the list, the new things should get added to the top.
     *
     * @param other the new entries to merge in
     */
    void merge(Configurator other) throws IOException;
}
