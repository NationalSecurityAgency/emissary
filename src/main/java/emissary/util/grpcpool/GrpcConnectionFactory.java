package emissary.util.grpcpool;

import emissary.config.Configurator;

import com.google.common.annotations.VisibleForTesting;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.Nullable;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PoolUtils;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.BaseObjectPoolConfig;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * GrpcConnectionFactory allows vista grpc connections to be handled in an Apache connection pools
 *
 */
public abstract class GrpcConnectionFactory extends BasePooledObjectFactory<ManagedChannel> {
    private static final Logger logger = LoggerFactory.getLogger(GrpcConnectionFactory.class);
    private final String host;
    private final int port;
    private final long keepAlive;
    private final long keepAliveTimeout;
    private final boolean keepAliveWithoutCalls;
    private final int maxInboundMessageSize;
    private final int maxInboundMetadataSize;
    private final float erodingPoolFactor;

    // Apache commons connection pool min idle connections
    private static final String MIN_IDLE_CONNS = "MIN_IDLE_CONNS";
    // Apache commons connection pool max idle connections
    private static final String MAX_IDLE_CONNS = "MAX_IDLE_CONNS";
    // Apache commons connection pool max number of total connections
    private static final String MAX_POOL_SIZE = "MAX_POOL_SIZE";
    // Apache commons connection pool order to borrow connections
    private static final String LIFO = "LIFO";
    private static final String LIFO_POOL = "LIFO_POOL";
    // Apache commons connection pool enable thread blocking when borrowing from exhausted pool
    private static final String BLOCK_WHEN_POOL_EXHAUSTED = "BLOCK_WHEN_POOL_EXHAUSTED";
    // Apache commons connection pool max duration to wait until block is released from exhausted pool
    private static final String MAX_WAIT_POOL_BORROW = "MAX_WAIT_POOL_BORROW";
    // When positive, Apache common pool util parameter for fraction of time
    // (15 minutes initially, 10 minutes on update) to phase out a connection
    private static final String ERODING_POOL_FACTOR = "ERODING_POOL_FACTOR";
    // Grpc connection keep alive query time in milliseconds
    private static final String GRPC_KEEP_ALIVE_MS = "GRPC_KEEP_ALIVE_MS";
    // Grpc connection keep alive timeout in milliseconds
    private static final String GRPC_KEEP_ALIVE_TIMEOUT_MS = "GRPC_KEEP_ALIVE_TIMEOUT_MS";
    // Grpc connection uses keep alive pings when there isn't an active connection.
    // Note: Seme grpc services, like Triton Inference server, have keepAliveWithoutCalls set to false
    // and will be noisy if this is not adjusted
    private static final String GRPC_KEEP_ALIVE_WITHOUT_CALLS = "GRPC_KEEP_ALIVE_WITHOUT_CALLS";
    // Grpc connection allowed maximum size for incoming messages from the server.
    private static final String GRPC_MAX_INBOUND_MESSAGE_SIZE = "GRPC_MAX_INBOUND_MESSAGE_SIZE";
    // Grpc connection allowed maximum size for incoming message metadata from the server.
    private static final String GRPC_MAX_INBOUND_METADATA_SIZE = "GRPC_MAX_INBOUND_METADATA_SIZE";

    public static final int DEFAULT_MIN_IDLE_CONNS = 0;
    public static final int DEFAULT_MAX_IDLE_CONNS = 8;
    public static final int DEFAULT_MAX_POOL_SIZE = 8;
    public static final long DEFAULT_MAX_WAIT_POOL_BORROW = 10000L;
    // Default Grpc settings based from:
    // https://docs.microsoft.com/en-us/aspnet/core/grpc/performance?view=aspnetcore-5.0
    public static final float DEFAULT_ERODING_POOL_FACTOR = -1.0f;
    // Default Grpc settings based from:
    // https://docs.microsoft.com/en-us/aspnet/core/grpc/performance?view=aspnetcore-5.0
    public static final long DEFAULT_GRPC_KEEP_ALIVE_MS = 60000L;
    public static final long DEFAULT_GRPC_KEEP_ALIVE_TIMEOUT_MS = 30000L;
    public static final boolean DEFAULT_GRPC_KEEP_ALIVE_WITHOUT_CALLS = false;

    public static final int DEFAULT_GRPC_MAX_INBOUND_METADATA_SIZE = 1 << 13; // Reported default in grpc-java is 8KiB
    // (2^13)
    public static final int DEFAULT_GRPC_MAX_INBOUND_MESSAGE_SIZE = 1 << 22; // Reported default in grpc-java is 4MiB
    // (2^22)


    @VisibleForTesting
    GenericObjectPoolConfig<ManagedChannel> poolConfig;


    /**
     * GrpcConnectionFactory creates a factory for generating connection pools
     *
     * @param host grpc connection host
     * @param port grpc connection port
     * @param configG configurator containing the parameters for the connection pool and grpc connections
     */
    protected GrpcConnectionFactory(String host, int port, Configurator configG) {
        // Grpc connection parameters
        this.host = host;
        this.port = port;
        this.keepAlive = configG.findLongEntry(GRPC_KEEP_ALIVE_MS, DEFAULT_GRPC_KEEP_ALIVE_MS);
        this.keepAliveTimeout = configG.findLongEntry(GRPC_KEEP_ALIVE_TIMEOUT_MS, DEFAULT_GRPC_KEEP_ALIVE_TIMEOUT_MS);
        this.keepAliveWithoutCalls = configG.findBooleanEntry(GRPC_KEEP_ALIVE_WITHOUT_CALLS, DEFAULT_GRPC_KEEP_ALIVE_WITHOUT_CALLS);
        this.maxInboundMessageSize = configG.findIntEntry(GRPC_MAX_INBOUND_MESSAGE_SIZE, DEFAULT_GRPC_MAX_INBOUND_MESSAGE_SIZE);
        this.maxInboundMetadataSize = configG.findIntEntry(GRPC_MAX_INBOUND_METADATA_SIZE, DEFAULT_GRPC_MAX_INBOUND_METADATA_SIZE);

        // Generate Apache Commons pool config
        final int minIdleConns = configG.findIntEntry(MIN_IDLE_CONNS, DEFAULT_MIN_IDLE_CONNS);
        final int maxIdleConns = configG.findIntEntry(MAX_IDLE_CONNS, DEFAULT_MAX_IDLE_CONNS);
        final int maxPoolSize = configG.findIntEntry(MAX_POOL_SIZE, DEFAULT_MAX_POOL_SIZE);
        final boolean lifo = configG.findBooleanEntry(LIFO, BaseObjectPoolConfig.DEFAULT_LIFO);
        final boolean lifoPool = configG.findBooleanEntry(LIFO_POOL, lifo);
        final boolean blockWhenPoolExhausted = configG.findBooleanEntry(BLOCK_WHEN_POOL_EXHAUSTED, BaseObjectPoolConfig.DEFAULT_BLOCK_WHEN_EXHAUSTED);
        final Duration maxWaitPoolBorrow = Duration.ofMillis(configG.findLongEntry(MAX_WAIT_POOL_BORROW, DEFAULT_MAX_WAIT_POOL_BORROW));
        this.erodingPoolFactor = (float) configG.findDoubleEntry(ERODING_POOL_FACTOR, DEFAULT_ERODING_POOL_FACTOR);

        this.poolConfig = new GenericObjectPoolConfig<>();
        this.poolConfig.setMinIdle(minIdleConns);
        this.poolConfig.setMaxIdle(maxIdleConns);
        this.poolConfig.setMaxTotal(maxPoolSize);
        this.poolConfig.setLifo(lifoPool);
        this.poolConfig.setBlockWhenExhausted(blockWhenPoolExhausted);
        this.poolConfig.setMaxWait(maxWaitPoolBorrow);
    }

    /**
     * acquire channel returns a channel from the given channel pool.
     *
     * @param pool that contains the objects
     * @return channel
     * @throws RuntimeException failed borrow
     */
    public static ManagedChannel acquireChannel(ObjectPool<ManagedChannel> pool) {
        // Make connection
        // Create gRPC-stub for communicating with the server
        try {
            return pool.borrowObject();
        } catch (Exception ex) {
            // Catch IllegalStateException, Exception, and NoSuchElementException that occur when borrowing an object.
            throw new GrpcPoolException(String.format("Unable to borrow channel from pool: %s", ex.getMessage()));
        }
    }

    public static void invalidateChannel(@Nullable ManagedChannel channel, ObjectPool<ManagedChannel> pool) {
        if (channel != null) {
            try {
                pool.invalidateObject(channel);
            } catch (Exception inv) {
                logger.error("Unable to invalidate borrowed grpc channel, check for possible resource leak: {}", inv.getMessage());
            }
        }
    }

    /**
     * returnConnection returns the connection to the channel and handles errors
     *
     * @param channel to return
     * @param pool to return channel to
     */
    public static void returnChannel(@Nullable ManagedChannel channel, ObjectPool<ManagedChannel> pool) {
        try {
            if (channel != null) {
                pool.returnObject(channel);
            }
        } catch (Exception e) {
            try {
                logger.warn("Unable to cleanly return grpc connection channel to the pool: {}", e.getMessage());
                logger.debug("Stack trace: ", e);
                pool.invalidateObject(channel);
            } catch (Exception inv) {
                logger.error("Unable to invalidate existing grpc connection - be wary of unclosed references: {}", inv.getMessage());
                logger.debug("Stack trace: ", inv);
            }
        }
    }

    /**
     * newConnectionPool uses a factory to generate a connection pool
     *
     * @return an Apache commons connection pool
     */
    public ObjectPool<ManagedChannel> newConnectionPool() {
        if (this.erodingPoolFactor > 0) {
            return PoolUtils.erodingPool(new GenericObjectPool<>(this, this.poolConfig), this.erodingPoolFactor);
        }
        return new GenericObjectPool<>(this, this.poolConfig);
    }

    @Override
    public ManagedChannel create() {
        String loadBalancing = "round_robin";
        String target = String.format("%s:%d", this.host, this.port); // target may be a host or dns service
        return ManagedChannelBuilder.forTarget(target)
                .keepAliveWithoutCalls(this.keepAliveWithoutCalls)
                .defaultLoadBalancingPolicy(loadBalancing)
                .usePlaintext()
                .maxInboundMessageSize(this.maxInboundMessageSize)
                .maxInboundMetadataSize(this.maxInboundMetadataSize)
                .keepAliveTime(this.keepAlive, TimeUnit.MILLISECONDS)
                .keepAliveTimeout(this.keepAliveTimeout, TimeUnit.MILLISECONDS).build();
    }


    /**
     * Use the default PooledObject implementation.
     */
    @Override
    public PooledObject<ManagedChannel> wrap(ManagedChannel channel) {
        return new DefaultPooledObject<>(channel);
    }

    /**
     * When an object is removed from the pool, do nothing
     */
    @Override
    public void passivateObject(PooledObject<ManagedChannel> pooledObject) {
        // No-op since grpc connections aren't set idle, but are instead recommended to either stay active or timeout.
    }

    /**
     * Override this function for custom channel validation. When an object is validated, check for a connection
     */
    @Override
    public abstract boolean validateObject(PooledObject<ManagedChannel> pooledObject);

    /**
     * When an object is removed, shutdown the channel
     */
    @Override
    public void destroyObject(PooledObject<ManagedChannel> pooledObject) {
        pooledObject.getObject().shutdownNow();
    }

    // for all other methods, the no-op implementation
    // in BasePooledObjectFactory will suffice


    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public long getKeepAlive() {
        return keepAlive;
    }

    public long getKeepAliveTimeout() {
        return keepAliveTimeout;
    }

    public boolean isKeepAliveWithoutCalls() {
        return keepAliveWithoutCalls;
    }

    public int getMaxInboundMessageSize() {
        return maxInboundMessageSize;
    }

    public int getMaxInboundMetadataSize() {
        return maxInboundMetadataSize;
    }

    public float getErodingPoolFactor() {
        return erodingPoolFactor;
    }
}
