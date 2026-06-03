package emissary.grpc.channel;

import emissary.config.ServiceConfigGuide;
import emissary.test.core.junit5.UnitTest;

import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChannelManagerTest extends UnitTest {
    @ParameterizedTest
    @ValueSource(strings = {"localhost", "dns:///foo.bar"})
    void testGoodHostName(String host) {
        Runnable invocation = () -> new TestChannelManager(host, 1).close();
        assertDoesNotThrow(invocation::run);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dns:foo.bar", "dns:/foo.bar", "dns://foo.bar", "http:///foo.bar", "https:///foo.bar", "foo.bar"})
    void testBadHostName(String host) {
        Runnable invocation = () -> new TestChannelManager(host, 1).close();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, invocation::run);
        assertEquals(String.format("Expected DNS URI prefix \"dns:///\" but got \"%s\"", host), e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 8000, 8001, 8080, 65535})
    void testGoodPortNumber(int port) {
        Runnable invocation = () -> new TestChannelManager("dns:///foo.bar", port).close();
        assertDoesNotThrow(invocation::run);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 65536})
    void testBadPortNumber(int port) {
        Runnable invocation = () -> new TestChannelManager("dns:///foo.bar", port).close();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, invocation::run);
        assertEquals(String.format("Port \"%d\" is outside valid range [1, 65535]", port), e.getMessage());
    }


    private static class TestChannelManager extends ChannelManager {
        public TestChannelManager(String host, int port) {
            super(host, port, new ServiceConfigGuide(), InsecureChannelCredentials.create());
        }

        @Override
        public ManagedChannel acquire() {
            return null;
        }

        @Override
        public void release(ManagedChannel channel) {
            /* No-op */
        }

        @Override
        public void shutdown(ManagedChannel channel) {
            /* No-op */
        }

        @Override
        public void close() {
            /* No-op */
        }
    }
}
