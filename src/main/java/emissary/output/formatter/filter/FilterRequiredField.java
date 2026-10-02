package emissary.output.formatter.filter;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;

import jakarta.annotation.Nullable;

import java.util.Set;
import java.util.TreeSet;

/**
 * Denies views whose payload lacks any of the configured {@code CONTENT_REQUIRED_FIELDS}. Parameters always pass.
 */
public class FilterRequiredField extends AbstractItemFilter {

    public static final String REQUIRED_FIELDS = "CONTENT_REQUIRED_FIELDS";

    private Set<String> requiredFields = Set.of();

    @Override
    public void configure(@Nullable final Configurator config) {
        super.configure(config);
        if (config == null) {
            return;
        }
        this.requiredFields = new TreeSet<>(config.findEntries(REQUIRED_FIELDS));
    }

    @Override
    public boolean test(final IBaseDataObject d, final String viewName) {
        if (d == null) {
            return true;
        }
        if (this.requiredFields.isEmpty() || d.getAlternateView(viewName) == null) {
            return true;
        }
        return this.requiredFields.stream().anyMatch(d::hasParameter);
    }

    @Override
    public String describe(final boolean verbose) {
        StringBuilder sb = new StringBuilder(super.describe(verbose));
        if (verbose && !this.requiredFields.isEmpty()) {
            sb.append(' ').append(REQUIRED_FIELDS).append(" = ").append(String.join(",", this.requiredFields));
        }
        return sb.toString();
    }
}
