package emissary.output.stats;

import emissary.core.Namespace;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.Marker;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ViewOutputStatsTest extends UnitTest {

    @Test
    void testCountAndFlushEmitsOneLinePerKey() {
        ViewOutputStats stats = new ViewOutputStats(10, TimeUnit.MINUTES);

        // two objects had PrimaryView (PDF, OK), one had NormalizedText (PDF, OK),
        // one had NormalizedText that was suppressed (PDF, NOT_OUTPUTTABLE)
        stats.count("PrimaryView", "PDF", "OK");
        stats.count("PrimaryView", "PDF", "OK");
        stats.count("NormalizedText", "PDF", "OK");
        stats.count("NormalizedText", "PDF", "NOT_OUTPUTTABLE");

        Logger logger = mock(Logger.class);
        stats.logStats(logger);

        // one structured line per distinct viewName x fileType x status key
        verify(logger, times(3)).info(any(Marker.class), eq(""));
    }

    @Test
    void testFlushResetsWindow() {
        ViewOutputStats stats = new ViewOutputStats(10, TimeUnit.MINUTES);
        stats.count("PrimaryView", "PDF", "OK");

        Logger logger1 = mock(Logger.class);
        stats.logStats(logger1);
        verify(logger1, times(1)).info(any(Marker.class), eq(""));

        // second flush with no new counts should emit nothing (reset worked)
        Logger logger2 = mock(Logger.class);
        stats.logStats(logger2);
        verifyNoInteractions(logger2);
    }

    @Test
    void testConcurrentCounting() throws InterruptedException {
        ViewOutputStats stats = new ViewOutputStats(10, TimeUnit.MINUTES);
        int threads = 8;
        int perThread = 1000;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < perThread; j++) {
                        stats.count("PrimaryView", "PDF", "OK");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));

        // exact aggregate, proving no updates were lost under contention
        Map<String, Long> snapshot = stats.snapshotAndReset();
        assertEquals(1, snapshot.size());
        assertEquals((long) threads * perThread, snapshot.get("PrimaryView\tPDF\tOK"));
    }

    @Test
    void testRecordIsNoOpWhenNotBound() {
        // not bound in the Namespace -> must not throw
        assertFalse(Namespace.exists(ViewOutputStats.DEFAULT_NAMESPACE_NAME));
        ViewOutputStats.record("PrimaryView", "PDF", "OK");
    }

    @Test
    void testStartBindAndShutdown() {
        ViewOutputStats stats = new ViewOutputStats(10, TimeUnit.MINUTES);
        stats.startAndBind();
        assertTrue(Namespace.exists(ViewOutputStats.DEFAULT_NAMESPACE_NAME));

        // record via the static path now that it is bound
        ViewOutputStats.record("PrimaryView", "PDF", "OK");
        Logger logger = mock(Logger.class);
        stats.logStats(logger);
        verify(logger, times(1)).info(any(Marker.class), eq(""));

        stats.shutdown();
        assertFalse(Namespace.exists(ViewOutputStats.DEFAULT_NAMESPACE_NAME));
    }
}
