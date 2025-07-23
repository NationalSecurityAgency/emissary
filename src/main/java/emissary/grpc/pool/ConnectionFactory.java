package emissary.grpc.pool;

import emissary.config.Configurator;
import emissary.grpc.exceptions.PoolException;

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
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Abstract base class for managing a pool of gRPC {@link ManagedChannel} connections.
 * <p>
 * Configuration Keys:
 * <ul>
 * <li>{@code GRPC_KEEP_ALIVE_MILLIS} - Time to wait before sending a ping on idle, default={@code 60000L}</li>
 * <li>{@code GRPC_KEEP_ALIVE_TIMEOUT_MILLIS} - Timeout for receiving ping ACKs, default={@code 30000L}</li>
 * <li>{@code GRPC_KEEP_ALIVE_WITHOUT_CALLS} - Send pings even when no RPCs are active if {@code true},
 * default={@code false}</li>
 * <li>{@code GRPC_LOAD_BALANCING_POLICY} - gRPC load balancing policy, default={@code "round_robin"}</li>
 * <li>{@code GRPC_MAX_INBOUND_MESSAGE_BYTE_SIZE} - Max inbound gRPC message size, default={@code 4194304}</li>
 * <li>{@code GRPC_MAX_INBOUND_METADATA_BYTE_SIZE} - Max inbound gRPC metadata size, default={@code 8192}</li>
 * <li>{@code GRPC_POOL_BLOCK_EXHAUSTED} - If {@code true}, threads block when pool is empty, otherwise throws an
 * Exception, default={@code true}</li>
 * <li>{@code GRPC_POOL_ERODING_FACTOR} - Optional shrink rate for idle connections, default={@code -1.0f}</li>
 * <li>{@code GRPC_POOL_MAX_BORROW_WAIT_MILLIS} - Time to wait before failing a borrow attempt,
 * default={@code 10000L}</li>
 * <li>{@code GRPC_POOL_MAX_IDLE_CONNECTIONS} - Maximum idle connections in the pool, default={@code 8}</li>
 * <li>{@code GRPC_POOL_MAX_SIZE} - Maximum total connections allowed, default={@code 8}</li>
 * <li>{@code GRPC_POOL_MIN_IDLE_CONNECTIONS} - Minimum idle connections in the pool, default={@code 0}</li>
 * <li>{@code GRPC_POOL_RETRIEVAL_ORDER} - Whether pool behaves LIFO or FIFO, default={@code "LIFO"}</li>
 * <li>{@code GRPC_POOL_TEST_BEFORE_BORROW} - If {@code true}, validates pooled connections before use with
 * {@link #validateObject(PooledObject)}, default={@code true}</li>
 * </ul>
 * <a href="https://docs.microsoft.com/en-us/aspnet/core/grpc/performance?view=aspnetcore-5.0">Source</a> for default
 * gRPC configurations.
 */
public class ConnectionFactory extends BasePooledObjectFactory<ManagedChannel> {
    public static final String GRPC_KEEP_ALIVE_MILLIS = "GRPC_KEEP_ALIVE_MILLIS";
    public static final String GRPC_KEEP_ALIVE_TIMEOUT_MILLIS = "GRPC_KEEP_ALIVE_TIMEOUT_MILLIS";
    public static final String GRPC_KEEP_ALIVE_WITHOUT_CALLS = "GRPC_KEEP_ALIVE_WITHOUT_CALLS";
    public static final String GRPC_LOAD_BALANCING_POLICY = "GRPC_LOAD_BALANCING_POLICY";
    public static final String GRPC_MAX_INBOUND_MESSAGE_BYTE_SIZE = "GRPC_MAX_INBOUND_MESSAGE_BYTE_SIZE";
    public static final String GRPC_MAX_INBOUND_METADATA_BYTE_SIZE = "GRPC_MAX_INBOUND_METADATA_BYTE_SIZE";
    public static final String GRPC_POOL_BLOCK_EXHAUSTED = "GRPC_POOL_BLOCK_EXHAUSTED";
    public static final String GRPC_POOL_ERODING_FACTOR = "GRPC_POOL_ERODING_FACTOR";
    public static final String GRPC_POOL_MAX_BORROW_WAIT_MILLIS = "GRPC_POOL_MAX_BORROW_WAIT_MILLIS";
    public static final String GRPC_POOL_MAX_IDLE_CONNECTIONS = "GRPC_POOL_MAX_IDLE_CONNECTIONS";
    public static final String GRPC_POOL_MAX_SIZE = "GRPC_POOL_MAX_SIZE";
    public static final String GRPC_POOL_MIN_IDLE_CONNECTIONS = "GRPC_POOL_MIN_IDLE_CONNECTIONS";
    public static final String GRPC_POOL_RETRIEVAL_ORDER = "GRPC_POOL_RETRIEVAL_ORDER";
    public static final String GRPC_POOL_TEST_BEFORE_BORROW = "GRPC_POOL_TEST_BEFORE_BORROW";

    protected static final Logger logger = LoggerFactory.getLogger(ConnectionFactory.class);

    private final GenericObjectPoolConfig<ManagedChannel> poolConfig = new GenericObjectPoolConfig<>();
    private final Predicate<ManagedChannel> channelValidator;
    private final Consumer<ManagedChannel> channelPassivator;

    private final String host;
    private final int port;
    private final String target;
    private final long keepAliveMillis;
    private final long keepAliveTimeoutMillis;
    private final boolean keepAliveWithoutCalls;
    private final int maxInboundMessageByteSize;
    private final int maxInboundMetadataByteSize;
    private final String loadBalancingPolicy;
    private final float erodingPoolFactor;

    /**
     * Constructs a new gRPC connection factory using the provided host, port, and configuration. Initializes pool settings
     * and gRPC channel properties from the given configuration source.
     * <p>
     * See {@link ConnectionFactory} for supported configuration keys and defaults.
     * 
     * @param host gRPC service hostname or DNS target
     * @param port gRPC service port
     * @param configG configuration provider for channel and pool parameters
     * @param channelValidator method to determine if channel can successfully communicate with its associated server
     * @param channelPassivator method that cleans up a channel after a gRPC call
     */
    public ConnectionFactory(String host, int port, Configurator configG,
            Predicate<ManagedChannel> channelValidator, Consumer<ManagedChannel> channelPassivator) {
        this.host = host;
        this.port = port;
        this.target = host + ":" + port; // target may be a host or dns service

        this.channelValidator = channelValidator;
        this.channelPassivator = channelPassivator;

        // How often (in milliseconds) to send pings when the connection is idle
        this.keepAliveMillis = configG.findLongEntry(GRPC_KEEP_ALIVE_MILLIS, 60000L);

        // Time to wait (in milliseconds) for a ping ACK before closing the connection
        this.keepAliveTimeoutMillis = configG.findLongEntry(GRPC_KEEP_ALIVE_TIMEOUT_MILLIS, 30000L);

        // Whether to send pings when no RPCs are active
        // Note: Seme gRPC services have this set to false and will be noisy if not adjusted
        this.keepAliveWithoutCalls = configG.findBooleanEntry(GRPC_KEEP_ALIVE_WITHOUT_CALLS, false);

        // Specifies how the client chooses between multiple backend addresses
        // e.g. "pick_first" uses the first address only, "round_robin" cycles through all of them for client-side balancing
        this.loadBalancingPolicy = LoadBalancingPolicy.getPolicyName(
                configG.findStringEntry(GRPC_LOAD_BALANCING_POLICY), LoadBalancingPolicy.ROUND_ROBIN);

        // Max size (in bytes) for incoming messages and message metadata from the server
        this.maxInboundMessageByteSize = configG.findIntEntry(GRPC_MAX_INBOUND_MESSAGE_BYTE_SIZE, 4 << 20); // 4 MiB
        this.maxInboundMetadataByteSize = configG.findIntEntry(GRPC_MAX_INBOUND_METADATA_BYTE_SIZE, 8 << 10); // 8 KiB

        // Controls how aggressively idle connections are phased out over time
        // Set to a float between 0.0 and 1.0 to enable erosion (e.g. 0.2 = mild erosion)
        // Set to -1.0 to disable automatic pool shrinking entirely
        this.erodingPoolFactor = (float) configG.findDoubleEntry(GRPC_POOL_ERODING_FACTOR, -1.0f);

        // Enable thread blocking when borrowing from exhausted pool
        this.poolConfig.setBlockWhenExhausted(configG.findBooleanEntry(GRPC_POOL_BLOCK_EXHAUSTED,
                BaseObjectPoolConfig.DEFAULT_BLOCK_WHEN_EXHAUSTED));

        // Max duration to wait until block is released from exhausted pool
        this.poolConfig.setMaxWait(Duration.ofMillis(configG.findLongEntry(GRPC_POOL_MAX_BORROW_WAIT_MILLIS, 10000L)));

        // Min/max number of idle connections in pool
        this.poolConfig.setMinIdle(configG.findIntEntry(GRPC_POOL_MIN_IDLE_CONNECTIONS, 0));
        this.poolConfig.setMaxIdle(configG.findIntEntry(GRPC_POOL_MAX_IDLE_CONNECTIONS, 8));

        // Max number of total connections in pool
        this.poolConfig.setMaxTotal(configG.findIntEntry(GRPC_POOL_MAX_SIZE, 8));

        // Order for pool to borrow connections
        this.poolConfig.setLifo(PoolRetrievalOrdering.isLifo(
                configG.findStringEntry(GRPC_POOL_RETRIEVAL_ORDER), PoolRetrievalOrdering.LIFO));

        // Whether to validate channels when borrowing from the pool
        this.poolConfig.setTestOnBorrow(configG.findBooleanEntry(GRPC_POOL_TEST_BEFORE_BORROW, true));
    }

    /**
     * Borrows a {@link ManagedChannel} from the provided connection pool.
     *
     * @param pool the object pool to borrow from
     * @return a managed gRPC channel
     * @throws PoolException if the pool is exhausted or borrowing fails
     */
    public static ManagedChannel acquireChannel(ObjectPool<ManagedChannel> pool) {
        try {
            return pool.borrowObject();
        } catch (Exception e) {
            throw new PoolException(String.format("Unable to borrow channel from pool: %s", e.getMessage()));
        }
    }

    /**
     * Invalidates a {@link ManagedChannel}, removing it from the pool. Used when a channel is considered unhealthy or no
     * longer usable.
     *
     * @param channel the channel to invalidate
     * @param pool the pool the channel belongs to
     */
    public static void invalidateChannel(@Nullable ManagedChannel channel, ObjectPool<ManagedChannel> pool) {
        if (channel != null) {
            try {
                pool.invalidateObject(channel);
            } catch (Exception e) {
                logger.error("Unable to invalidate existing grpc connection - check for possible resource leaks: {}", e.getMessage());
                logger.debug("Stack trace: ", e);
            }
        }
    }

    /**
     * Returns a previously acquired {@link ManagedChannel} to the connection pool. If the return fails, attempts to
     * invalidate the channel to avoid pool corruption.
     *
     * @param channel the channel to return
     * @param pool the pool to return the channel to
     */
    public static void returnChannel(@Nullable ManagedChannel channel, ObjectPool<ManagedChannel> pool) {
        try {
            if (channel != null) {
                pool.returnObject(channel);
            }
        } catch (Exception e) {
            logger.warn("Unable to cleanly return grpc connection channel to the pool: {}", e.getMessage());
            logger.debug("Stack trace: ", e);
            invalidateChannel(channel, pool);
        }
    }

    /**
     * Creates a new Apache Commons object pool for managing {@link ManagedChannel} instances. If the erosion factor is
     * positive, wraps the pool with eroding behavior to gradually shrink idle connections.
     *
     * @return a new configured connection pool
     */
    public ObjectPool<ManagedChannel> newConnectionPool() {
        if (this.erodingPoolFactor > 0) {
            return PoolUtils.erodingPool(new GenericObjectPool<>(this, this.poolConfig), this.erodingPoolFactor);
        }
        return new GenericObjectPool<>(this, this.poolConfig);
    }

    /**
     * Creates a new {@link ManagedChannel} instance configured with the current factory settings. Called internally by the
     * object pool during channel instantiation.
     *
     * @return a new gRPC channel
     */
    @Override
    public ManagedChannel create() {
        return ManagedChannelBuilder.forTarget(this.target)
                .keepAliveTime(this.keepAliveMillis, TimeUnit.MILLISECONDS)
                .keepAliveTimeout(this.keepAliveTimeoutMillis, TimeUnit.MILLISECONDS)
                .keepAliveWithoutCalls(this.keepAliveWithoutCalls)
                .defaultLoadBalancingPolicy(this.loadBalancingPolicy)
                .maxInboundMessageSize(this.maxInboundMessageByteSize)
                .maxInboundMetadataSize(this.maxInboundMetadataByteSize)
                .usePlaintext().build();
    }

    /**
     * Wraps a {@link ManagedChannel} for use with the object pool. Provides pooling metadata and lifecycle tracking.
     *
     * @param channel the gRPC channel to wrap
     * @return a pooled object wrapper
     */
    @Override
    public PooledObject<ManagedChannel> wrap(ManagedChannel channel) {
        return new DefaultPooledObject<>(channel);
    }

    /**
     * Called when a {@link ManagedChannel} is returned to the pool. Since gRPC channels are designed to remain ready for
     * reuse without needing to be reset or cleared, a no-op passivator is fine for most use cases. Custom behavior may
     * become necessary if using a stub or channel wrapper that requires cleanup between uses.
     *
     * @param pooledObject the pooled channel being passivated
     */
    @Override
    public void passivateObject(PooledObject<ManagedChannel> pooledObject) {
        channelPassivator.accept(pooledObject.getObject());
    }

    /**
     * Validates whether the {@link ManagedChannel} is healthy and can be reused. Called by the pool before returning a
     * channel to a caller. Implementations should check connection state or channel health as needed. For example, you
     * might verify that the channel is not shutdown or terminated.
     *
     * @param pooledObject the pooled gRPC channel to validate
     * @return true if the channel is valid and safe to reuse, false otherwise
     */
    @Override
    public boolean validateObject(PooledObject<ManagedChannel> pooledObject) {
        return channelValidator.test(pooledObject.getObject());
    }

    /**
     * Cleans up a pooled {@link ManagedChannel} when it is removed from the pool. Immediately shuts down the channel to
     * release resources.
     *
     * @param pooledObject the pooled gRPC channel to destroy
     */
    @Override
    public void destroyObject(PooledObject<ManagedChannel> pooledObject) {
        pooledObject.getObject().shutdownNow();
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getTarget() {
        return target;
    }

    public long getKeepAliveMillis() {
        return keepAliveMillis;
    }

    public long getKeepAliveTimeoutMillis() {
        return keepAliveTimeoutMillis;
    }

    public boolean getKeepAliveWithoutCalls() {
        return keepAliveWithoutCalls;
    }

    public String getLoadBalancingPolicy() {
        return loadBalancingPolicy;
    }

    public int getMaxInboundMessageByteSize() {
        return maxInboundMessageByteSize;
    }

    public int getMaxInboundMetadataByteSize() {
        return maxInboundMetadataByteSize;
    }

    public float getErodingPoolFactor() {
        return erodingPoolFactor;
    }

    public boolean getPoolBlockedWhenExhausted() {
        return this.poolConfig.getBlockWhenExhausted();
    }

    public long getPoolMaxWaitMillis() {
        return this.poolConfig.getMaxWaitDuration().toMillis();
    }

    public int getPoolMinIdleConnections() {
        return this.poolConfig.getMinIdle();
    }

    public int getPoolMaxIdleConnections() {
        return this.poolConfig.getMaxIdle();
    }

    public int getPoolMaxTotalConnections() {
        return this.poolConfig.getMaxTotal();
    }

    public boolean getPoolIsLifo() {
        return this.poolConfig.getLifo();
    }

    public boolean getPoolIsFifo() {
        return !this.poolConfig.getLifo();
    }
}
