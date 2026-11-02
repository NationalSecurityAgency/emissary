package emissary.output.policy.rule;

import emissary.output.policy.Decision.Outcome;
import emissary.output.policy.request.ParamRequest;
import emissary.output.policy.request.PayloadRequest;
import emissary.output.policy.request.ViewRequest;

import jakarta.annotation.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * One declarative decision rule: an action, a dimension, and a matcher. Rules are immutable and evaluated in declared
 * order by {@link RuleStage}.
 */
public final class Rule {

    /** The kinds of output decision a rule can make. */
    public enum Dimension {
        /** Whether the whole payload may be written. */
        PAYLOAD,
        /** Whether one alternate view may be written. */
        VIEW,
        /** Whether one metadata parameter key may be written. */
        PARAM,
        /** Whether one value of a metadata parameter may be written. */
        PARAM_VALUE
    }

    private final int index;
    private final Outcome action;
    private final Dimension dimension;
    private final String text;

    @Nullable
    private final Predicate<PayloadRequest> payloadMatcher;
    @Nullable
    private final Predicate<ViewRequest> viewMatcher;
    @Nullable
    private final Predicate<ParamRequest> paramMatcher;

    private Rule(final int index, final Outcome action, final Dimension dimension, final String text,
            @Nullable final Predicate<PayloadRequest> payloadMatcher, @Nullable final Predicate<ViewRequest> viewMatcher,
            @Nullable final Predicate<ParamRequest> paramMatcher) {
        this.index = index;
        this.action = Objects.requireNonNull(action, "action");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.text = Objects.requireNonNull(text, "text");
        this.payloadMatcher = payloadMatcher;
        this.viewMatcher = viewMatcher;
        this.paramMatcher = paramMatcher;
    }

    static Rule payload(final int index, final Outcome action, final String text, final Predicate<PayloadRequest> matcher) {
        return new Rule(index, action, Dimension.PAYLOAD, text, matcher, null, null);
    }

    static Rule view(final int index, final Outcome action, final String text, final Predicate<ViewRequest> matcher) {
        return new Rule(index, action, Dimension.VIEW, text, null, matcher, null);
    }

    static Rule param(final int index, final Outcome action, final String text, final Predicate<ParamRequest> matcher) {
        return new Rule(index, action, Dimension.PARAM, text, null, null, matcher);
    }

    static Rule value(final int index, final Outcome action, final String text, final Predicate<ParamRequest> matcher) {
        return new Rule(index, action, Dimension.PARAM_VALUE, text, null, null, matcher);
    }

    public int index() {
        return this.index;
    }

    public Outcome action() {
        return this.action;
    }

    public Dimension dimension() {
        return this.dimension;
    }

    public String text() {
        return this.text;
    }

    /** The label used in a {@link emissary.output.policy.Decision}. */
    public String stage() {
        return "rule #" + this.index;
    }

    boolean matches(final PayloadRequest request) {
        return this.payloadMatcher != null && this.payloadMatcher.test(request);
    }

    boolean matches(final ViewRequest request) {
        return this.viewMatcher != null && this.viewMatcher.test(request);
    }

    boolean matches(final ParamRequest request) {
        return this.paramMatcher != null && this.paramMatcher.test(request);
    }

    @Override
    public String toString() {
        return stage() + " " + this.text;
    }
}
