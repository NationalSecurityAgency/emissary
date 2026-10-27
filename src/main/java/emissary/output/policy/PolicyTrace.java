package emissary.output.policy;

import jakarta.annotation.Nullable;

import java.util.List;
import java.util.stream.Collectors;

/** Complete simulation of the gates leading to output, including stages skipped by short-circuiting. */
@SuppressWarnings("ImmutableMemberCollection")
public final class PolicyTrace {
    private final List<Step> steps;
    private final List<Decision> decisions;

    private PolicyTrace(final List<Step> steps) {
        this.steps = List.copyOf(steps);
        this.decisions = steps.stream().filter(step -> step.decision != null).map(step -> step.decision).collect(Collectors.toUnmodifiableList());
    }

    static PolicyTrace ofSteps(final List<Step> steps) {
        return new PolicyTrace(steps);
    }

    /** Evaluated decisions only, in order. */
    public List<Decision> decisions() {
        return this.decisions;
    }

    public List<Step> steps() {
        return this.steps;
    }

    public Decision decision() {
        if (this.decisions.isEmpty()) {
            return Decision.defaultAllow();
        }
        return this.decisions.get(this.decisions.size() - 1);
    }

    /** Whether the supplied family and requested component would pass all applicable policy gates. */
    public boolean emitted() {
        return decision().isAllowed();
    }

    public String describe() {
        return (emitted() ? "EMIT" : "SKIP") + " - " + decision() + "; path: "
                + this.steps.stream().filter(step -> step.decision != null)
                        .map(step -> step.target + ": " + step.decision).collect(Collectors.joining(" -> "));
    }

    @Override
    public String toString() {
        return describe();
    }

    /** One evaluated or explicitly unevaluated step. Kept nested as a trace implementation/API detail. */
    public static final class Step {
        private final String target;
        private final String stage;
        @Nullable
        private final String detail;
        @Nullable
        private final Decision decision;

        private Step(final String target, final String stage, @Nullable final String detail, @Nullable final Decision decision) {
            this.target = target;
            this.stage = stage;
            this.detail = detail;
            this.decision = decision;
        }

        static Step evaluated(final String target, final Decision decision) {
            return new Step(target, decision.stage(), decision.detail(), decision);
        }

        static Step skipped(final String target, final String stage, final String detail) {
            return new Step(target, stage, detail, null);
        }

        public String target() {
            return this.target;
        }

        public String stage() {
            return this.stage;
        }

        @Nullable
        public String detail() {
            return this.detail;
        }

        @Nullable
        public Decision decision() {
            return this.decision;
        }
    }
}
