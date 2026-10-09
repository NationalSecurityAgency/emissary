package emissary.output.formatter.filter.metadata;

import emissary.config.Configurator;
import emissary.output.formatter.filter.AndFilter;

import java.util.List;

/**
 * Metadata (parameter) filter that ANDs an ordered list of metadata filters together.
 */
public class MetadataFilterChain extends MetadataFilter {

    private final AndFilter<MetadataFilter> composite;

    /**
     * Build a chain from the supplied filters, evaluated in order.
     *
     * @param filters the filters to AND together
     */
    public MetadataFilterChain(final List<MetadataFilter> filters) {
        this.composite = new AndFilter<>(filters);
    }

    @Override
    public boolean allows(final String key) {
        return this.composite.allMatches(filter -> filter.allows(key));
    }

    @Override
    public boolean allows(final String key, final Object value) {
        return this.composite.allMatches(filter -> filter.allows(key, value));
    }

    @Override
    public void configure(final Configurator config) {
        super.configure(config);
        this.composite.configure(config);
    }

    /**
     * The filters ANDed by this chain, in evaluation order.
     *
     * @return the member filters
     */
    public List<MetadataFilter> getFilters() {
        return this.composite.getFilters();
    }
}
