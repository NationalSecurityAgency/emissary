package emissary.grpc.channel.impl;

import emissary.config.ConfigEntry;
import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.grpc.channel.impl.PooledChannelManager.PoolException;
import emissary.grpc.channel.impl.PooledChannelManager.PoolRetrievalOrdering;
import emissary.test.core.junit5.UnitTest;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.grpc.ManagedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PooledChannelManagerTest extends UnitTest {
    private Logger logbackLogger;
    private ListAppender<ILoggingEvent> listAppender;

    private static PooledChannelManager newChannelManager(Configurator configT) {
        return new PooledChannelManager("localhost", 1234, configT);
    }

    private static Configurator buildConfigs(ConfigEntry... configEntries) {
        Configurator configT = new ServiceConfigGuide();
        Arrays.stream(configEntries).forEach(c -> configT.addEntry(c.getKey(), c.getValue()));
        configT.addEntry(PooledChannelManager.MIN_IDLE_CONNECTIONS, "1");
        configT.addEntry(PooledChannelManager.MAX_IDLE_CONNECTIONS, "2");
        configT.addEntry(PooledChannelManager.MAX_SIZE, "2");
        return configT;
    }

    @BeforeEach
    void setUpLogCapture() {
        logbackLogger = (Logger) LoggerFactory.getLogger(PooledChannelManager.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logbackLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDownLogCapture() {
        logbackLogger.detachAppender(listAppender);
        listAppender.stop();
    }

    @Test
    void testBadPoolRetrievalOrderConfig() {
        Runnable invocation = () -> newChannelManager(buildConfigs(
                new ConfigEntry(PooledChannelManager.RETRIEVAL_ORDER, "ZIFO"))).close();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, invocation::run);
        assertEquals("No enum constant " +
                "emissary.grpc.channel.impl.PooledChannelManager.PoolRetrievalOrdering.ZIFO", e.getMessage());
    }

    @Test
    void testLifoPoolRetrievalOrderConfig() {
        try (PooledChannelManager manager = newChannelManager(buildConfigs(
                new ConfigEntry(PooledChannelManager.RETRIEVAL_ORDER, PoolRetrievalOrdering.LIFO.name())))) {

            ManagedChannel inChannelFirst = manager.acquire();
            ManagedChannel inChannelLast = manager.acquire();

            assertNotSame(inChannelFirst, inChannelLast);

            manager.release(inChannelFirst);
            manager.release(inChannelLast);

            ManagedChannel outChannel = manager.acquire();

            assertNotSame(inChannelFirst, outChannel);
            assertSame(inChannelLast, outChannel);
        }
    }

    @Test
    void testFifoPoolRetrievalOrderConfig() {
        try (PooledChannelManager manager = newChannelManager(buildConfigs(
                new ConfigEntry(PooledChannelManager.RETRIEVAL_ORDER, PoolRetrievalOrdering.FIFO.name())))) {

            ManagedChannel inChannelFirst = manager.acquire();
            ManagedChannel inChannelLast = manager.acquire();

            assertNotSame(inChannelFirst, inChannelLast);

            manager.release(inChannelFirst);
            manager.release(inChannelLast);

            ManagedChannel outChannel = manager.acquire();

            assertSame(inChannelFirst, outChannel);
            assertNotSame(inChannelLast, outChannel);
        }
    }

    @Test
    void testReleaseUnavailableChannel() {
        try (PooledChannelManager manager = newChannelManager(buildConfigs())) {
            ManagedChannel channel = manager.acquire();

            assertFalse(channel.isShutdown());

            manager.release(channel);

            assertFalse(channel.isShutdown());

            manager.release(channel);

            assertTrue(channel.isShutdown());

            ILoggingEvent log = listAppender.list.get(0);
            assertEquals(Level.ERROR, log.getLevel());
            assertEquals("Unable to cleanly return gRPC connection channel to the pool: " +
                    "Object has already been returned to this pool or is invalid", log.getFormattedMessage());
        }
    }

    @Test
    void testShutdownUnavailableChannel() {
        try (PooledChannelManager manager = newChannelManager(buildConfigs())) {
            ManagedChannel channel = manager.acquire();

            assertFalse(channel.isShutdown());

            manager.shutdown(channel);

            assertTrue(channel.isShutdown());

            manager.shutdown(channel);

            ILoggingEvent log = listAppender.list.get(0);
            assertEquals(Level.ERROR, log.getLevel());
            assertEquals("Unable to invalidate existing gRPC connection: " +
                    "Invalidated object not currently part of this pool", log.getFormattedMessage());
        }
    }

    @SuppressWarnings("java:S2925") // sleep warning
    @Test
    void testPoolErosion() throws InterruptedException {
        try (PooledChannelManager manager = newChannelManager(buildConfigs(
                new ConfigEntry(PooledChannelManager.ERODING_FACTOR, "0.0001")))) {
            ManagedChannel alive = manager.acquire();
            ManagedChannel dead = manager.acquire();

            manager.release(alive);
            Thread.sleep(Duration.ofMillis(250).toMillis());
            manager.release(dead);

            assertFalse(alive.isShutdown());
            assertTrue(dead.isShutdown());
        }
    }

    @Test
    void testPoolClose() {
        PooledChannelManager manager = newChannelManager(buildConfigs());
        manager.close();

        PoolException e = assertThrows(PoolException.class, manager::acquire);
        assertEquals("Unable to borrow channel from pool: Pool not open", e.getMessage());
    }
}
