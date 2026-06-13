package emissary.grpc.channel;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.test.core.junit5.UnitTest;

import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingletonChannelManagerTest extends UnitTest {
    private static SingletonChannelManager newChannelManager(Configurator configT) {
        return new SingletonChannelManager("localhost", 1234, configT, InsecureChannelCredentials.create());
    }

    @Test
    void testChannelShutdown() {
        try (SingletonChannelManager manager = newChannelManager(new ServiceConfigGuide())) {
            ManagedChannel channel = manager.acquire();

            assertFalse(channel.isShutdown());

            manager.shutdown(channel);

            assertTrue(channel.isShutdown());
        }
    }

    @Test
    void testChannelReleaseNoOp() {
        try (SingletonChannelManager manager = newChannelManager(new ServiceConfigGuide())) {
            ManagedChannel channel = manager.acquire();

            assertFalse(channel.isShutdown());

            manager.release(channel);

            assertFalse(channel.isShutdown());
        }
    }

    @Test
    void testCloseManager() {
        SingletonChannelManager manager = newChannelManager(new ServiceConfigGuide());
        ManagedChannel channel = manager.acquire();

        assertFalse(channel.isShutdown());

        manager.close();

        assertTrue(channel.isShutdown());
    }
}
