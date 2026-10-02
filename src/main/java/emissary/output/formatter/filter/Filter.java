package emissary.output.formatter.filter;

import emissary.config.Configurator;

/**
 * Common contract for filters that can be configured from a {@link Configurator}.
 */
public interface Filter {

    /** Configure this filter. */
    void configure(Configurator config);
}
