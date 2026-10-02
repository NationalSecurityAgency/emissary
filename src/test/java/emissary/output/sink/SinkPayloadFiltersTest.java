package emissary.output.sink;

import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.output.sink.filter.MaxChildrenFilter;
import emissary.output.sink.filter.PayloadFilterCondition;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whole-payload filter behavior configured at the sink level via {@link AbstractSink#PAYLOAD_FILTER}.
 */
class SinkPayloadFiltersTest extends UnitTest {

    @Test
    void testDefaultFilter(@TempDir final Path tmpDir) {
        ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("OUTPUT_PATH", tmpDir.toAbsolutePath().toString());
        config.addEntry("DENY_FILETYPES", "JSON_RARE");

        JsonSink sink = new JsonSink();
        sink.initialize(config, null, config);

        IBaseDataObject denied = DataObjectFactory.getInstance();
        denied.setFileType("JSON_RARE");
        IBaseDataObject allowed = DataObjectFactory.getInstance();
        allowed.setFileType("JSON");

        assertFalse(sink.accept(denied), "default payload filter should deny a listed filetype");
        assertTrue(sink.accept(allowed), "default payload filter should allow other payloads");
    }

    @Test
    void testDeclaredFilters(@TempDir final Path tmpDir) {
        ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("OUTPUT_PATH", tmpDir.toAbsolutePath().toString());
        config.addEntry(AbstractSink.PAYLOAD_FILTER, PayloadFilterCondition.class.getName());
        config.addEntry(AbstractSink.PAYLOAD_FILTER, MaxChildrenFilter.class.getName());
        config.addEntry(MaxChildrenFilter.MAX_CHILDREN, "1");
        config.addEntry("DENY_FILETYPES", "RARE");

        JsonSink sink = new JsonSink();
        sink.initialize(config, null, config);

        IBaseDataObject tld = DataObjectFactory.getInstance();
        tld.setFileType("COMMON");
        tld.setNumChildren(0);

        IBaseDataObject overLimit = DataObjectFactory.getInstance();
        overLimit.setFileType("COMMON");
        overLimit.setNumChildren(5);

        IBaseDataObject rare = DataObjectFactory.getInstance();
        rare.setFileType("RARE");
        rare.setNumChildren(0);

        assertTrue(sink.accept(tld), "small family should be allowed");
        assertFalse(sink.accept(overLimit), "family over the child limit should be denied");
        assertFalse(sink.accept(rare), "deny-first payload filter should still apply");

        IBaseDataObject child1 = DataObjectFactory.getInstance();
        child1.setFileType("COMMON");
        assertTrue(sink.accept(List.of(tld, child1)), "family list within threshold should be allowed");

        IBaseDataObject child2 = DataObjectFactory.getInstance();
        child2.setFileType("COMMON");
        assertFalse(sink.accept(List.of(tld, child1, child2)), "family list over threshold should be denied");
    }
}
