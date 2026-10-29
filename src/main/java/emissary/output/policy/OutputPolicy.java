package emissary.output.policy;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;
import emissary.output.policy.Decision.Outcome;
import emissary.output.policy.request.ParamRequest;
import emissary.output.policy.request.PayloadRequest;
import emissary.output.policy.request.ViewRequest;

import jakarta.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Compiled output policy. First decisive stage wins within each gate; the configured default applies otherwise.
 * Serialization and simulation use the same evaluator. Traces compose family eligibility with the requested gates.
 * Custom stages must obey {@link OutputFilter}'s read-only, thread-safe contract.
 */
@SuppressWarnings("ImmutableMemberCollection")
public final class OutputPolicy {
    public static final String PRIMARY_VIEW_NAME = "PrimaryView";

    /** Which output parts the policy emits. */
    public enum EmitMode {
        BOTH, METADATA, CONTENT
    }

    private static final OutputPolicy ALLOW_ALL = new OutputPolicy(Collections.emptyList(), Outcome.ALLOW, EmitMode.BOTH);
    private final List<OutputFilter> stages;
    private final Outcome defaultOutcome;
    private final EmitMode emitMode;
    private final boolean allowAll;

    OutputPolicy(final List<OutputFilter> stages, final Outcome defaultOutcome, final EmitMode emitMode) {
        this.stages = List.copyOf(stages);
        this.defaultOutcome = Objects.requireNonNull(defaultOutcome, "defaultOutcome");
        this.emitMode = Objects.requireNonNull(emitMode, "emitMode");
        this.allowAll = this.stages.isEmpty() && defaultOutcome == Outcome.ALLOW && emitMode == EmitMode.BOTH;
    }

    public static OutputPolicy allowAll() {
        return ALLOW_ALL;
    }

    public static OutputPolicy from(@Nullable final Configurator config) {
        return PolicyConfig.from(config);
    }

    public boolean isAllowAll() {
        return this.allowAll;
    }

    /**
     * The configured default when no stage decides: {@link Outcome#ALLOW} is a deny-list posture, {@link Outcome#DENY} is
     * an allow-list.
     */
    public Outcome defaultOutcome() {
        return this.defaultOutcome;
    }

    public EmitMode emitMode() {
        return this.emitMode;
    }

    /** Context custom stages need for faithful simulations; declarative rules need only payload facts. */
    public Set<String> requiredContext() {
        final Set<String> required = new TreeSet<>();
        this.stages.forEach(stage -> required.addAll(stage.requiredContext()));
        return Collections.unmodifiableSet(required);
    }

    public Decision decidePayload(final IBaseDataObject payload) {
        return decidePayload(PayloadRequest.from(payload));
    }

    public Decision decidePayload(final PayloadRequest request) {
        return evaluate("payload", stage -> stage.decide(request), null, null);
    }

    /** A family is emitted atomically: every listed and nested record must be eligible. */
    public Decision decideFamily(final List<IBaseDataObject> family) {
        return evaluateFamily(family, null);
    }

    public Decision decideView(final IBaseDataObject payload, final String viewName) {
        return decideView(PayloadRequest.from(payload), viewName);
    }

    /** Component question; serializers call this after family eligibility has been checked. */
    public Decision decideView(final PayloadRequest payload, final String viewName) {
        return evaluateView(payload, viewName, null);
    }

    public Decision decideParam(final IBaseDataObject payload, final String key) {
        return decideParam(PayloadRequest.from(payload), key, null);
    }

    public Decision decideParam(final IBaseDataObject payload, final String key, @Nullable final Object value) {
        return decideParam(PayloadRequest.from(payload), key, value);
    }

    /** Null asks the key question; a non-null value asks the key gate then the value gate, matching {@link #traceParam}. */
    public Decision decideParam(final PayloadRequest payload, final String key, @Nullable final Object value) {
        if (value == null) {
            return evaluateParam(new ParamRequest(payload, key, null), null);
        }
        final Decision keyDecision = evaluateParam(new ParamRequest(payload, key, null), null);
        if (keyDecision.isDenied()) {
            return keyDecision;
        }
        return evaluateParam(ParamRequest.forValue(payload, key, value), null);
    }

    /** Explicit value question, including null values. */
    public Decision decideValue(final PayloadRequest payload, final String key, @Nullable final Object value) {
        return evaluateParam(ParamRequest.forValue(payload, key, value), null);
    }

    public PolicyTrace tracePayload(final IBaseDataObject payload) {
        return traceFamily(Collections.singletonList(payload));
    }

    public PolicyTrace tracePayload(final PayloadRequest request) {
        final List<PolicyTrace.Step> steps = new ArrayList<>();
        evaluateFamily(request, steps);
        return PolicyTrace.ofSteps(steps);
    }

    public PolicyTrace traceFamily(final List<IBaseDataObject> family) {
        final List<PolicyTrace.Step> steps = new ArrayList<>();
        evaluateFamily(family, steps);
        return PolicyTrace.ofSteps(steps);
    }

    /** Complete view explanation, including eligibility of nested records. */
    public PolicyTrace traceView(final IBaseDataObject payload, final String viewName) {
        return traceView(PayloadRequest.from(payload), viewName);
    }

    public PolicyTrace traceView(final PayloadRequest payload, final String viewName) {
        final List<PolicyTrace.Step> steps = new ArrayList<>();
        if (evaluateFamily(payload, steps).isAllowed()) {
            evaluateView(payload, viewName, steps);
        } else {
            steps.add(PolicyTrace.Step.skipped("view", "view", "Family eligibility denied output"));
        }
        return PolicyTrace.ofSteps(steps);
    }

    public PolicyTrace traceParam(final IBaseDataObject payload, final String key) {
        return traceParam(payload, key, null);
    }

    public PolicyTrace traceParam(final IBaseDataObject payload, final String key, @Nullable final Object value) {
        return traceParam(PayloadRequest.from(payload), key, value);
    }

    /** Complete parameter explanation. A value question includes both the key and value gates. */
    public PolicyTrace traceParam(final PayloadRequest payload, final String key, @Nullable final Object value) {
        return traceParameter(payload, key, value, value != null);
    }

    public PolicyTrace traceValue(final PayloadRequest payload, final String key, @Nullable final Object value) {
        return traceParameter(payload, key, value, true);
    }

    private PolicyTrace traceParameter(final PayloadRequest payload, final String key, @Nullable final Object value,
            final boolean valueQuestion) {
        final List<PolicyTrace.Step> steps = new ArrayList<>();
        final boolean eligible = evaluateFamily(payload, steps).isAllowed();
        final boolean keyAllowed;
        if (eligible) {
            keyAllowed = evaluateParam(new ParamRequest(payload, key, null), steps).isAllowed();
        } else {
            steps.add(PolicyTrace.Step.skipped("param", "param", "Family eligibility denied output"));
            keyAllowed = false;
        }
        if (valueQuestion) {
            if (keyAllowed) {
                evaluateParam(ParamRequest.forValue(payload, key, value), steps);
            } else {
                steps.add(PolicyTrace.Step.skipped("value", "value", "An earlier gate denied output"));
            }
        }
        return PolicyTrace.ofSteps(steps);
    }

    private Decision evaluateView(final PayloadRequest payload, final String name, @Nullable final List<PolicyTrace.Step> steps) {
        return evaluate("view", stage -> stage.decide(new ViewRequest(payload, name)),
                this.emitMode == EmitMode.METADATA ? "EMIT=metadata" : null, steps);
    }

    private Decision evaluateParam(final ParamRequest request, @Nullable final List<PolicyTrace.Step> steps) {
        return evaluate(request.isKeyDecision() ? "param" : "value", stage -> stage.decide(request),
                this.emitMode == EmitMode.CONTENT ? "EMIT=content" : null, steps);
    }

    private Decision evaluateFamily(final PayloadRequest root, @Nullable final List<PolicyTrace.Step> steps) {
        return evaluateFamily(Collections.singletonList(root.payload()), root, steps);
    }

    private Decision evaluateFamily(final List<IBaseDataObject> family, @Nullable final List<PolicyTrace.Step> steps) {
        return evaluateFamily(family, null, steps);
    }

    private Decision evaluateFamily(final List<IBaseDataObject> family, @Nullable final PayloadRequest root,
            @Nullable final List<PolicyTrace.Step> steps) {
        if (family.isEmpty()) {
            return record(steps, "payload", Decision.deny("family", "Empty family"));
        }
        final Set<IBaseDataObject> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        final ArrayDeque<IBaseDataObject> pending = new ArrayDeque<>(family);
        Decision result = Decision.defaultAllow();
        while (!pending.isEmpty()) {
            final IBaseDataObject data = pending.removeFirst();
            if (!visited.add(data)) {
                continue;
            }
            final PayloadRequest facts = root != null && root.payload() == data ? root : PayloadRequest.from(data);
            result = evaluate("payload:" + data.shortName(), stage -> stage.decide(facts), null, steps);
            if (result.isDenied()) {
                return result;
            }
            final List<IBaseDataObject> children = data.getExtractedRecords();
            if (children != null) {
                pending.addAll(children);
            }
        }
        return result;
    }

    /** Optional collection makes normal writes and traces follow exactly the same short-circuiting evaluator. */
    private Decision evaluate(final String target, final Function<OutputFilter, Decision> evaluator,
            @Nullable final String suppressed, @Nullable final List<PolicyTrace.Step> steps) {
        if (suppressed != null) {
            final Decision decision = record(steps, target, Decision.deny("emit", suppressed));
            skipRemaining(steps, target, 0);
            return decision;
        }
        for (int i = 0; i < this.stages.size(); i++) {
            final Decision decision = record(steps, target, Objects.requireNonNull(evaluator.apply(this.stages.get(i)), "stage decision"));
            if (decision.isDecisive()) {
                skipRemaining(steps, target, i + 1);
                return decision;
            }
        }
        return record(steps, target, this.defaultOutcome == Outcome.ALLOW ? Decision.defaultAllow() : Decision.defaultDeny());
    }

    private void skipRemaining(@Nullable final List<PolicyTrace.Step> steps, final String target, final int start) {
        if (steps != null) {
            for (int i = start; i < this.stages.size(); i++) {
                final OutputFilter stage = this.stages.get(i);
                steps.add(PolicyTrace.Step.skipped(target, stage.name(), stage.describe()));
            }
            steps.add(PolicyTrace.Step.skipped(target, Decision.DEFAULT_STAGE, "An earlier stage decided"));
        }
    }

    private static Decision record(@Nullable final List<PolicyTrace.Step> steps, final String target, final Decision decision) {
        if (steps != null) {
            steps.add(PolicyTrace.Step.evaluated(target, decision));
        }
        return decision;
    }

    public String describe() {
        final StringBuilder sb = new StringBuilder("default=").append(this.defaultOutcome.name().toLowerCase(Locale.ROOT));
        sb.append(" emit=").append(this.emitMode.name().toLowerCase(Locale.ROOT));
        if (this.stages.isEmpty()) {
            return sb.append("; no stages").toString();
        }
        for (final OutputFilter stage : this.stages) {
            sb.append("; ").append(stage.describe());
        }
        return sb.toString();
    }
}
