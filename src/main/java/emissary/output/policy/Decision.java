package emissary.output.policy;

import jakarta.annotation.Nullable;

import java.util.Objects;

/** An output decision, including the rule or stage that made it. */
public final class Decision {

    /** Stage name used when the policy default decides. */
    public static final String DEFAULT_STAGE = "default";

    /** The result of evaluating one output stage. */
    public enum Outcome {
        /** The item may be output. */
        ALLOW,
        /** The item may not be output. */
        DENY,
        /** This stage takes no position; the next stage decides. */
        ABSTAIN
    }

    private static final Decision DEFAULT_ALLOW = new Decision(Outcome.ALLOW, DEFAULT_STAGE, null);
    private static final Decision DEFAULT_DENY = new Decision(Outcome.DENY, DEFAULT_STAGE, null);

    private final Outcome outcome;
    private final String stage;

    @Nullable
    private final String detail;

    private Decision(final Outcome outcome, final String stage, @Nullable final String detail) {
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        this.stage = Objects.requireNonNull(stage, "stage");
        this.detail = detail;
    }

    /** An allow decision attributed to {@code stage}. */
    public static Decision allow(final String stage) {
        return new Decision(Outcome.ALLOW, stage, null);
    }

    public static Decision allow(final String stage, @Nullable final String detail) {
        return new Decision(Outcome.ALLOW, stage, detail);
    }

    /** A deny decision attributed to {@code stage}, with an optional detail. */
    public static Decision deny(final String stage, @Nullable final String detail) {
        return new Decision(Outcome.DENY, stage, detail);
    }

    /** An abstain decision attributed to {@code stage}. */
    public static Decision abstain(final String stage) {
        return new Decision(Outcome.ABSTAIN, stage, null);
    }

    public static Decision abstain(final String stage, @Nullable final String detail) {
        return new Decision(Outcome.ABSTAIN, stage, detail);
    }

    /** The shared allow decision produced by the policy default. */
    public static Decision defaultAllow() {
        return DEFAULT_ALLOW;
    }

    /** The shared deny decision produced by the policy default. */
    public static Decision defaultDeny() {
        return DEFAULT_DENY;
    }

    public Outcome outcome() {
        return this.outcome;
    }

    public String stage() {
        return this.stage;
    }

    @Nullable
    public String detail() {
        return this.detail;
    }

    /** Whether this decision settles the question rather than deferring to the next stage. */
    public boolean isDecisive() {
        return this.outcome != Outcome.ABSTAIN;
    }

    public boolean isAllowed() {
        return this.outcome == Outcome.ALLOW;
    }

    public boolean isDenied() {
        return this.outcome == Outcome.DENY;
    }

    @Override
    public boolean equals(@Nullable final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Decision)) {
            return false;
        }
        final Decision that = (Decision) other;
        return this.outcome == that.outcome
                && this.stage.equals(that.stage)
                && Objects.equals(this.detail, that.detail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.outcome, this.stage, this.detail);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(this.outcome).append(" by ").append(this.stage);
        if (this.detail != null) {
            sb.append(" (").append(this.detail).append(')');
        }
        return sb.toString();
    }
}
