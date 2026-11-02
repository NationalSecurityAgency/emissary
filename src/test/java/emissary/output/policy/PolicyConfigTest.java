package emissary.output.policy;

import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.EmissaryRuntimeException;
import emissary.core.IBaseDataObject;
import emissary.output.policy.request.ParamRequest;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyConfigTest extends UnitTest {

    private static IBaseDataObject payload() {
        return DataObjectFactory.getInstance(new byte[0], "test", "XML");
    }

    @Test
    void noConfigAllowsEverything() {
        final OutputPolicy policy = PolicyConfig.from(null);

        assertTrue(policy.isAllowAll());
        assertTrue(policy.decideView(payload(), "JSON_ML").isAllowed());
    }

    @Test
    void readsDefault() {
        final ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("POLICY_DEFAULT", "deny");

        final OutputPolicy policy = PolicyConfig.from(config);
        assertTrue(policy.decideView(payload(), "JSON_ML").isDenied());
        assertTrue(policy.decideParam(payload(), "FOO").isDenied());
    }

    @Test
    void readsOrderedStages() {
        final ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("POLICY_STAGE", "rule deny view JSON_ML");
        config.addEntry("POLICY_STAGE", "rule allow view JSON_*");

        final OutputPolicy policy = PolicyConfig.from(config);

        assertTrue(policy.decideView(payload(), "JSON_ML").isDenied());
        assertTrue(policy.decideView(payload(), "JSON_TEXT").isAllowed());
        assertTrue(policy.decideView(payload(), "XML_TEXT").isAllowed());
    }

    @Test
    void emitMetadataDeniesContentOnly() {
        final ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("EMIT", "metadata");

        final OutputPolicy policy = PolicyConfig.from(config);

        assertTrue(policy.decidePayload(payload()).isAllowed());
        assertTrue(policy.decideView(payload(), "JSON_ML").isDenied());
        assertTrue(policy.decideParam(payload(), "FOO").isAllowed());
        assertTrue(policy.decideParam(payload(), "FOO", "bar").isAllowed());
    }

    @Test
    void emitContentDeniesMetadataOnly() {
        final ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("EMIT", "content");

        final OutputPolicy policy = PolicyConfig.from(config);

        assertTrue(policy.decidePayload(payload()).isAllowed());
        assertTrue(policy.decideView(payload(), "JSON_ML").isAllowed());
        assertTrue(policy.decideParam(payload(), "FOO").isDenied());
    }

    @Test
    void loadsFilterStageById() {
        final ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("POLICY_STAGE", "filter " + DenyParamFilter.class.getName());

        final OutputPolicy policy = PolicyConfig.from(config);

        assertTrue(policy.decideParam(payload(), "FOO").isDenied());
        assertTrue(policy.decideView(payload(), "JSON_ML").isAllowed());
    }

    @Test
    void defaultBeforeRulesLetsRulesOverride() {
        final ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("POLICY_DEFAULT", "deny");
        config.addEntry("POLICY_STAGE", "rule allow view JSON_ML");

        final OutputPolicy policy = PolicyConfig.from(config);

        assertTrue(policy.decideView(payload(), "JSON_ML").isAllowed());
        assertTrue(policy.decideView(payload(), "JSON_TEXT").isDenied());
    }

    @Test
    void rejectsInvalidConfiguration() {
        final ServiceConfigGuide badDefault = new ServiceConfigGuide();
        badDefault.addEntry("POLICY_DEFAULT", "sometimes");
        assertThrows(EmissaryRuntimeException.class, () -> PolicyConfig.from(badDefault));

        final ServiceConfigGuide badEmit = new ServiceConfigGuide();
        badEmit.addEntry("EMIT", "everything");
        assertThrows(EmissaryRuntimeException.class, () -> PolicyConfig.from(badEmit));

        final ServiceConfigGuide badStage = new ServiceConfigGuide();
        badStage.addEntry("POLICY_STAGE", "rule deny table FOO");
        assertThrows(EmissaryRuntimeException.class, () -> PolicyConfig.from(badStage));

        final ServiceConfigGuide badFilter = new ServiceConfigGuide();
        badFilter.addEntry("POLICY_STAGE", "filter no.such.Filter");
        assertThrows(EmissaryRuntimeException.class, () -> PolicyConfig.from(badFilter));

        final ServiceConfigGuide notAFilter = new ServiceConfigGuide();
        notAFilter.addEntry("POLICY_STAGE", "filter java.lang.String");
        assertThrows(EmissaryRuntimeException.class, () -> PolicyConfig.from(notAFilter));

        final ServiceConfigGuide empty = new ServiceConfigGuide();
        empty.addEntry("POLICY_STAGE", "   ");
        assertThrows(EmissaryRuntimeException.class, () -> PolicyConfig.from(empty));
    }

    @Test
    void customFilterCanBePlacedBeforeRules() {
        final ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("POLICY_STAGE", "filter " + DenyParamFilter.class.getName());
        config.addEntry("POLICY_STAGE", "rule allow param FOO");

        final OutputPolicy policy = PolicyConfig.from(config);

        assertTrue(policy.decideParam(payload(), "FOO").isDenied());
        assertTrue(policy.decideParam(payload(), "BAR").isDenied());
    }

    /** Test filter that denies every parameter decision. */
    public static final class DenyParamFilter implements OutputFilter {

        @Override
        public String name() {
            return "deny-params";
        }

        @Override
        public Decision decide(final ParamRequest request) {
            return Decision.deny(name(), "test filter");
        }
    }
}
