package emissary.output.formatter.filter.content;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;
import emissary.output.formatter.filter.AndFilter;

import java.util.List;

/**
 * Content (view) filter that ANDs an ordered list of content filters together.
 */
public class ContentFilterChain extends ContentFilter {

    private final AndFilter<ContentFilter> composite;

    /**
     * Build a chain from the supplied filters, evaluated in order.
     *
     * @param filters the filters to AND together
     */
    public ContentFilterChain(final List<ContentFilter> filters) {
        this.composite = new AndFilter<>(filters);
    }

    @Override
    public boolean allows(final IBaseDataObject d, final String viewName) {
        return this.composite.allMatches(filter -> filter.allows(d, viewName));
    }

    @Override
    public void configure(final Configurator config) {
        this.composite.configure(config);
    }

    /**
     * The filters ANDed by this chain, in evaluation order.
     *
     * @return the member filters
     */
    public List<ContentFilter> getFilters() {
        return this.composite.getFilters();
    }
}
