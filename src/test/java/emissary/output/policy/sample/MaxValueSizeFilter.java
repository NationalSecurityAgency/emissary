package emissary.output.policy.sample;

import emissary.config.Configurator;
import emissary.output.policy.Decision;
import emissary.output.policy.OutputFilter;
import emissary.output.policy.request.ParamRequest;

import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.Set;

/**
 * Test example of a pluggable stage: denies parameter values longer than {@code MAX_PARAM_VALUE_SIZE}.
 *
 * <p>
 * Add it to a policy with {@code POLICY_STAGE = "filter emissary.output.policy.sample.MaxValueSizeFilter"} and set
 * {@code MAX_PARAM_VALUE_SIZE} in the same sink configuration. Keys always abstain; only values are measured.
 */
public final class MaxValueSizeFilter implements OutputFilter {

    /** Maximum value length, parsed with size suffixes such as {@code 4096}. */
    public static final String MAX_PARAM_VALUE_SIZE = "MAX_PARAM_VALUE_SIZE";

    private long maxValueSize;

    @Override
    public String name() {
        return "max-value-size";
    }

    @Override
    public Set<String> requiredContext() {
        return Collections.emptySet();
    }

    @Override
    public void configure(@Nullable final Configurator config) {
        if (config != null) {
            this.maxValueSize = config.findSizeEntry(MAX_PARAM_VALUE_SIZE, 0);
        }
    }

    @Override
    public Decision decide(final ParamRequest request) {
        final Object value = request.value();
        if (value == null) {
            return Decision.abstain(name());
        }
        final int length = String.valueOf(value).length();
        if (length > this.maxValueSize) {
            return Decision.deny(name(), request.key() + " is " + length + " characters");
        }
        return Decision.abstain(name());
    }

    @Override
    public String describe() {
        return name() + " (" + MAX_PARAM_VALUE_SIZE + " = " + this.maxValueSize + ")";
    }
}
