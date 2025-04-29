package emissary.util.grpc.pool;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.test.core.junit5.UnitTest;

import io.grpc.ManagedChannel;
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
    private static final String MIN_IDLE_CONNS = "MIN_IDLE_CONNS";
    private static final String MAX_IDLE_CONNS = "MAX_IDLE_CONNS";
    private static final String MAX_POOL_SIZE = "MAX_POOL_SIZE";
    private static final String HOST = "localhost";
    private static final int PORT = 2222;

    private ConnectionFactory factory;
    private ObjectPool<ManagedChannel> pool;

    public static class TestConnectionFactory extends ConnectionFactory {
        public TestConnectionFactory(String host, int port, Configurator configG) {
            super(host, port, configG);
        }

        @Override
        public boolean validateObject(PooledObject<ManagedChannel> pooledObject) {
            return true;
        }
    }

    @BeforeEach
    public void init() {
        Configurator configT = new ServiceConfigGuide();
        configT.addEntry(MIN_IDLE_CONNS, "1");
        configT.addEntry(MAX_IDLE_CONNS, "2");
        configT.addEntry(MAX_POOL_SIZE, "2");

        factory = new TestConnectionFactory(HOST, PORT, configT);
        pool = factory.newConnectionPool();
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
        assertThrows(Exception.class, () -> ConnectionFactory.acquireChannel(pool));
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
