package emissary.output.formatter;

import emissary.config.Configurator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Accepts everything by default; parses the configured parameter deny lists from the configuration.
 */
public class MetadataDenyList {

    private final Set<String> denylistFields = new TreeSet<>();
    private final Set<String> denylistPrefixes = new TreeSet<>();
    private final Map<String, Set<String>> denylistValues = new HashMap<>();
    private final Set<String> stripPrefixes = new HashSet<>();

    /**
     * Parse the parameter deny-list configuration.
     *
     * @param config the formatter configuration
     */
    public void configure(final Configurator config) {
        if (config == null) {
            return;
        }
        this.denylistFields.addAll(config.findEntries("DENYLIST_FIELD"));
        this.denylistPrefixes.addAll(config.findEntries("DENYLIST_PREFIX"));
        this.denylistValues.putAll(config.findStringMatchMultiMap("DENYLIST_VALUE_"));
        this.stripPrefixes.addAll(config.findEntriesAsSet("STRIP_PARAM_PREFIX"));
    }

    /**
     * Determine if a parameter key should be included. Accepts the key unless it is denied.
     *
     * @param key the parameter key
     * @return true to include
     */
    public boolean isAllowed(final String key) {
        boolean denylistStar = denylistFields.contains("*") || denylistFields.contains("ALL");

        if (denylistFields.contains(key)) {
            return false;
        }

        for (final String prefix : denylistPrefixes) {
            if (key.startsWith(prefix)) {
                return false;
            }
        }

        return !denylistStar;
    }

    /**
     * Determine if a specific value for a parameter key should be included.
     *
     * @param key the parameter key
     * @param value the parameter value
     * @return true to include
     */
    public boolean isAllowed(final String key, final Object value) {
        Set<String> denied = this.denylistValues.get(key);
        return denied == null || !denied.contains(String.valueOf(value));
    }

    /**
     * Strip any configured prefix from a parameter name.
     *
     * @param name the parameter name
     * @return the name with the first matching configured prefix removed
     */
    public String stripPrefix(final String name) {
        for (final String prefix : this.stripPrefixes) {
            if (name.startsWith(prefix)) {
                return name.substring(prefix.length());
            }
        }
        return name;
    }
}
