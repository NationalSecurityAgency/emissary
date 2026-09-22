package emissary.output.formatter.filter;

import emissary.config.Configurator;

import java.util.List;
import java.util.function.Predicate;

/**
 * Group of filters supporting short-circuit evaluation
 */
public final class AndFilter<T extends Filter> implements Filter {

    private final List<T> filters;

    /**
     * Compose the supplied filters in order.
     *
     * @param filters the member filters
     */
    public AndFilter(final List<T> filters) {
        this.filters = List.copyOf(filters);
    }

    /**
     * True when every member passes, short-circuiting on the first failure.
     *
     * @param test the per-member evaluation adapted to the caller's signature
     * @return true when every member passes
     */
    public boolean allMatches(final Predicate<T> test) {
        for (final T filter : this.filters) {
            if (!test.test(filter)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void configure(final Configurator config) {
        for (final T filter : this.filters) {
            filter.configure(config);
        }
    }

    /**
     * The member filters, in order.
     *
     * @return the composed filters
     */
    public List<T> getFilters() {
        return this.filters;
    }
}
