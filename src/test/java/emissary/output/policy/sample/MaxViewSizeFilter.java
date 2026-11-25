package emissary.output.policy.sample;

import emissary.config.Configurator;
import emissary.output.policy.Decision;
import emissary.output.policy.OutputFilter;
import emissary.output.policy.request.ViewRequest;

import jakarta.annotation.Nullable;

import java.util.Set;

/**
 * Test example of a pluggable stage: denies alternate views larger than {@code MAX_VIEW_SIZE}.
 *
 * <p>
 * Add it to a policy with {@code POLICY_STAGE = "filter emissary.output.policy.sample.MaxViewSizeFilter"} and set
 * {@code MAX_VIEW_SIZE} in the same sink configuration. Configure without a size to allow every view.
 */
public final class MaxViewSizeFilter implements OutputFilter {

    /** Maximum view size, parsed with size suffixes such as {@code 10m}. */
    public static final String MAX_VIEW_SIZE = "MAX_VIEW_SIZE";

    private long maxViewSize;

    @Override
    public String name() {
        return "max-view-size";
    }

    @Override
    public Set<String> requiredContext() {
        return Set.of("content", "views");
    }

    @Override
    public void configure(@Nullable final Configurator config) {
        if (config != null) {
            this.maxViewSize = config.findSizeEntry(MAX_VIEW_SIZE, 0);
        }
    }

    @Override
    public Decision decide(final ViewRequest request) {
        final byte[] view = request.content();
        if (this.maxViewSize > 0 && view != null && view.length > this.maxViewSize) {
            return Decision.deny(name(), request.viewName() + " is " + view.length + " bytes");
        }
        return Decision.abstain(name());
    }

    @Override
    public String describe() {
        return name() + " (" + MAX_VIEW_SIZE + " = " + this.maxViewSize + ")";
    }
}
