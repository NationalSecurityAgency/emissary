package emissary.output;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.core.EmissaryRuntimeException;
import emissary.output.policy.OutputPolicy.EmitMode;
import emissary.output.sink.JsonMetadataSink;
import emissary.output.sink.JsonSink;
import emissary.output.sink.XmlSink;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OutputDispatchValidationTest extends UnitTest {
    private static final AtomicInteger OPENED = new AtomicInteger();

    @Test
    void invalidPolicyFailsBeforeAnyDestinationsAreOpened(@TempDir final Path root) {
        OPENED.set(0);
        final ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("OUTPUT_PATH", root.toString());
        config.addEntry("OUTPUT_SINK", ValidSink.class.getName());
        config.addEntry("OUTPUT_SINK", InvalidSink.class.getName());
        assertThrows(EmissaryRuntimeException.class, () -> new OutputDispatchPlace(config));
        assertEquals(0, OPENED.get());
        assertFalse(Files.exists(root.resolve("json")));
    }

    @Test
    void parentPathIsASinkSpecificRoot(@TempDir final Path root) {
        final ServiceConfigGuide parent = new ServiceConfigGuide();
        parent.addEntry("OUTPUT_PATH", root.toString());
        final ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("OUTPUT_PATH", "ignored-parent-override");
        final JsonSink first = new JsonSink();
        final JsonSink second = new JsonSink();
        first.prepare(parent, "FIRST", config);
        second.prepare(parent, "SECOND", config);
        assertEquals(root.resolve("first"), first.getOutputDirectory());
        assertEquals(root.resolve("second"), second.getOutputDirectory());
        assertFalse(Files.exists(root.resolve("first")));
    }

    @Test
    void duplicateNamesAndDirectoriesAreRejectedBeforeOpening(@TempDir final Path root) {
        OPENED.set(0);
        final ServiceConfigGuide duplicateNames = new ServiceConfigGuide();
        duplicateNames.addEntry("OUTPUT_SINK", "JSON:" + ValidSink.class.getName());
        duplicateNames.addEntry("OUTPUT_SINK", "json:" + ValidSink.class.getName());
        assertThrows(EmissaryRuntimeException.class, () -> new OutputDispatchPlace(duplicateNames));
        assertEquals(0, OPENED.get());

        final ServiceConfigGuide duplicatePaths = new ServiceConfigGuide();
        duplicatePaths.addEntry("SHARED_PATH", root.toString());
        duplicatePaths.addEntry("OUTPUT_SINK", SharedJsonSink.class.getName());
        duplicatePaths.addEntry("OUTPUT_SINK", SharedXmlSink.class.getName());
        assertThrows(EmissaryRuntimeException.class, () -> new OutputDispatchPlace(duplicatePaths));
        assertEquals(0, OPENED.get());
    }

    @Test
    void labelsDoNotSelectSinkConfiguration() {
        final ServiceConfigGuide parent = new ServiceConfigGuide();
        final JsonSink named = new JsonSink();
        named.prepare(parent, "JSONM", null);
        final JsonMetadataSink existing = new JsonMetadataSink();
        existing.prepare(parent, null, null);
        final JsonMetadataSink renamed = new JsonMetadataSink();
        renamed.prepare(parent, "REPORT", null);
        assertEquals(EmitMode.BOTH, named.getPolicy().emitMode());
        assertEquals(EmitMode.METADATA, existing.getPolicy().emitMode());
        assertEquals(existing.getPolicy().describe(), renamed.getPolicy().describe());
        assertEquals("jsonm", named.getOutputDirectory().getFileName().toString());
        assertEquals("jsonm", existing.getOutputDirectory().getFileName().toString());
        assertEquals("report", renamed.getOutputDirectory().getFileName().toString());
    }

    @Test
    void suppliedPathAlreadyEndingInSinkDirectoryIsNotDuplicated(@TempDir final Path root) {
        final ServiceConfigGuide parent = new ServiceConfigGuide();
        final ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("OUTPUT_PATH", root.resolve("jsonm").toString());
        final JsonMetadataSink sink = new JsonMetadataSink();
        sink.prepare(parent, "JSONM", config);
        assertEquals(root.resolve("jsonm"), sink.getOutputDirectory());
    }

    @Test
    void suppliedConfigurationOverridesClassDefaults() {
        final ServiceConfigGuide parent = new ServiceConfigGuide();
        final ServiceConfigGuide metadata = new ServiceConfigGuide();
        metadata.addEntry("EMIT", "metadata");
        final JsonSink sink = new JsonSink();
        sink.prepare(parent, "REPORT", metadata);
        assertEquals(EmitMode.METADATA, sink.getPolicy().emitMode());
    }

    public static class ValidSink extends JsonSink {
        @Override
        protected Configurator resolveSinkConfig(final Configurator supplied) {
            return new ServiceConfigGuide();
        }

        @Override
        public void initializePrepared() {
            OPENED.incrementAndGet();
        }
    }

    public static final class InvalidSink extends ValidSink {
        @Override
        protected Configurator resolveSinkConfig(final Configurator supplied) {
            final ServiceConfigGuide config = new ServiceConfigGuide();
            config.addEntry("POLICY_STAGE", "rule deny table FOO");
            return config;
        }
    }

    public static final class SharedJsonSink extends ValidSink {
        @Override
        protected Configurator resolveSinkConfig(final Configurator supplied) {
            return sharedConfig(this.configG);
        }
    }

    public static final class SharedXmlSink extends XmlSink {
        @Override
        protected Configurator resolveSinkConfig(final Configurator supplied) {
            return sharedConfig(this.configG);
        }

        @Override
        public void initializePrepared() {
            OPENED.incrementAndGet();
        }
    }

    private static Configurator sharedConfig(final Configurator parent) {
        final ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("OUTPUT_PATH", parent.findStringEntry("SHARED_PATH"));
        return config;
    }
}
