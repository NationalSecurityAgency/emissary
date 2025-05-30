package emissary.util.grpc.pool;

import emissary.config.Configurator;
import emissary.util.grpc.exceptions.GrpcPoolException;

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
 * Abstract base class for managing a pool of gRPC {@link ManagedChannel} connections.
 * <p>
 * Configuration Keys:
 * <ul>
 * <li>{@code MIN_IDLE_CONNS} - Minimum idle connections in the pool</li>
 * <li>{@code MAX_IDLE_CONNS} - Maximum idle connections in the pool</li>
 * <li>{@code MAX_POOL_SIZE} - Total max connections allowed</li>
 * <li>{@code MAX_WAIT_POOL_BORROW} - Time to wait before failing a borrow attempt (ms)</li>
 * <li>{@code LIFO} / {@code LIFO_POOL} - Whether pool behaves LIFO or FIFO</li>
 * <li>{@code BLOCK_WHEN_POOL_EXHAUSTED} - Whether threads should block when pool is empty</li>
 * <li>{@code GRPC_KEEP_ALIVE_MS} - Time to wait before sending a ping on idle</li>
 * <li>{@code GRPC_KEEP_ALIVE_TIMEOUT_MS} - Timeout for receiving ping ACKs</li>
 * <li>{@code GRPC_KEEP_ALIVE_WITHOUT_CALLS} - Send pings even when no RPCs are active</li>
 * <li>{@code GRPC_MAX_INBOUND_MESSAGE_SIZE} - Max inbound gRPC message size (bytes)</li>
 * <li>{@code GRPC_MAX_INBOUND_METADATA_SIZE} - Max inbound gRPC metadata size (bytes)</li>
 * <li>{@code LOAD_BALANCING_POLICY} - gRPC load balancing policy (e.g. "round_robin")</li>
 * <li>{@code ERODING_POOL_FACTOR} - Optional shrink rate for idle connections</li>
 * </ul>
 */
public abstract class ConnectionFactory extends BasePooledObjectFactory<ManagedChannel> {
    private static final String MIN_IDLE_CONNS = "MIN_IDLE_CONNS";
    private static final String MAX_IDLE_CONNS = "MAX_IDLE_CONNS";
    private static final String MAX_POOL_SIZE = "MAX_POOL_SIZE";
    private static final String LIFO = "LIFO";
    private static final String LIFO_POOL = "LIFO_POOL";
    private static final String BLOCK_WHEN_POOL_EXHAUSTED = "BLOCK_WHEN_POOL_EXHAUSTED";
    private static final String MAX_WAIT_POOL_BORROW = "MAX_WAIT_POOL_BORROW";
    private static final String ERODING_POOL_FACTOR = "ERODING_POOL_FACTOR";
    private static final String GRPC_KEEP_ALIVE_MS = "GRPC_KEEP_ALIVE_MS";
    private static final String GRPC_KEEP_ALIVE_TIMEOUT_MS = "GRPC_KEEP_ALIVE_TIMEOUT_MS";
    private static final String GRPC_KEEP_ALIVE_WITHOUT_CALLS = "GRPC_KEEP_ALIVE_WITHOUT_CALLS";
    private static final String GRPC_MAX_INBOUND_MESSAGE_SIZE = "GRPC_MAX_INBOUND_MESSAGE_SIZE";
    private static final String GRPC_MAX_INBOUND_METADATA_SIZE = "GRPC_MAX_INBOUND_METADATA_SIZE";
    private static final String LOAD_BALANCING_POLICY = "LOAD_BALANCING_POLICY";

    private static final Logger logger = LoggerFactory.getLogger(ConnectionFactory.class);

    private final GenericObjectPoolConfig<ManagedChannel> poolConfig = new GenericObjectPoolConfig<>();

    private final String host;
    private final int port;
    private final String target;
    private final long keepAlive;
    private final long keepAliveTimeout;
    private final boolean keepAliveWithoutCalls;
    private final int maxInboundMessageSize;
    private final int maxInboundMetadataSize;
    private final String loadBalancingPolicy;
    private final float erodingPoolFactor;

    /**
     * Constructs a new gRPC connection factory using the provided host, port, and configuration. Initializes pool settings
     * and gRPC channel properties from the given configuration source.
     *
     * @param host gRPC service hostname or DNS target
     * @param port gRPC service port
     * @param configG configuration provider for channel and pool parameters
     */
    protected ConnectionFactory(String host, int port, Configurator configG) {
        this.host = host;
        this.port = port;
        this.target = host + ":" + port; // target may be a host or dns service

        // How often (in milliseconds) to send pings when the connection is idle
        this.keepAlive = configG.findLongEntry(GRPC_KEEP_ALIVE_MS, ConnectionDefaults.GRPC_KEEP_ALIVE_MS);

        // Time to wait (in milliseconds) for a ping ACK before closing the connection
        this.keepAliveTimeout = configG.findLongEntry(GRPC_KEEP_ALIVE_TIMEOUT_MS, ConnectionDefaults.GRPC_KEEP_ALIVE_TIMEOUT_MS);

        // Whether to send pings when no RPCs are active
        // Note: Seme gRPC services have this set to false and will be noisy if not adjusted
        this.keepAliveWithoutCalls = configG.findBooleanEntry(GRPC_KEEP_ALIVE_WITHOUT_CALLS, ConnectionDefaults.GRPC_KEEP_ALIVE_WITHOUT_CALLS);

        // Max size (in bytes) for incoming messages and message metadata from the server
        this.maxInboundMessageSize = configG.findIntEntry(GRPC_MAX_INBOUND_MESSAGE_SIZE, ConnectionDefaults.GRPC_MAX_INBOUND_MESSAGE_SIZE);
        this.maxInboundMetadataSize = configG.findIntEntry(GRPC_MAX_INBOUND_METADATA_SIZE, ConnectionDefaults.GRPC_MAX_INBOUND_METADATA_SIZE);

        // Specifies how the client chooses between multiple backend addresses
        // e.g. "pick_first" uses the first address only, while "round_robin" cycles through all of them for client-side
        // balancing
        this.loadBalancingPolicy = configG.findStringEntry(LOAD_BALANCING_POLICY, ConnectionDefaults.LOAD_BALANCING_POLICY);

        // Controls how aggressively idle connections are phased out over time
        // Set to a float between 0.0 and 1.0 to enable erosion (e.g. 0.2 = mild erosion)
        // Set to -1.0 to disable connection pool erosion entirely
        this.erodingPoolFactor = (float) configG.findDoubleEntry(ERODING_POOL_FACTOR, ConnectionDefaults.ERODING_POOL_FACTOR);

        // Min/max number of idle connections in pool
        this.poolConfig.setMinIdle(configG.findIntEntry(MIN_IDLE_CONNS, ConnectionDefaults.MIN_IDLE_CONNS));
        this.poolConfig.setMaxIdle(configG.findIntEntry(MAX_IDLE_CONNS, ConnectionDefaults.MAX_IDLE_CONNS));

        // Max number of total connections in pool
        this.poolConfig.setMaxTotal(configG.findIntEntry(MAX_POOL_SIZE, ConnectionDefaults.MAX_POOL_SIZE));

        // Order for pool to borrow connections
        this.poolConfig.setLifo(configG.findBooleanEntry(LIFO_POOL,
                configG.findBooleanEntry(LIFO, BaseObjectPoolConfig.DEFAULT_LIFO)));

        // Enable thread blocking when borrowing from exhausted pool
        this.poolConfig.setBlockWhenExhausted(configG.findBooleanEntry(BLOCK_WHEN_POOL_EXHAUSTED,
                BaseObjectPoolConfig.DEFAULT_BLOCK_WHEN_EXHAUSTED));

        // Max duration to wait until block is released from exhausted pool
        this.poolConfig.setMaxWait(Duration.ofMillis(configG.findLongEntry(MAX_WAIT_POOL_BORROW, ConnectionDefaults.MAX_WAIT_POOL_BORROW)));
    }

    /**
     * Borrows a {@link ManagedChannel} from the provided connection pool.
     *
     * @param pool the object pool to borrow from
     * @return a managed gRPC channel
     * @throws GrpcPoolException if the pool is exhausted or borrowing fails
     */
    public static ManagedChannel acquireChannel(ObjectPool<ManagedChannel> pool) {
        try {
            return pool.borrowObject();
        } catch (Exception e) {
            throw new GrpcPoolException(String.format("Unable to borrow channel from pool: %s", e.getMessage()));
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
                .keepAliveWithoutCalls(this.keepAliveWithoutCalls)
                .defaultLoadBalancingPolicy(this.loadBalancingPolicy)
                .usePlaintext()
                .maxInboundMessageSize(this.maxInboundMessageSize)
                .maxInboundMetadataSize(this.maxInboundMetadataSize)
                .keepAliveTime(this.keepAlive, TimeUnit.MILLISECONDS)
                .keepAliveTimeout(this.keepAliveTimeout, TimeUnit.MILLISECONDS).build();
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
     * Called when a {@link ManagedChannel} is returned to the pool. No-op by default, since gRPC channels are designed to
     * remain ready for reuse without needing to be reset or cleared. Override this if using a stub or channel wrapper that
     * requires cleanup between uses.
     *
     * @param pooledObject the pooled channel being passivated
     */
    @Override
    public void passivateObject(PooledObject<ManagedChannel> pooledObject) { /* No-op */ }

    /**
     * Validates whether the {@link ManagedChannel} is healthy and can be reused. Called by the pool before returning a
     * channel to a caller. Implementations should check connection state or channel health as needed. For example, you
     * might verify that the channel is not shutdown or terminated.
     *
     * @param pooledObject the pooled gRPC channel to validate
     * @return true if the channel is valid and safe to reuse, false otherwise
     */
    @Override
    public abstract boolean validateObject(PooledObject<ManagedChannel> pooledObject);

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

    public String getLoadBalancingPolicy() {
        return loadBalancingPolicy;
    }

    public float getErodingPoolFactor() {
        return erodingPoolFactor;
    }
}
