package emissary.grpc.pool;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.grpc.exceptions.PoolException;
import emissary.test.core.junit5.UnitTest;

import io.grpc.ManagedChannel;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PooledObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class ConnectionFactoryTest extends UnitTest {
    private static ConnectionFactory buildConnectionFactory(Configurator configG) {
        return new ConnectionFactory("localhost", 2222, configG, channel -> true, channel -> {
        });
    }

    private static Configurator getDefaultConfigs() {
        Configurator configT = new ServiceConfigGuide();
        configT.addEntry(ConnectionFactory.GRPC_POOL_MIN_IDLE_CONNECTIONS, "1");
        configT.addEntry(ConnectionFactory.GRPC_POOL_MAX_IDLE_CONNECTIONS, "2");
        configT.addEntry(ConnectionFactory.GRPC_POOL_MAX_SIZE, "2");
        return configT;
    }

    @Test
    void testBadPoolRetrievalOrderConfig() {
        Configurator configT = getDefaultConfigs();
        configT.addEntry(ConnectionFactory.GRPC_POOL_RETRIEVAL_ORDER, "ZIFO");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> buildConnectionFactory(configT));
        assertEquals("No enum constant emissary.grpc.pool.PoolRetrievalOrdering.ZIFO", e.getMessage());
    }

    @Test
    void testLifoPoolRetrievalOrderConfig() {
        Configurator configT = getDefaultConfigs();
        configT.addEntry(ConnectionFactory.GRPC_POOL_RETRIEVAL_ORDER, PoolRetrievalOrdering.LIFO.name());
        ConnectionFactory factory = buildConnectionFactory(configT);
        assertTrue(factory.getPoolIsLifo());
        assertFalse(factory.getPoolIsFifo());
    }

    @Test
    void testFifoPoolRetrievalOrderConfig() {
        Configurator configT = getDefaultConfigs();
        configT.addEntry(ConnectionFactory.GRPC_POOL_RETRIEVAL_ORDER, PoolRetrievalOrdering.FIFO.name());
        ConnectionFactory factory = buildConnectionFactory(configT);
        assertFalse(factory.getPoolIsLifo());
        assertTrue(factory.getPoolIsFifo());
    }

    @Test
    void testBadLoadBalancingConfig() {
        Configurator configT = getDefaultConfigs();
        configT.addEntry(ConnectionFactory.GRPC_LOAD_BALANCING_POLICY, "bad_scheduler");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> buildConnectionFactory(configT));
        assertEquals("No enum constant emissary.grpc.pool.LoadBalancingPolicy.BAD_SCHEDULER", e.getMessage());
    }

    @Test
    void testRoundRobinLoadBalancingConfig() {
        Configurator configT = getDefaultConfigs();
        configT.addEntry(ConnectionFactory.GRPC_LOAD_BALANCING_POLICY, LoadBalancingPolicy.ROUND_ROBIN.name());
        ConnectionFactory factory = buildConnectionFactory(configT);
        assertEquals("round_robin", factory.getLoadBalancingPolicy());
    }

    @Test
    void testPickFirstLoadBalancingConfig() {
        Configurator configT = getDefaultConfigs();
        configT.addEntry(ConnectionFactory.GRPC_LOAD_BALANCING_POLICY, LoadBalancingPolicy.PICK_FIRST.name());
        ConnectionFactory factory = buildConnectionFactory(configT);
        assertEquals("pick_first", factory.getLoadBalancingPolicy());
    }

    @Nested
    class PooledChannelTests {
        private ConnectionFactory factory;
        private ObjectPool<ManagedChannel> pool;

        @BeforeEach
        void setUp() {
            Configurator configT = getDefaultConfigs();
            factory = spy(buildConnectionFactory(configT));
            pool = factory.newConnectionPool();
        }

        @Test
        void testAcquireChannelFails() {
            doReturn(false).when(factory).validateObject(any());
            PoolException e = assertThrows(PoolException.class, () -> ConnectionFactory.acquireChannel(pool));
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
        void testPassivateChannel() {
            ManagedChannel c1 = ConnectionFactory.acquireChannel(pool);
            ConnectionFactory.returnChannel(c1, pool);
            assertFalse(c1.isShutdown());

            doAnswer(invocation -> {
                PooledObject<ManagedChannel> pooledObject = invocation.getArgument(0);
                pooledObject.getObject().shutdownNow();
                return null;
            }).when(factory).passivateObject(any());

            ManagedChannel c2 = ConnectionFactory.acquireChannel(pool);
            ConnectionFactory.returnChannel(c2, pool);
            assertTrue(c2.isShutdown());
        }

        @Test
        void testMaxPoolSizeBlocks() {
            ManagedChannel c1 = ConnectionFactory.acquireChannel(pool);
            ManagedChannel c2 = ConnectionFactory.acquireChannel(pool);
            PoolException exception = assertThrows(PoolException.class, () -> ConnectionFactory.acquireChannel(pool));
            assertTrue(StringUtils.startsWith(exception.getMessage(),
                    "Unable to borrow channel from pool: Timeout waiting for idle object"));
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
}
