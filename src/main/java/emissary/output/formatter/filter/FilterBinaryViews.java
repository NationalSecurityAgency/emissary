package emissary.output.formatter.filter;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;
import emissary.util.ByteUtil;

import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Denies alternate views containing non-printable (binary) bytes when {@code CONTENT_REJECT_BINARY_VIEWS} is enabled.
 * Parameters always pass.
 */
public class FilterBinaryViews extends AbstractItemFilter {

    public static final String REJECT_BINARY_VIEWS = "CONTENT_REJECT_BINARY_VIEWS";
    public static final String BINARY_CHECK_PARAMS = "CONTENT_BINARY_CHECK_PARAMS";
    public static final String BINARY_CLUE_VALUES = "CONTENT_BINARY_CLUE_VALUES";

    private static final Set<String> DEFAULT_CLUE_VALUES = Set.of("binary", "octet-stream");

    private boolean rejectBinary;
    private Set<String> checkParams = Set.of();
    private Set<String> clueValues = DEFAULT_CLUE_VALUES;

    @Override
    public void configure(@Nullable final Configurator config) {
        super.configure(config);
        if (config == null) {
            return;
        }
        this.rejectBinary = config.findBooleanEntry(REJECT_BINARY_VIEWS, false);
        this.checkParams = new TreeSet<>(config.findEntries(BINARY_CHECK_PARAMS));
        Set<String> configuredClues = new TreeSet<>(config.findEntries(BINARY_CLUE_VALUES));
        if (!configuredClues.isEmpty()) {
            this.clueValues = configuredClues;
        }
    }

    @Override
    public boolean test(final IBaseDataObject d, final String viewName) {
        if (!this.rejectBinary || d == null) {
            return true;
        }
        byte[] view = d.getAlternateView(viewName);
        if (view == null) {
            return true;
        }
        if (!this.checkParams.isEmpty()) {
            Boolean binary = binaryClueOf(d);
            if (binary != null) {
                return !binary;
            }
        }
        return !ByteUtil.hasNonPrintableValues(view);
    }

    /**
     * Whether configured parameters mark the payload binary, or null when none are present.
     */
    @Nullable
    private Boolean binaryClueOf(final IBaseDataObject d) {
        for (final String param : this.checkParams) {
            List<Object> values = d.getParameter(param);
            if (values != null && !values.isEmpty()) {
                return anyValueIndicatesBinary(values) ? Boolean.TRUE : Boolean.FALSE;
            }
        }
        return null;
    }

    private boolean anyValueIndicatesBinary(final List<Object> values) {
        for (final Object value : values) {
            if (value != null && indicatesBinary(String.valueOf(value))) {
                return true;
            }
        }
        return false;
    }

    private boolean indicatesBinary(final String value) {
        String lowered = value.toLowerCase(Locale.ROOT);
        return this.clueValues.stream().anyMatch(lowered::contains);
    }

    @Override
    public String describe(final boolean verbose) {
        StringBuilder sb = new StringBuilder(super.describe(verbose));
        if (verbose) {
            sb.append(' ').append(REJECT_BINARY_VIEWS).append(" = ").append(this.rejectBinary);
            if (!this.checkParams.isEmpty()) {
                sb.append(' ').append(BINARY_CHECK_PARAMS).append(" = ").append(String.join(",", this.checkParams));
            }
            if (!this.clueValues.isEmpty()) {
                sb.append(' ').append(BINARY_CLUE_VALUES).append(" = ").append(String.join(",", this.clueValues));
            }
        }
        return sb.toString();
    }
}
