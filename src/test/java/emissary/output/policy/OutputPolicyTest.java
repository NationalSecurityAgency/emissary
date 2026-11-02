package emissary.output.policy;

import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.output.policy.Decision.Outcome;
import emissary.output.policy.request.ParamRequest;
import emissary.output.policy.request.PayloadRequest;
import emissary.output.policy.request.ViewRequest;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputPolicyTest extends UnitTest {

    private static IBaseDataObject payload(final String fileType) {
        return DataObjectFactory.getInstance(new byte[0], "test", fileType);
    }

    @Test
    void allowAllPolicyShortCircuits() {
        final OutputPolicy policy = OutputPolicy.allowAll();
        final IBaseDataObject data = payload("XML");

        assertTrue(policy.isAllowAll());
        assertTrue(policy.decidePayload(data).isAllowed());
        assertTrue(policy.decideView(data, "JSON_1_0").isAllowed());
        assertTrue(policy.decideParam(data, "FOO").isAllowed());
        assertTrue(policy.decideParam(data, "FOO", "bar").isAllowed());
        assertEquals(Decision.DEFAULT_STAGE, policy.decidePayload(data).stage());
    }

    @Test
    void defaultDenyAppliesWhenNoStageDecides() {
        final OutputPolicy policy = PolicyConfig.Builder.create().defaults(Outcome.DENY).build();

        assertFalse(policy.isAllowAll());
        assertTrue(policy.decidePayload(payload("XML")).isDenied());
        assertTrue(policy.decideView(payload("XML"), "JSON_1_0").isDenied());
        assertTrue(policy.decideParam(payload("XML"), "FOO").isDenied());
    }

    @Test
    void firstDecisiveStageWins() {
        final OutputPolicy denyFirst = PolicyConfig.Builder.create()
                .stage(new FixedFilter("deny-all", Outcome.DENY))
                .rule("allow view JSON_ML")
                .build();
        assertTrue(denyFirst.decideView(payload("XML"), "JSON_ML").isDenied());
        assertEquals("deny-all", denyFirst.decideView(payload("XML"), "JSON_ML").stage());

        final OutputPolicy allowFirst = PolicyConfig.Builder.create()
                .rule("allow view JSON_ML")
                .stage(new FixedFilter("deny-all", Outcome.DENY))
                .build();
        assertTrue(allowFirst.decideView(payload("XML"), "JSON_ML").isAllowed());
        assertEquals("rule #1", allowFirst.decideView(payload("XML"), "JSON_ML").stage());
    }

    @Test
    void abstainingStagesFallThrough() {
        final OutputPolicy policy = PolicyConfig.Builder.create()
                .stage(new FixedFilter("abstain", Outcome.ABSTAIN))
                .rule("deny param SECRET_*")
                .build();

        assertTrue(policy.decideParam(payload("XML"), "SECRET_TOKEN").isDenied());
        assertTrue(policy.decideParam(payload("XML"), "FOO").isAllowed());
    }

    @Test
    void decisionCarriesRuleText() {
        final OutputPolicy policy = PolicyConfig.Builder.create().rule("deny view JSON_ML").build();
        final Decision decision = policy.decideView(payload("XML"), "JSON_ML");

        assertEquals(Outcome.DENY, decision.outcome());
        assertEquals("rule #1", decision.stage());
        assertEquals("deny view JSON_ML", decision.detail());
    }

    @Test
    void customFilterSeesRequestFacts() {
        final IBaseDataObject data = payload("XML");
        data.setOutputable(false);
        final OutputPolicy policy = PolicyConfig.Builder.create().stage(new FixedFilter("custom", Outcome.ABSTAIN)).build();
        final PayloadRequest request = PayloadRequest.from(data);

        assertEquals("XML", request.fileType());
        assertFalse(request.outputable());
        assertTrue(policy.decidePayload(request).isAllowed());

        final ViewRequest view = new ViewRequest(request, "JSON_1_0");
        assertEquals("JSON_1_0", view.viewName());
        assertEquals("XML", view.payload().fileType());
        assertEquals(data, view.data());

        final ParamRequest param = new ParamRequest(request, "EMAIL", "a@b");
        assertEquals("EMAIL", param.key());
        assertEquals("a@b", param.value());
        assertFalse(param.isKeyDecision());
        assertTrue(new ParamRequest(request, "EMAIL", null).isKeyDecision());
    }

    @Test
    void traceShowsEvaluatedStagesAndAppliedDecision() {
        final OutputPolicy policy = PolicyConfig.Builder.create()
                .stage(new FixedFilter("abstain", Outcome.ABSTAIN))
                .rule("deny view JSON_ML")
                .rule("allow view JSON_ML")
                .build();

        final PolicyTrace trace = policy.traceView(payload("XML"), "JSON_ML");

        assertFalse(trace.emitted());
        assertEquals("rule #1", trace.decision().stage());
        assertEquals(6, trace.decisions().size());
        assertEquals(Outcome.ABSTAIN, trace.decisions().get(0).outcome());
        assertTrue(trace.describe().contains("SKIP - DENY by rule #1"));
        assertTrue(trace.describe().contains("abstain"));
    }

    @Test
    void traceEndsWithDefaultWhenEveryStageAbstains() {
        final OutputPolicy policy = PolicyConfig.Builder.create()
                .stage(new FixedFilter("abstain", Outcome.ABSTAIN))
                .defaults(Outcome.DENY)
                .build();

        final PolicyTrace trace = policy.traceParam(payload("XML"), "FOO");

        assertFalse(trace.emitted());
        assertEquals(Decision.DEFAULT_STAGE, trace.decision().stage());
        assertEquals(2, trace.decisions().size());
        assertTrue(trace.describe().contains("default"));
    }

    @Test
    void describeListsStagesInOrder() {
        final OutputPolicy policy = PolicyConfig.Builder.create()
                .rule("deny view JSON_ML")
                .stage(new FixedFilter("custom", Outcome.ABSTAIN))
                .build();

        final String description = policy.describe();
        assertTrue(description.contains("1. deny view JSON_ML"));
        assertTrue(description.contains("custom"));
    }

    @Test
    void parentGatesPreventComponentEmission() {
        final IBaseDataObject data = payload("XML");
        final OutputPolicy denyPayload = PolicyConfig.Builder.create().rule("deny payload any").build();
        assertFalse(denyPayload.traceView(data, "TEXT").emitted());
        assertTrue(denyPayload.traceView(data, "TEXT").steps().stream()
                .anyMatch(step -> step.target().equals("view") && step.decision() == null));

        final OutputPolicy denyKey = PolicyConfig.Builder.create().rule("deny param SECRET").build();
        assertFalse(denyKey.traceParam(data, "SECRET", "value").emitted());
        assertTrue(denyKey.traceParam(data, "SECRET", "value").steps().stream()
                .anyMatch(step -> step.target().equals("value") && step.decision() == null));
    }

    @Test
    void eachUnmatchedRuleIsExplainedAndAllowCarriesRuleText() {
        final OutputPolicy policy = PolicyConfig.Builder.create().rule("deny view FIRST").rule("deny view SECOND")
                .rule("allow view OTHER").build();
        final PolicyTrace trace = policy.traceView(payload("XML"), "OTHER");
        assertTrue(trace.emitted());
        assertEquals("allow view OTHER", trace.decision().detail());
        assertEquals(2, trace.steps().stream().filter(step -> "view".equals(step.target()) && step.decision() != null
                && step.decision().outcome() == Outcome.ABSTAIN && step.detail().contains("no match")).count());
    }

    @Test
    void laterCustomFiltersAreNotExecutedDuringTrace() {
        final OutputPolicy policy = PolicyConfig.Builder.create().rule("allow payload any").rule("allow view TEXT")
                .stage(new OutputFilter() {
                    @Override
                    public String name() {
                        return "must-not-run";
                    }

                    @Override
                    public Decision decide(final PayloadRequest request) {
                        throw new AssertionError("Payload stage was short-circuited");
                    }

                    @Override
                    public Decision decide(final ViewRequest request) {
                        throw new AssertionError("View stage was short-circuited");
                    }
                }).build();
        assertTrue(policy.traceView(payload("XML"), "TEXT").emitted());
        assertTrue(policy.traceView(payload("XML"), "TEXT").steps().stream()
                .anyMatch(step -> step.stage().equals("must-not-run") && step.decision() == null));
    }

    @Test
    void nestedDenialBlocksFamilyAndComponentTrace() {
        final IBaseDataObject parent = payload("XML");
        final IBaseDataObject child = payload("CHILD");
        child.setOutputable(false);
        parent.setExtractedRecords(List.of(child));
        final OutputPolicy policy = PolicyConfig.Builder.create().rule("deny payload outputable=false").build();
        assertTrue(policy.decideFamily(List.of(parent)).isDenied());
        assertFalse(policy.traceView(parent, "TEXT").emitted());
        assertEquals("deny payload outputable=false", policy.traceView(parent, "TEXT").decision().detail());
    }

    @Test
    void explicitNullIsAValueQuestion() {
        final PayloadRequest data = PayloadRequest.from(payload("XML"));
        final OutputPolicy policy = PolicyConfig.Builder.create().rule("deny value FOO=null").build();
        assertTrue(policy.decideParam(data, "FOO", null).isAllowed());
        assertTrue(policy.decideValue(data, "FOO", null).isDenied());
        assertFalse(policy.traceValue(data, "FOO", null).emitted());
    }

    private static final class FixedFilter implements OutputFilter {
        private final String name;
        private final Outcome outcome;

        private FixedFilter(final String name, final Outcome outcome) {
            this.name = name;
            this.outcome = outcome;
        }

        @Override
        public String name() {
            return this.name;
        }

        @Override
        public Decision decide(final PayloadRequest request) {
            return decision();
        }

        @Override
        public Decision decide(final ViewRequest request) {
            return decision();
        }

        @Override
        public Decision decide(final ParamRequest request) {
            return decision();
        }

        private Decision decision() {
            switch (this.outcome) {
                case ALLOW:
                    return Decision.allow(this.name);
                case DENY:
                    return Decision.deny(this.name, "fixed");
                default:
                    return Decision.abstain(this.name);
            }
        }
    }
}
