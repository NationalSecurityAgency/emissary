package emissary.output.sink;

import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.output.policy.Decision;
import emissary.output.policy.OutputFilter;
import emissary.output.policy.OutputPolicy;
import emissary.output.policy.request.ParamRequest;
import emissary.test.core.junit5.UnitTest;
import emissary.util.xml.JDOMUtil;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class XmlWriterTest extends UnitTest {

    private static IBaseDataObject payload() {
        final IBaseDataObject data = DataObjectFactory.getInstance("This is the data".getBytes(UTF_8), "testfile", "FTYPE");
        data.appendParameter("FOO", "keep");
        data.appendParameter("SECRET", "hidden");
        data.addAlternateView("KEEP", "view".getBytes(UTF_8));
        data.addAlternateView("DROP", "other".getBytes(UTF_8));
        return data;
    }

    private static String format(final String... stages) throws IOException {
        final ServiceConfigGuide config = new ServiceConfigGuide();
        for (final String stage : stages) {
            config.addEntry("POLICY_STAGE", stage);
        }
        return format(config);
    }

    private static String format(final ServiceConfigGuide config) throws IOException {
        final XmlWriter writer = new XmlWriter(OutputPolicy.from(config));
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.writeTo(out, Collections.singletonList(payload()), new HashMap<>());
        return new String(out.toByteArray(), UTF_8);
    }

    @Test
    void filtersViewsAndParameters() throws IOException {
        final String xml = format("rule deny view DROP", "rule deny param SECRET");

        assertTrue(xml.contains("KEEP"));
        assertFalse(xml.contains("DROP"));
        assertTrue(xml.contains("FOO"));
        assertFalse(xml.contains("SECRET"));
    }

    @Test
    void emitMetadataOmitsContent() throws IOException {
        final ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("EMIT", "metadata");
        final String xml = format(config);

        assertFalse(xml.contains("<data>"));
        assertFalse(xml.contains("<views>"));
        assertTrue(xml.contains("FOO"));
    }

    @Test
    void emitContentOmitsMetadata() throws IOException {
        final ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("EMIT", "content");
        final String xml = format(config);

        assertTrue(xml.contains("<data>"));
        assertTrue(xml.contains("KEEP"));
        assertFalse(xml.contains("<metadata>"));
        assertFalse(xml.contains("FOO"));
    }

    @Test
    void filtersOriginalValuesBeforeJoiningOrEncoding() throws IOException {
        final ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("POLICY_STAGE", "rule deny value FOO=bad,BINARY=*");
        final IBaseDataObject data = payload();
        data.appendParameter("FOO", "bad");
        data.putParameter("BINARY", "\u0001\u0002\u0003");
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final OutputPolicy policy = OutputPolicy.from(config);
        new XmlWriter(policy).writeTo(out, List.of(data), new HashMap<>());
        final String xml = out.toString(UTF_8);
        assertTrue(xml.contains("keep"));
        assertFalse(xml.contains("bad"));
        assertFalse(xml.contains("BINARY"));
        assertFalse(policy.traceParam(data, "FOO", "bad").emitted());
    }

    @Test
    void metadataOutputDoesNotReadPrimaryContent() throws IOException {
        final ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("EMIT", "metadata");
        final IBaseDataObject data = spy(payload());
        doThrow(new AssertionError("Denied bytes must not be loaded")).when(data).data();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        new XmlWriter(OutputPolicy.from(config)).writeTo(out, List.of(data), new HashMap<>());
        verify(data, never()).data();
        assertTrue(out.toString(UTF_8).contains("FOO"));
    }

    @Test
    void customValueFiltersReceiveOriginalTypedValues() throws Exception {
        final ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("POLICY_STAGE", "filter " + DenyNumbers.class.getName());
        final IBaseDataObject data = payload();
        data.putParameter("TYPED", List.of("keep", 42));
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        new XmlWriter(OutputPolicy.from(config)).writeTo(out, List.of(data), new HashMap<>());
        assertEquals("keep", JDOMUtil.createDocument(out.toString(UTF_8), false)
                .getRootElement().getChild("payload").getChild("metadata").getChildren("param").stream()
                .filter(element -> "TYPED".equals(element.getAttributeValue("name"))).findFirst().orElseThrow().getText());
    }

    public static final class DenyNumbers implements OutputFilter {
        @Override
        public String name() {
            return "deny-numbers";
        }

        @Override
        public Decision decide(final ParamRequest request) {
            return request.value() instanceof Number ? Decision.deny(name(), "Number") : Decision.abstain(name());
        }
    }
}
