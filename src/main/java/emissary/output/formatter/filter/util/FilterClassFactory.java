package emissary.output.formatter.filter.util;

import emissary.config.Configurator;
import emissary.core.EmissaryRuntimeException;
import emissary.core.Factory;
import emissary.output.formatter.filter.Filter;

import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Instantiates filter classes declared in the configuration.
 */
public final class FilterClassFactory {

    private FilterClassFactory() {}

    /**
     * Create one filter instance per class name declared under the supplied config key, in config order.
     *
     * @param <T> the filter base type
     * @param configG the configuration holding the declared class names
     * @param key the config key listing filter class names
     * @param type the filter base type every declared class must extend
     * @param typeLabel the base type label used in error messages
     * @return the created, not yet configured filters
     * @throws EmissaryRuntimeException when a declared class cannot be created or is not a subtype of {@code type}
     */
    public static <T> List<T> createFrom(@Nullable final Configurator configG, final String key, final Class<T> type,
            final String typeLabel) {
        final List<T> filters = new ArrayList<>();
        if (configG == null) {
            return filters;
        }
        for (final String className : configG.findEntries(key)) {
            final Object filter = Factory.create(className);
            if (filter != null && type.isInstance(filter)) {
                filters.add(type.cast(filter));
            } else {
                throw new EmissaryRuntimeException(String.format("Invalid filter configuration: `%s = \"%s\"` is not a %s",
                        key, className, typeLabel));
            }
        }
        return filters;
    }

    /**
     * Build a filter chain from the classes declared under the supplied config key and configure it. This handles both
     * content (view) and metadata (parameter) chains generically.
     *
     * @param <F> the filter base type and chain type
     * @param <C> the concrete chain type
     * @param config the configuration holding the declared class names
     * @param key the config key listing filter class names
     * @param filterType the filter base type every declared class must extend
     * @param typeLabel the base type label used in error messages
     * @param chainFactory constructs the chain from the created filters, in declared order
     * @return the configured chain, honoring the declared filters in order or accepting everything when none are declared
     * @throws EmissaryRuntimeException when a declared class cannot be created or is not a subtype of {@code filterType}
     */
    public static <F extends Filter, C extends F> C createFilterChain(@Nullable final Configurator config,
            final String key, final Class<F> filterType, final String typeLabel, final Function<List<F>, C> chainFactory) {
        final C chain = chainFactory.apply(createFrom(config, key, filterType, typeLabel));
        chain.configure(config);
        return chain;
    }
}
