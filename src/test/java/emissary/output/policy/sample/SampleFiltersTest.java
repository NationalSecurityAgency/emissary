package emissary.output.policy.sample;

import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.output.policy.Decision.Outcome;
import emissary.output.policy.OutputPolicy;
import emissary.output.policy.request.ParamRequest;
import emissary.output.policy.request.PayloadRequest;
import emissary.output.policy.request.ViewRequest;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SampleFiltersTest extends UnitTest {

    @Test
    void maxViewSizeDeniesOnlyLargeViews() {
        final ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry(MaxViewSizeFilter.MAX_VIEW_SIZE, "4");
        final MaxViewSizeFilter filter = new MaxViewSizeFilter();
        filter.configure(config);

        final IBaseDataObject payload = DataObjectFactory.getInstance("data".getBytes(UTF_8), "test", "FTYPE");
        payload.addAlternateView("SMALL", "1234".getBytes(UTF_8));
        payload.addAlternateView("LARGE", "12345".getBytes(UTF_8));
        final PayloadRequest request = PayloadRequest.from(payload);

        assertEquals(Outcome.ABSTAIN, filter.decide(new ViewRequest(request, "SMALL")).outcome());
        assertFalse(filter.decide(new ViewRequest(request, "LARGE")).isAllowed());
        assertEquals(Outcome.ABSTAIN, filter.decide(new ViewRequest(request, OutputPolicy.PRIMARY_VIEW_NAME)).outcome());
        payload.setData("12345".getBytes(UTF_8));
        assertTrue(filter.decide(new ViewRequest(request, OutputPolicy.PRIMARY_VIEW_NAME)).isDenied());
    }

    @Test
    void maxValueSizeDeniesOnlyLargeValues() {
        final ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry(MaxValueSizeFilter.MAX_PARAM_VALUE_SIZE, "4");
        final MaxValueSizeFilter filter = new MaxValueSizeFilter();
        filter.configure(config);

        final IBaseDataObject payload = DataObjectFactory.getInstance();
        final PayloadRequest request = PayloadRequest.from(payload);

        assertEquals(Outcome.ABSTAIN, filter.decide(new ParamRequest(request, "FOO", null)).outcome());
        assertEquals(Outcome.ABSTAIN, filter.decide(new ParamRequest(request, "FOO", "1234")).outcome());
        assertFalse(filter.decide(new ParamRequest(request, "FOO", "12345")).isAllowed());
    }

    @Test
    void sampleFilterLoadsFromPolicy() {
        final ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("POLICY_STAGE", "filter " + MaxValueSizeFilter.class.getName());
        config.addEntry(MaxValueSizeFilter.MAX_PARAM_VALUE_SIZE, "4");

        final OutputPolicy policy = OutputPolicy.from(config);
        final IBaseDataObject payload = DataObjectFactory.getInstance();

        assertTrue(policy.decideParam(payload, "FOO", "1234").isAllowed());
        assertFalse(policy.decideParam(payload, "FOO", "12345").isAllowed());
    }
}
