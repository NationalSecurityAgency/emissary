package emissary.output.formatter.filter.metadata;

import emissary.config.Configurator;
import emissary.output.formatter.filter.FilterConfigKeys;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Allows only metadata keys and values matching the configured {@code ALLOWLIST_FIELD}, {@code ALLOWLIST_PREFIX} and
 * {@code ALLOWLIST_VALUE_} parameters, rejecting everything else.
 */
public class MetadataAllowFilter extends MetadataFilter {

    private final Set<String> fields = new TreeSet<>();
    private final Set<String> prefixes = new TreeSet<>();
    private final Map<String, Set<String>> values = new HashMap<>();

    @Override
    public void configure(final Configurator config) {
        super.configure(config);
        if (config == null) {
            return;
        }
        this.fields.addAll(config.findEntries(FilterConfigKeys.ALLOWLIST_FIELD));
        this.prefixes.addAll(config.findEntries(FilterConfigKeys.ALLOWLIST_PREFIX));
        this.values.putAll(config.findStringMatchMultiMap(FilterConfigKeys.ALLOWLIST_VALUE));
    }

    @Override
    public boolean allows(final String key) {
        boolean star = fields.contains("*") || fields.contains("ALL");

        if (fields.contains(key)) {
            return true;
        }

        for (final String prefix : prefixes) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }

        return star;
    }

    @Override
    public boolean allows(final String key, final Object value) {
        Set<String> allowed = this.values.get(key);
        return allowed == null || allowed.contains(String.valueOf(value));
    }
}
