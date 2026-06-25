package emissary.grpc.channel.impl;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.test.core.junit5.UnitTest;

import io.grpc.ManagedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandaloneChannelManagerTest extends UnitTest {
    private static StandaloneChannelManager newChannelManager(Configurator configT) {
        return new StandaloneChannelManager("localhost", 1234, configT);
    }

    @Test
    void testChannelShutdown() {
        try (StandaloneChannelManager manager = newChannelManager(new ServiceConfigGuide())) {
            ManagedChannel channel = manager.acquire();

            assertFalse(channel.isShutdown());

            manager.shutdown(channel);

            assertTrue(channel.isShutdown());
        }
    }

    @Test
    void testChannelReleaseNoOp() {
        try (StandaloneChannelManager manager = newChannelManager(new ServiceConfigGuide())) {
            ManagedChannel channel = manager.acquire();

            assertFalse(channel.isShutdown());

            manager.release(channel);

            assertFalse(channel.isShutdown());
        }
    }

    @Test
    void testCloseManager() {
        StandaloneChannelManager manager = newChannelManager(new ServiceConfigGuide());
        ManagedChannel channel = manager.acquire();

        assertFalse(channel.isShutdown());

        manager.close();

        assertTrue(channel.isShutdown());
    }
}
