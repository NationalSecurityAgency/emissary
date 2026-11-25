package emissary.output.sink;

import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.output.policy.Decision;
import emissary.output.policy.OutputFilter;
import emissary.output.policy.request.PayloadRequest;
import emissary.output.roller.JournaledCoalescer;
import emissary.output.roller.journal.JournalReader;
import emissary.output.sink.ISink.WriteStatus;
import emissary.spi.ObjectTracingService;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;

class AbstractRollableSinkTest extends UnitTest {
    @Test
    void commitsSuccessfulWriteAndChecksEligibilityOnlyOnce(@TempDir final Path root) throws Exception {
        final TestSink sink = sink(false);
        sink.rollable = new JournaledCoalescer(root, () -> "test-output", 1);
        final IBaseDataObject data = DataObjectFactory.getInstance(new byte[] {1}, "test", "TEST");
        CountingFilter.COUNT.set(0);
        assertEquals(WriteStatus.SUCCESS, sink.write(data, new HashMap<>()));
        assertEquals(1, CountingFilter.COUNT.get());
        assertEquals(2, committedOffset(root));
        sink.rollable.close();
    }

    @Test
    void failedWriteDoesNotCommitOrEmitSuccessfulLifecycleEvent(@TempDir final Path root) throws Exception {
        final TestSink sink = sink(true);
        sink.rollable = new JournaledCoalescer(root, () -> "test-output", 1);
        sink.enableObjectTrace = true;
        try (MockedStatic<ObjectTracingService> tracing = mockStatic(ObjectTracingService.class)) {
            final IBaseDataObject data = DataObjectFactory.getInstance(new byte[] {1}, "test", "TEST");
            assertEquals(WriteStatus.FAILURE, sink.write(data, new HashMap<>()));
            assertEquals(0, committedOffset(root));
            tracing.verifyNoInteractions();
        }
        sink.rollable.close();
    }

    private static TestSink sink(final boolean fail) {
        final TestSink sink = new TestSink(fail);
        final ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("POLICY_STAGE", "filter " + CountingFilter.class.getName());
        sink.initialize(config, "TEST", config);
        return sink;
    }

    private static long committedOffset(final Path root) throws IOException {
        final Path journal = JournalReader.getJournalPaths(root).iterator().next();
        try (JournalReader reader = new JournalReader(journal)) {
            return reader.getJournal().getLastEntry().getOffset();
        }
    }

    private static final class TestSink extends AbstractRollableSink {
        private final boolean fail;

        TestSink(final boolean fail) {
            this.fail = fail;
        }

        @Override
        public void initializePrepared() {
            // Destination supplied by the test; no filesystem or roll manager resources.
        }

        @Override
        protected void writePayloads(final OutputStream out, final List<IBaseDataObject> list, final Map<String, Object> params)
                throws IOException {
            out.write(1);
            if (this.fail) {
                throw new IOException("Simulated write failure");
            }
        }
    }

    public static final class CountingFilter implements OutputFilter {
        private static final AtomicInteger COUNT = new AtomicInteger();

        @Override
        public String name() {
            return "counting";
        }

        @Override
        public Decision decide(final PayloadRequest request) {
            COUNT.incrementAndGet();
            return Decision.abstain(name());
        }
    }
}
