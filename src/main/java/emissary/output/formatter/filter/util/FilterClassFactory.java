package emissary.output.formatter.filter.util;

import emissary.config.Configurator;
import emissary.core.EmissaryRuntimeException;
import emissary.core.Factory;

import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Instantiates filter classes declared in the configuration.
 */
public final class FilterClassFactory {

    private FilterClassFactory() {}

    /**
     * Create a filter per declared class name under the given config key. Entries may carry a {@code NAME:} label prefix,
     * which is stripped before instantiation.
     *
     * @param <T> the filter base type
     * @return the created filters, not yet configured
     * @throws EmissaryRuntimeException when a declared class cannot be created or is not a {@code type}
     */
    public static <T> List<T> createFrom(@Nullable final Configurator configG, final String key, final Class<T> type,
            final String typeLabel) {
        final List<T> filters = new ArrayList<>();
        if (configG == null) {
            return filters;
        }
        for (final String entry : configG.findEntries(key)) {
            final String className = parseClassName(entry);
            final Object filter;
            try {
                filter = Factory.create(className);
            } catch (RuntimeException ex) {
                throw new EmissaryRuntimeException(String.format("Invalid filter configuration: `%s = \"%s\"` cannot be created",
                        key, className), ex);
            }
            if (filter != null && type.isInstance(filter)) {
                filters.add(type.cast(filter));
            } else {
                throw new EmissaryRuntimeException(String.format("Invalid filter configuration: `%s = \"%s\"` is not a %s",
                        key, className, typeLabel));
            }
        }
        return filters;
    }

    private static String parseClassName(final String entry) {
        final int colpos = entry.indexOf(':');
        return colpos > -1 ? entry.substring(colpos + 1) : entry;
    }
}
