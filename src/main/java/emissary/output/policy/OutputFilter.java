package emissary.output.policy;

import emissary.config.Configurator;
import emissary.output.policy.request.ParamRequest;
import emissary.output.policy.request.PayloadRequest;
import emissary.output.policy.request.ViewRequest;

import jakarta.annotation.Nullable;

import java.util.Set;

/**
 * A custom stage in an {@link OutputPolicy}.
 *
 * <p>
 * Return {@link Decision#abstain(String)} when the stage has no opinion. Every decision names the stage so the log
 * shows why output was allowed or skipped. Implementations must be thread-safe after configuration and must not mutate
 * requests or their payloads. Evaluation is also used for simulations, and must not perform output or other side
 * effects.
 */
public interface OutputFilter {

    /** The name shown in traces and startup descriptions. */
    String name();

    /** Context needed for a faithful simulation. Custom filters default to requiring the full supplied record. */
    default Set<String> requiredContext() {
        return Set.of("content", "views", "parameters");
    }

    /** Configure this filter from the policy configuration. */
    default void configure(@Nullable final Configurator config) {
        // nothing by default
    }

    /** Decide whether a whole payload may be written. */
    default Decision decide(final PayloadRequest request) {
        return Decision.abstain(name());
    }

    /** Decide whether one alternate view may be written. */
    default Decision decide(final ViewRequest request) {
        return Decision.abstain(name());
    }

    /** Decide whether a parameter key, or one value of it, may be written. */
    default Decision decide(final ParamRequest request) {
        return Decision.abstain(name());
    }

    /** A one-line description of the configured stage, for startup logging. */
    default String describe() {
        return name();
    }
}
