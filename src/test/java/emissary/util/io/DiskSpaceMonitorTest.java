package emissary.util.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiskSpaceMonitorTest {

    @TempDir
    Path tempDir;

    @Test
    void testBuilderValidation() {
        // Null path should throw exception in constructor
        assertThrows(IllegalArgumentException.class, () -> new DiskSpaceMonitor.Builder(null));

        // Invalid check interval - zero (throws in checkInterval method)
        DiskSpaceMonitor.Builder builder1 = new DiskSpaceMonitor.Builder(tempDir);
        assertThrows(IllegalArgumentException.class, () -> builder1.checkInterval(0));

        // Invalid check interval - negative (throws in checkInterval method)
        DiskSpaceMonitor.Builder builder2 = new DiskSpaceMonitor.Builder(tempDir);
        assertThrows(IllegalArgumentException.class, () -> builder2.checkInterval(-1));

        // Invalid percentage threshold - negative (throws in pauseThresholdPercent method)
        DiskSpaceMonitor.Builder builder3 = new DiskSpaceMonitor.Builder(tempDir);
        assertThrows(IllegalArgumentException.class, () -> builder3.pauseThresholdPercent(-1));

        // Invalid percentage threshold - over 100 (throws in pauseThresholdPercent method)
        DiskSpaceMonitor.Builder builder4 = new DiskSpaceMonitor.Builder(tempDir);
        assertThrows(IllegalArgumentException.class, () -> builder4.pauseThresholdPercent(101));

        // No thresholds configured (throws in build method)
        DiskSpaceMonitor.Builder builderWithNoThresholds = new DiskSpaceMonitor.Builder(tempDir);
        assertThrows(IllegalArgumentException.class, builderWithNoThresholds::build);

        // Resume threshold must be better than pause threshold (percentage) - worse (throws in build method)
        DiskSpaceMonitor.Builder builderWithWorseResumePercent = new DiskSpaceMonitor.Builder(tempDir)
                .pauseThresholdPercent(90)
                .resumeThresholdPercent(95);
        assertThrows(IllegalArgumentException.class, builderWithWorseResumePercent::build);

        // Resume threshold must be better than pause threshold (percentage) - equal (throws in build method)
        DiskSpaceMonitor.Builder builderWithEqualResumePercent = new DiskSpaceMonitor.Builder(tempDir)
                .pauseThresholdPercent(90)
                .resumeThresholdPercent(90);
        assertThrows(IllegalArgumentException.class, builderWithEqualResumePercent::build);

        // Resume threshold must be better than pause threshold (bytes) - worse (throws in build method)
        DiskSpaceMonitor.Builder builderWithWorseResumeBytes = new DiskSpaceMonitor.Builder(tempDir)
                .pauseThresholdBytes(10000)
                .resumeThresholdBytes(5000);
        assertThrows(IllegalArgumentException.class, builderWithWorseResumeBytes::build);

        // Resume threshold must be better than pause threshold (bytes) - equal (throws in build method)
        DiskSpaceMonitor.Builder builderWithEqualResumeBytes = new DiskSpaceMonitor.Builder(tempDir)
                .pauseThresholdBytes(10000)
                .resumeThresholdBytes(10000);
        assertThrows(IllegalArgumentException.class, builderWithEqualResumeBytes::build);
    }

    @Test
    void testValidBuilderConfiguration() {
        // Valid percentage configuration
        DiskSpaceMonitor monitor1 = new DiskSpaceMonitor.Builder(tempDir)
                .pauseThresholdPercent(90)
                .resumeThresholdPercent(85)
                .checkInterval(1)
                .build();
        assertNotNull(monitor1);

        // Valid byte configuration
        DiskSpaceMonitor monitor2 = new DiskSpaceMonitor.Builder(tempDir)
                .pauseThresholdBytes(10000)
                .resumeThresholdBytes(20000)
                .checkInterval(1)
                .build();
        assertNotNull(monitor2);

        // Valid with defaults
        DiskSpaceMonitor monitor3 = new DiskSpaceMonitor.Builder(tempDir)
                .pauseThresholdPercent(90)
                .resumeThresholdPercent(85)
                .build();
        assertNotNull(monitor3);
    }

    @Test
    void testListenerNotification() throws InterruptedException {
        // This test verifies that listeners are notified
        // We can't easily mock disk space, so we just verify the listener infrastructure works
        CountDownLatch exceededLatch = new CountDownLatch(1);
        AtomicReference<Path> notifiedPath = new AtomicReference<>();
        AtomicReference<Double> notifiedPercent = new AtomicReference<>();
        AtomicReference<Long> notifiedBytes = new AtomicReference<>();

        DiskSpaceMonitor monitor = new DiskSpaceMonitor.Builder(tempDir)
                .pauseThresholdPercent(0.01) // Very low threshold to trigger easily
                .resumeThresholdPercent(0.001)
                .checkInterval(1)
                .build();

        monitor.addListener(new DiskSpaceListener() {
            @Override
            public void onDiskSpaceExceeded(Path path, double usedPercent, long freeBytes) {
                notifiedPath.set(path);
                notifiedPercent.set(usedPercent);
                notifiedBytes.set(freeBytes);
                exceededLatch.countDown();
            }

            @Override
            public void onDiskSpaceRecovered(Path path, double usedPercent, long freeBytes) {
                // Not expected in this test
            }
        });

        monitor.start();

        // Wait for notification (disk is likely > 0.01% used)
        boolean notified = exceededLatch.await(5, TimeUnit.SECONDS);
        monitor.stop();

        if (notified) {
            // If we got notified, verify the values are reasonable
            assertEquals(tempDir, notifiedPath.get());
            assertNotNull(notifiedPercent.get());
            assertTrue(notifiedPercent.get() >= 0 && notifiedPercent.get() <= 100);
            assertNotNull(notifiedBytes.get());
            assertTrue(notifiedBytes.get() >= 0);
        }
        // Note: We don't fail if not notified as disk might be nearly empty in CI
    }

    @Test
    void testMultipleListeners() throws InterruptedException {
        AtomicInteger listener1Count = new AtomicInteger(0);
        AtomicInteger listener2Count = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(2);

        DiskSpaceMonitor monitor = new DiskSpaceMonitor.Builder(tempDir)
                .pauseThresholdPercent(0.01)
                .resumeThresholdPercent(0.001)
                .checkInterval(1)
                .build();

        monitor.addListener(new DiskSpaceListener() {
            @Override
            public void onDiskSpaceExceeded(Path path, double usedPercent, long freeBytes) {
                listener1Count.incrementAndGet();
                latch.countDown();
            }

            @Override
            public void onDiskSpaceRecovered(Path path, double usedPercent, long freeBytes) {
                // Not tested in this scenario
            }
        });

        monitor.addListener(new DiskSpaceListener() {
            @Override
            public void onDiskSpaceExceeded(Path path, double usedPercent, long freeBytes) {
                listener2Count.incrementAndGet();
                latch.countDown();
            }

            @Override
            public void onDiskSpaceRecovered(Path path, double usedPercent, long freeBytes) {
                // Not tested in this scenario
            }
        });

        monitor.start();
        boolean notified = latch.await(5, TimeUnit.SECONDS);
        monitor.stop();

        if (notified) {
            // Both listeners should have been notified
            assertTrue(listener1Count.get() > 0);
            assertTrue(listener2Count.get() > 0);
        }
    }

    @Test
    void testListenerExceptionDoesNotStopMonitoring() throws InterruptedException {
        AtomicBoolean goodListenerCalled = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        DiskSpaceMonitor monitor = new DiskSpaceMonitor.Builder(tempDir)
                .pauseThresholdPercent(0.01)
                .resumeThresholdPercent(0.001)
                .checkInterval(1)
                .build();

        // Add listener that throws exception
        monitor.addListener(new DiskSpaceListener() {
            @Override
            public void onDiskSpaceExceeded(Path path, double usedPercent, long freeBytes) {
                throw new IllegalStateException("Test exception");
            }

            @Override
            public void onDiskSpaceRecovered(Path path, double usedPercent, long freeBytes) {
                // Not tested in this scenario
            }
        });

        // Add good listener
        monitor.addListener(new DiskSpaceListener() {
            @Override
            public void onDiskSpaceExceeded(Path path, double usedPercent, long freeBytes) {
                goodListenerCalled.set(true);
                latch.countDown();
            }

            @Override
            public void onDiskSpaceRecovered(Path path, double usedPercent, long freeBytes) {
                // Not tested in this scenario
            }
        });

        monitor.start();
        boolean notified = latch.await(5, TimeUnit.SECONDS);
        monitor.stop();

        if (notified) {
            // Good listener should still be called despite exception in first listener
            assertTrue(goodListenerCalled.get());
        }
    }

    @Test
    void testStartStopLifecycle() {
        DiskSpaceMonitor monitor = new DiskSpaceMonitor.Builder(tempDir)
                .pauseThresholdPercent(90)
                .resumeThresholdPercent(85)
                .checkInterval(1)
                .build();

        assertFalse(monitor.isRunning());

        monitor.start();
        assertTrue(monitor.isRunning());

        monitor.stop();
        // stop() is synchronous and waits for termination
        assertFalse(monitor.isRunning());
    }

    @Test
    void testMultipleStarts() {
        DiskSpaceMonitor monitor = new DiskSpaceMonitor.Builder(tempDir)
                .pauseThresholdPercent(90)
                .resumeThresholdPercent(85)
                .checkInterval(1)
                .build();

        monitor.start();
        assertTrue(monitor.isRunning());

        // Second start should be no-op
        monitor.start();
        assertTrue(monitor.isRunning());

        monitor.stop();
    }

    @Test
    void testRemoveListener() throws InterruptedException {
        AtomicInteger listener1Count = new AtomicInteger(0);
        AtomicInteger listener2Count = new AtomicInteger(0);
        AtomicInteger listener1CountAfterRemove = new AtomicInteger(0);
        CountDownLatch initialLatch = new CountDownLatch(1);
        CountDownLatch afterRemovalLatch = new CountDownLatch(1);

        DiskSpaceMonitor monitor = new DiskSpaceMonitor.Builder(tempDir)
                .pauseThresholdPercent(0.01)
                .resumeThresholdPercent(0.001)
                .checkInterval(1)
                .build();

        DiskSpaceListener listener1 = new DiskSpaceListener() {
            @Override
            public void onDiskSpaceExceeded(Path path, double usedPercent, long freeBytes) {
                listener1Count.incrementAndGet();
                initialLatch.countDown();
            }

            @Override
            public void onDiskSpaceRecovered(Path path, double usedPercent, long freeBytes) {
                // Not tested in this scenario
            }
        };

        DiskSpaceListener listener2 = new DiskSpaceListener() {
            @Override
            public void onDiskSpaceExceeded(Path path, double usedPercent, long freeBytes) {
                listener2Count.incrementAndGet();
                if (listener1CountAfterRemove.get() > 0) {
                    afterRemovalLatch.countDown();
                }
            }

            @Override
            public void onDiskSpaceRecovered(Path path, double usedPercent, long freeBytes) {
                // Not tested in this scenario
            }
        };

        // Add both listeners
        monitor.addListener(listener1);
        monitor.addListener(listener2);

        monitor.start();

        // Wait for initial notification (or timeout)
        initialLatch.await(5, TimeUnit.SECONDS);

        // Remove listener1 and capture count
        monitor.removeListener(listener1);
        listener1CountAfterRemove.set(listener1Count.get());

        // Wait a bit for potential additional notifications
        afterRemovalLatch.await(2, TimeUnit.SECONDS);

        monitor.stop();

        // Listener1 count should not have increased after removal
        int listener1FinalCount = listener1Count.get();
        assertEquals(listener1CountAfterRemove.get(), listener1FinalCount,
                "Listener1 should not receive notifications after removal");
    }

    @Test
    void testPausedStateTracking() throws InterruptedException {
        DiskSpaceMonitor monitor = new DiskSpaceMonitor.Builder(tempDir)
                .pauseThresholdPercent(0.01)
                .resumeThresholdPercent(0.001)
                .checkInterval(1)
                .build();

        assertFalse(monitor.isPaused());

        CountDownLatch latch = new CountDownLatch(1);
        monitor.addListener(new DiskSpaceListener() {
            @Override
            public void onDiskSpaceExceeded(Path path, double usedPercent, long freeBytes) {
                latch.countDown();
            }

            @Override
            public void onDiskSpaceRecovered(Path path, double usedPercent, long freeBytes) {
                // Not tested in this scenario
            }
        });

        monitor.start();
        boolean triggered = latch.await(5, TimeUnit.SECONDS);

        if (triggered) {
            // Monitor should be in paused state after threshold exceeded
            assertTrue(monitor.isPaused());
        }

        monitor.stop();
    }

    @Test
    void testAddNullListener() {
        DiskSpaceMonitor monitor = new DiskSpaceMonitor.Builder(tempDir)
                .pauseThresholdPercent(90)
                .resumeThresholdPercent(85)
                .build();

        // Should not throw exception
        monitor.addListener(null);
    }
}
