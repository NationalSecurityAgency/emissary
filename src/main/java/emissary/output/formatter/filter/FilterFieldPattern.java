package emissary.output.formatter.filter;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;

import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

/**
 * Denies parameters whose key does not match the configured {@code METADATA_FIELD_PATTERN}. Views always pass.
 */
public class FilterFieldPattern extends AbstractItemFilter {

    public static final String FIELD_PATTERN = "METADATA_FIELD_PATTERN";

    @Nullable
    private Pattern fieldPattern;

    @Override
    public void configure(@Nullable final Configurator config) {
        super.configure(config);
        if (config == null) {
            return;
        }
        String pattern = config.findStringEntry(FIELD_PATTERN, null);
        if (StringUtils.isNotBlank(pattern)) {
            this.fieldPattern = Pattern.compile(pattern);
        }
    }

    @Override
    public boolean test(@Nullable final IBaseDataObject d, final OutputItem item) {
        if (!item.isParameter()) {
            return true;
        }
        return this.fieldPattern == null || this.fieldPattern.matcher(item.name()).matches();
    }

    @Override
    public String describe(final boolean verbose) {
        StringBuilder sb = new StringBuilder(super.describe(verbose));
        if (verbose && this.fieldPattern != null) {
            sb.append(' ').append(FIELD_PATTERN).append(" = \"").append(this.fieldPattern.pattern()).append('"');
        }
        return sb.toString();
    }
}
