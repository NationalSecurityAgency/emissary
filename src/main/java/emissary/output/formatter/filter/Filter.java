package emissary.output.formatter.filter;

import emissary.config.Configurator;

/**
 * Common contract for filters that can be configured from a {@link Configurator}.
 */
public interface Filter {

    /**
     * Configure this filter (and any nested children) from the supplied configuration.
     *
     * @param config the formatter configuration
     */
    void configure(Configurator config);
}
