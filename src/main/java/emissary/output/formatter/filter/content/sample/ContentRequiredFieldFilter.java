package emissary.output.formatter.filter.content.sample;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;
import emissary.output.formatter.filter.content.ContentFilter;

import java.util.Set;
import java.util.TreeSet;

/**
 * Content (view) filter that only emits views when at least one of the configured {@code CONTENT_REQUIRED_FIELDS}
 * exists as a parameter on the payload.
 */
public class ContentRequiredFieldFilter extends ContentFilter {

    public static final String REQUIRED_FIELDS = "CONTENT_REQUIRED_FIELDS";

    private Set<String> requiredFields = Set.of();

    @Override
    public void configure(final Configurator config) {
        if (config == null) {
            return;
        }
        this.requiredFields = new TreeSet<>(config.findEntries(REQUIRED_FIELDS));
    }

    @Override
    public boolean allows(final IBaseDataObject d, final String viewName) {
        if (this.requiredFields.isEmpty()) {
            return true;
        }
        if (d.getAlternateView(viewName) == null) {
            return true;
        }
        return this.requiredFields.stream().anyMatch(d::hasParameter);
    }
}
