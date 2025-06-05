package emissary.grpc.pool;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.grpc.pool.ConnectionFactory;
import emissary.test.core.junit5.UnitTest;
import emissary.grpc.exceptions.GrpcPoolException;

import io.grpc.ManagedChannel;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PooledObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionFactoryTest extends UnitTest {
    private static final String GRPC_POOL_MIN_IDLE_CONNECTIONS = "GRPC_POOL_MIN_IDLE_CONNECTIONS";
    private static final String GRPC_POOL_MAX_IDLE_CONNECTIONS = "GRPC_POOL_MAX_IDLE_CONNECTIONS";
    private static final String GRPC_POOL_MAX_SIZE = "GRPC_POOL_MAX_SIZE";
    private static final String GRPC_POOL_RETRIEVAL_ORDER = "GRPC_POOL_RETRIEVAL_ORDER";
    private static final String GRPC_LOAD_BALANCING_POLICY = "GRPC_LOAD_BALANCING_POLICY";

    private static final String HOST = "localhost";
    private static final int PORT = 2222;

    private TestConnectionFactory factory;
    private ObjectPool<ManagedChannel> pool;

    static class TestConnectionFactory extends ConnectionFactory {
        private boolean valid = true;

        public TestConnectionFactory(String host, int port, Configurator configG) {
            super(host, port, configG);
        }

        public void invalidate() {
            valid = false;
        }

        @Override
        public boolean validateObject(PooledObject<ManagedChannel> pooledObject) {
            return valid;
        }
    }

    private static Configurator getDefaultConfigs() {
        Configurator configT = new ServiceConfigGuide();
        configT.addEntry(GRPC_POOL_MIN_IDLE_CONNECTIONS, "1");
        configT.addEntry(GRPC_POOL_MAX_IDLE_CONNECTIONS, "2");
        configT.addEntry(GRPC_POOL_MAX_SIZE, "2");
        return configT;
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        Configurator configT = getDefaultConfigs();
        factory = new TestConnectionFactory(HOST, PORT, configT);
        pool = factory.newConnectionPool();
    }

    @Test
    void testBadPoolRetrievalOrderConfigs() {
        Configurator configT = getDefaultConfigs();
        configT.addEntry(GRPC_POOL_RETRIEVAL_ORDER, "ZIFO");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new TestConnectionFactory(HOST, PORT, configT));
        assertEquals("No enum constant emissary.grpc.pool.PoolRetrievalOrdering.ZIFO", e.getMessage());
    }

    @Test
    void testBadLoadBalancingConfigs() {
        Configurator configT = getDefaultConfigs();
        configT.addEntry(GRPC_LOAD_BALANCING_POLICY, "bad_scheduler");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new TestConnectionFactory(HOST, PORT, configT));
        assertEquals("No enum constant emissary.grpc.pool.LoadBalancingPolicy.BAD_SCHEDULER", e.getMessage());
    }

    @Test
    void testAcquireChannelFails() {
        factory.invalidate();
        pool = factory.newConnectionPool();
        GrpcPoolException e = assertThrows(GrpcPoolException.class, () -> ConnectionFactory.acquireChannel(pool));
        assertEquals("Unable to borrow channel from pool: Unable to validate object", e.getMessage());
    }

    @Test
    void testAcquireChannel() throws Exception {
        ManagedChannel channel = ConnectionFactory.acquireChannel(pool);
        assertNotNull(channel);
        pool.returnObject(channel);
    }

    @Test
    void testInvalidateChannel() throws Exception {
        ManagedChannel c1 = ConnectionFactory.acquireChannel(pool);
        ConnectionFactory.invalidateChannel(c1, pool);
        ManagedChannel c2 = ConnectionFactory.acquireChannel(pool);
        assertNotSame(c1, c2);
        pool.returnObject(c2);
    }

    @Test
    void testReturnChannel() {
        ManagedChannel c1 = ConnectionFactory.acquireChannel(pool);
        ConnectionFactory.returnChannel(c1, pool);
        ManagedChannel c2 = ConnectionFactory.acquireChannel(pool);
        assertSame(c1, c2);
        ConnectionFactory.returnChannel(c2, pool);
    }

    @Test
    void testMaxPoolSizeBlocks() {
        ManagedChannel c1 = ConnectionFactory.acquireChannel(pool);
        ManagedChannel c2 = ConnectionFactory.acquireChannel(pool);
        GrpcPoolException exception = assertThrows(GrpcPoolException.class, () -> ConnectionFactory.acquireChannel(pool));
        assertTrue(StringUtils.startsWith(exception.getMessage(), "Unable to borrow channel from pool: Timeout waiting for idle object"));
        ConnectionFactory.returnChannel(c1, pool);
        ConnectionFactory.returnChannel(c2, pool);
    }

    @Test
    void testCreate() {
        ManagedChannel channel = factory.create();
        assertNotNull(channel);
        channel.shutdownNow();
    }

    @Test
    void testWrap() {
        ManagedChannel channel = factory.create();
        PooledObject<ManagedChannel> wrapped = factory.wrap(channel);
        assertNotNull(wrapped);
        assertEquals(channel, wrapped.getObject());
        wrapped.getObject().shutdownNow();
    }

    @Test
    void testDestroy() {
        PooledObject<ManagedChannel> wrapped = factory.wrap(factory.create());
        ManagedChannel channel = wrapped.getObject();
        assertNotNull(wrapped);
        assertFalse(channel.isShutdown());
        assertFalse(channel.isTerminated());
        assertDoesNotThrow(() -> factory.destroyObject(wrapped));
        assertTrue(channel.isShutdown());
    }
}
