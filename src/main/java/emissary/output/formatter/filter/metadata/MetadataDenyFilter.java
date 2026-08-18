package emissary.output.formatter.filter.metadata;

import emissary.config.Configurator;
import emissary.output.formatter.filter.FilterConfigKeys;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Rejects metadata keys and values matching the configured {@code DENYLIST_FIELD}, {@code DENYLIST_PREFIX} and
 * {@code DENYLIST_VALUE_} parameters, accepting everything else.
 */
public class MetadataDenyFilter extends MetadataFilter {

    private final Set<String> fields = new TreeSet<>();
    private final Set<String> prefixes = new TreeSet<>();
    private final Map<String, Set<String>> values = new HashMap<>();

    @Override
    public void configure(final Configurator config) {
        super.configure(config);
        if (config == null) {
            return;
        }
        this.fields.addAll(config.findEntries(FilterConfigKeys.DENYLIST_FIELD));
        this.prefixes.addAll(config.findEntries(FilterConfigKeys.DENYLIST_PREFIX));
        this.values.putAll(config.findStringMatchMultiMap(FilterConfigKeys.DENYLIST_VALUE));
    }

    @Override
    public boolean allows(final String key) {
        boolean star = fields.contains("*") || fields.contains("ALL");

        if (fields.contains(key)) {
            return false;
        }

        for (final String prefix : prefixes) {
            if (key.startsWith(prefix)) {
                return false;
            }
        }

        return !star;
    }

    @Override
    public boolean allows(final String key, final Object value) {
        if (!allows(key)) {
            return false;
        }
        Set<String> denied = this.values.get(key);
        return denied == null || !denied.contains(String.valueOf(value));
    }
}
