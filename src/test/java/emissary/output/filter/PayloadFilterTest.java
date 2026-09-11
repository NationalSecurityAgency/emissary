package emissary.output.filter;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayloadFilterTest extends UnitTest {

    IBaseDataObject getTestPayload(final String filetype, final List<String> altViews) {
        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("".getBytes());
        payload.setFileType(filetype);
        payload.setFilename("");
        altViews.forEach(viewName -> payload.addAlternateView(viewName, "".getBytes()));
        return payload;
    }

    @Test
    void testPayloadFilterDeniesWholePayload() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("DENY_FILETYPES", "JSON_RARE");
        config.addEntry("DENY_FORMS", "UNKNOWN");

        PayloadFilterCondition f = new PayloadFilterCondition();
        f.initialize(config);

        IBaseDataObject denied = getTestPayload("JSON_RARE", Collections.emptyList());
        IBaseDataObject formDenied = getTestPayload("JSON", Collections.emptyList());
        formDenied.setCurrentForm("UNKNOWN");
        IBaseDataObject allowed = getTestPayload("JSON", Collections.emptyList());

        assertFalse(f.test(denied), "denylisted filetype should deny the payload");
        assertFalse(f.test(formDenied), "denylisted form should deny the payload");
        assertTrue(f.test(allowed), "other payloads should be allowed");
        assertFalse(f.accept(Collections.singletonList(denied)), "deny should apply to the list");
        assertTrue(f.accept(Arrays.asList(allowed, allowed)), "list of allowed payloads should be allowed");
    }

    @Test
    void testPayloadFilterDenyWildcard() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("DENY_FILETYPES", "JSON_*");

        PayloadFilterCondition f = new PayloadFilterCondition();
        f.initialize(config);

        assertFalse(f.test(getTestPayload("JSON_RARE", Collections.emptyList())), "wildcard filetype should deny");
        assertTrue(f.test(getTestPayload("JSON", Collections.emptyList())), "non-matching filetype should be allowed");
    }
}
