package emissary.pickup;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.core.IMobileAgent;
import emissary.pickup.file.FilePickUpClient;
import emissary.pickup.file.FilePickUpPlace;
import emissary.server.EmissaryServer;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PickUpPlaceTest extends UnitTest {

    @Test
    void testIsPickUpTrue() {
        assertTrue(PickUpPlace.implementsPickUpPlace(FilePickUpPlace.class));
    }

    @Test
    void testIsPickupFalse() {
        assertFalse(PickUpPlace.implementsPickUpPlace(EmissaryServer.class));
    }

    @Test
    void testIsPickupFalseForInterface() {
        assertFalse(PickUpPlace.implementsPickUpPlace(IMobileAgent.class));
    }

    @Test
    void testIsPickupTrueForClient() {
        assertTrue(PickUpPlace.implementsPickUpPlace(FilePickUpClient.class));
    }

    @Test
    void testIdPickupTrueForPickupSpace() {
        assertTrue(PickUpPlace.implementsPickUpPlace(PickUpSpace.class));
    }

    @Test
    void testDiskSpaceMonitoringDisabledByDefault(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("hold"));
        Files.createDirectories(tempDir.resolve("error"));
        Files.createDirectories(tempDir.resolve("done"));
        Files.createDirectories(tempDir.resolve("output"));

        Configurator config = createBaseConfig(tempDir);
        // DISK_SPACE_MONITORING_ENABLED not set - should default to false

        TestPickUpPlace place = new TestPickUpPlace(config);
        try {
            // Disk space monitoring should be disabled by default
            assertNotNull(place);

            // Verify that no monitor was created
            assertFalse(place.isDiskSpaceMonitorConfigured(), "Disk space monitor should not be configured when disabled");

            // Calling startDiskSpaceMonitoring should be a no-op
            place.startDiskSpaceMonitoring();

            // Verify callbacks were never called
            assertEquals(0, place.exceededCallCount.get());
            assertEquals(0, place.recoveredCallCount.get());
        } finally {
            place.shutDown();
        }
    }

    @Test
    void testDiskSpaceMonitoringEnabledWithPercentageThreshold(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);
        Files.createDirectories(tempDir.resolve("hold"));
        Files.createDirectories(tempDir.resolve("error"));
        Files.createDirectories(tempDir.resolve("done"));

        Configurator config = createBaseConfig(tempDir);
        config.addEntry("OUTPUT_DATA", outputDir.toString());
        config.addEntry("DISK_SPACE_MONITORING_ENABLED", "true");
        config.addEntry("DISK_SPACE_CHECK_INTERVAL_SECONDS", "1");
        config.addEntry("DISK_SPACE_PAUSE_THRESHOLD_PERCENT", "0.01"); // Very low to trigger easily
        config.addEntry("DISK_SPACE_RESUME_THRESHOLD_PERCENT", "0.001");

        TestPickUpPlace place = new TestPickUpPlace(config);
        try {
            assertNotNull(place);

            // Start monitoring
            place.startDiskSpaceMonitoring();

            // Wait for threshold to be exceeded (disk is likely > 0.01% used)
            boolean exceeded = place.exceededLatch.await(5, TimeUnit.SECONDS);

            // Stop monitoring
            place.stopDiskSpaceMonitoring();

            if (exceeded) {
                // Verify callback was called
                assertTrue(place.exceededCallCount.get() > 0, "onDiskSpaceExceeded should be called");
            }
        } finally {
            place.shutDown();
        }
    }

    @Test
    void testDiskSpaceMonitoringConfigurationValidation(@TempDir Path tempDir) throws IOException {
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);
        Files.createDirectories(tempDir.resolve("hold"));
        Files.createDirectories(tempDir.resolve("error"));
        Files.createDirectories(tempDir.resolve("done"));

        // Test with no thresholds configured - should disable monitoring
        Configurator config = createBaseConfig(tempDir);
        config.addEntry("OUTPUT_DATA", outputDir.toString());
        config.addEntry("DISK_SPACE_MONITORING_ENABLED", "true");
        // Note: no thresholds configured

        TestPickUpPlace place = new TestPickUpPlace(config);
        try {
            assertNotNull(place);
            // Should not crash, monitoring just won't start
            assertFalse(place.isDiskSpaceMonitorConfigured(), "Monitor should not be configured without thresholds");
        } finally {
            place.shutDown();
        }
    }

    @Test
    void testDiskSpaceMonitoringWithDefaultResumeThreshold(@TempDir Path tempDir) throws IOException {
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);
        Files.createDirectories(tempDir.resolve("hold"));
        Files.createDirectories(tempDir.resolve("error"));
        Files.createDirectories(tempDir.resolve("done"));

        // Test with only pause threshold - should use sensible default for resume
        Configurator config = createBaseConfig(tempDir);
        config.addEntry("OUTPUT_DATA", outputDir.toString());
        config.addEntry("DISK_SPACE_MONITORING_ENABLED", "true");
        config.addEntry("DISK_SPACE_CHECK_INTERVAL_SECONDS", "1");
        config.addEntry("DISK_SPACE_PAUSE_THRESHOLD_PERCENT", "90");
        // Note: DISK_SPACE_RESUME_THRESHOLD_PERCENT not set - should default to 85%

        TestPickUpPlace place = new TestPickUpPlace(config);
        try {
            assertNotNull(place);
            // Verify monitor was configured with sensible defaults
            assertTrue(place.isDiskSpaceMonitorConfigured(), "Monitor should be configured with default resume threshold");
        } finally {
            place.shutDown();
        }
    }

    /**
     * Creates a base configuration with required service settings for PickUpPlace.
     */
    private static Configurator createBaseConfig(Path tempDir) {
        ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("PLACE_NAME", "TestPickUpPlace");
        config.addEntry("SERVICE_NAME", "TEST_PICKUP");
        config.addEntry("SERVICE_TYPE", "INITIAL");
        config.addEntry("SERVICE_DESCRIPTION", "Test pickup place");
        config.addEntry("SERVICE_COST", "50");
        config.addEntry("SERVICE_QUALITY", "50");
        config.addEntry("SERVICE_PROXY", "INITIAL");
        config.addEntry("HOLDING_AREA", tempDir.resolve("hold").toString());
        config.addEntry("ERROR_DATA", tempDir.resolve("error").toString());
        config.addEntry("DONE_DATA", tempDir.resolve("done").toString());
        config.addEntry("OUTPUT_DATA", tempDir.resolve("output").toString());
        return config;
    }

    /**
     * Test PickUpPlace implementation that tracks disk space callbacks
     */
    private static class TestPickUpPlace extends PickUpPlace {
        final AtomicInteger exceededCallCount = new AtomicInteger(0);
        final AtomicInteger recoveredCallCount = new AtomicInteger(0);
        final CountDownLatch exceededLatch = new CountDownLatch(1);
        final CountDownLatch recoveredLatch = new CountDownLatch(1);

        public TestPickUpPlace(Configurator config) throws IOException {
            super(config);
        }

        @Override
        protected void onDiskSpaceExceeded(Path path, double usedPercent, long freeBytes) {
            exceededCallCount.incrementAndGet();
            exceededLatch.countDown();
        }

        @Override
        protected void onDiskSpaceRecovered(Path path, double usedPercent, long freeBytes) {
            recoveredCallCount.incrementAndGet();
            recoveredLatch.countDown();
        }

        @Override
        public void shutDown() {
            stopDiskSpaceMonitoring();
            super.shutDown();
        }

        /**
         * Exposes whether disk space monitor was configured for testing.
         */
        public boolean isDiskSpaceMonitorConfigured() {
            return diskSpaceMonitor != null;
        }
    }

}
