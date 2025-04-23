package emissary.util.grpcpool;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.test.core.junit5.UnitTest;

import io.grpc.ManagedChannel;
import org.apache.commons.pool2.PooledObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GrpcConnectionFactoryTest extends UnitTest {
    private static final String host = "localhost";
    private static final int port = 2222;
    private static final int minIdleConns = 5;
    private static final int maxIdleConns = 6;

    private static final int keepAlive = 7;

    private static final int keepAliveTimeout = 8;
    private static final int maxInboundMsgSize = 1000;
    private static final int maxInboundMetadataSize = 100;
    private static final int maxPoolSize = 10;
    private static final float erodingPoolFactor = 1.0f;
    private static final boolean keepAliveWithoutCalls = false;
    private static final boolean lifo = false;
    private static final boolean blockWhenPoolExhausted = true;
    private static final Long maxWaitPoolBorrow = Duration.parse("PT10M").toMillis();
    private GrpcConnectionFactory factory;

    public static class TestFactory extends GrpcConnectionFactory {
        public TestFactory(String host, int port, Configurator configG) {
            super(host, port, configG);
        }

        @Override
        public boolean validateObject(PooledObject<ManagedChannel> pooledObject) {
            return true;
        }
    }

    @BeforeEach
    public void init() throws Exception {
        // This method of creating a configurator for testes requires either all unit tests are run
        // or the -Demissary.config.dir option enabled.
        Configurator configT = new ServiceConfigGuide();
        // Easily testable parameters
        configT.addEntry("MIN_IDLE_CONNS", String.valueOf(minIdleConns));
        configT.addEntry("MAX_IDLE_CONNS", String.valueOf(maxIdleConns));
        configT.addEntry("MAX_POOL_SIZE", String.valueOf(maxPoolSize));
        configT.addEntry("BLOCK_WHEN_POOL_EXHAUSTED", String.valueOf(blockWhenPoolExhausted));
        configT.addEntry("LIFO_POOL", String.valueOf(lifo));
        configT.addEntry("MAX_WAIT_POOL_BORROW", String.valueOf(maxWaitPoolBorrow));

        // Parameters buried somewhere in pool config
        configT.addEntry("ERODING_POOL_FACTOR", String.valueOf(erodingPoolFactor));
        configT.addEntry("GRPC_KEEP_ALIVE_MS", String.valueOf(keepAlive));
        configT.addEntry("GRPC_KEEP_ALIVE_TIMEOUT_MS", String.valueOf(keepAliveTimeout));
        configT.addEntry("GRPC_KEEP_ALIVE_WITHOUT_CALLS", String.valueOf(keepAliveWithoutCalls));
        configT.addEntry("GRPC_MAX_INBOUND_MESSAGE_SIZE", String.valueOf(maxInboundMsgSize));
        configT.addEntry("GRPC_MAX_INBOUND_METADATA_SIZE", String.valueOf(maxInboundMetadataSize));

        factory = new TestFactory(host, port, configT);
    }

    @Test
    void testCreateModelFromConfig() {
        assertEquals(minIdleConns, factory.poolConfig.getMinIdle(), "Mismatched minIdleConnections");
        assertEquals(maxIdleConns, factory.poolConfig.getMaxIdle(), "Mismatched maxIdleConnections");
        assertEquals(maxPoolSize, factory.poolConfig.getMaxTotal(), "Mismatched total connections");
        assertEquals(blockWhenPoolExhausted, factory.poolConfig.getBlockWhenExhausted(), "Mismatched blockWhenPoolExhausted");
        assertEquals(lifo, factory.poolConfig.getLifo(), "Mismatched pool stack order - lifo");
        assertEquals(maxWaitPoolBorrow, factory.poolConfig.getMaxWaitDuration().toMillis(), "Mismatched maxWaitPoolBorrow");
    }

    @Test
    void testManagedChannelParameters() {
        assertEquals(host, factory.getHost(), "Mismatched host");
        assertEquals(port, factory.getPort(), "Mismatched port");
        assertEquals(keepAlive, factory.getKeepAlive(), "Mismatched keepAlive");
        assertEquals(keepAliveTimeout, factory.getKeepAliveTimeout(), "Mismatched keepAliveTimeout");
        assertEquals(keepAliveWithoutCalls, factory.isKeepAliveWithoutCalls(), "Mismatched isKeepAliveWithoutCalls");
        assertEquals(maxInboundMsgSize, factory.getMaxInboundMessageSize(), "Mismatched maxInboundMessageSize");
        assertEquals(maxInboundMetadataSize, factory.getMaxInboundMetadataSize(), "Mismatched maxInboundMetadataSize");
        assertEquals(erodingPoolFactor, factory.getErodingPoolFactor(), "Mismatched erodingPoolFactor");
    }
}
