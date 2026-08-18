package emissary.output.formatter.filter.metadata.sample;

import emissary.config.Configurator;
import emissary.output.formatter.filter.metadata.MetadataFilter;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

/**
 * Metadata (parameter) filter that keeps only keys matching the configured {@code METADATA_FIELD_PATTERN}.
 */
public class MetadataFieldPatternFilter extends MetadataFilter {

    public static final String FIELD_PATTERN = "METADATA_FIELD_PATTERN";

    private Pattern fieldPattern;

    @Override
    public void configure(final Configurator config) {
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
    public boolean allows(final String key) {
        return this.fieldPattern == null || this.fieldPattern.matcher(key).matches();
    }

    @Override
    public boolean allows(final String key, final Object value) {
        return true;
    }
}
