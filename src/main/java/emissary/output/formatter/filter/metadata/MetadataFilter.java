package emissary.output.formatter.filter.metadata;

import emissary.config.Configurator;
import emissary.output.formatter.filter.Filter;
import emissary.output.formatter.filter.FilterConfigKeys;

import java.util.HashSet;
import java.util.Set;

/**
 * Base for metadata (parameter) filtering.
 */
public abstract class MetadataFilter implements Filter {

    private final Set<String> stripPrefixes = new HashSet<>();

    /**
     * Determine if a parameter key should be included.
     *
     * @param key the parameter key
     * @return true to include
     */
    public boolean allows(String key) {
        return true;
    }

    /**
     * Determine if a specific value for a parameter key should be included.
     *
     * @param key the parameter key
     * @param value the parameter value
     * @return true to include
     */
    public boolean allows(String key, Object value) {
        return true;
    }

    /**
     * Parse the shared {@code STRIP_PARAM_PREFIX} configuration. Subclasses overriding this should call
     * {@code super.configure(config)}.
     *
     * @param config the formatter configuration
     */
    @Override
    public void configure(final Configurator config) {
        if (config == null) {
            return;
        }
        this.stripPrefixes.addAll(config.findEntriesAsSet(FilterConfigKeys.STRIP_PARAM_PREFIX));
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
