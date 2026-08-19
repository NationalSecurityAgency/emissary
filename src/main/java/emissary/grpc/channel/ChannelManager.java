package emissary.grpc.channel;

import emissary.config.Configurator;

import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for managing gRPC {@link io.grpc.ManagedChannel} connections.
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
 * </ul>
 */
public abstract class ChannelManager implements AutoCloseable {
    public static final String GRPC_CHANNEL_PREFIX = "GRPC_";
    public static final String KEEP_ALIVE_MILLIS = GRPC_CHANNEL_PREFIX + "KEEP_ALIVE_MILLIS";
    public static final String KEEP_ALIVE_TIMEOUT_MILLIS = GRPC_CHANNEL_PREFIX + "KEEP_ALIVE_TIMEOUT_MILLIS";
    public static final String KEEP_ALIVE_WITHOUT_CALLS = GRPC_CHANNEL_PREFIX + "KEEP_ALIVE_WITHOUT_CALLS";
    public static final String LOAD_BALANCING_POLICY = GRPC_CHANNEL_PREFIX + "LOAD_BALANCING_POLICY";
    public static final String MAX_INBOUND_MESSAGE_BYTE_SIZE = GRPC_CHANNEL_PREFIX + "MAX_INBOUND_MESSAGE_BYTE_SIZE";
    public static final String MAX_INBOUND_METADATA_BYTE_SIZE = GRPC_CHANNEL_PREFIX + "MAX_INBOUND_METADATA_BYTE_SIZE";

    protected static final int MAX_PORT_NUMBER = 0xFFFF;

    protected final Logger logger;

    protected final String host;
    protected final int port;
    protected final String target;
    protected final long keepAliveMillis;
    protected final long keepAliveTimeoutMillis;
    protected final boolean keepAliveWithoutCalls;
    protected final int maxInboundMessageByteSize;
    protected final int maxInboundMetadataByteSize;
    protected final String loadBalancingPolicy;

    /**
     * Constructs a new gRPC connection manager using the provided host, port, and configuration. Initializes gRPC channel
     * properties from the given configuration source.
     *
     * @param host gRPC service hostname or DNS target
     * @param port gRPC service port
     * @param configG configuration provider for channel parameters
     * @see ChannelManager
     * @see <a href="https://docs.microsoft.com/en-us/aspnet/core/grpc/performance?view=aspnetcore-5.0">Source</a> for
     *      default gRPC configurations.
     */
    protected ChannelManager(String host, int port, Configurator configG) {
        this.logger = LoggerFactory.getLogger(this.getClass().getName());

        this.host = host;
        this.port = port;
        this.target = createTarget();

        // How often (in milliseconds) to send pings when the connection is idle
        this.keepAliveMillis = configG.findLongEntry(KEEP_ALIVE_MILLIS, 60000L);

        // Time to wait (in milliseconds) for a ping ACK before closing the connection
        this.keepAliveTimeoutMillis = configG.findLongEntry(KEEP_ALIVE_TIMEOUT_MILLIS, 30000L);

        // Whether to send pings when no RPCs are active
        // Note: Seme gRPC services have this set to false and will be noisy if not adjusted
        this.keepAliveWithoutCalls = configG.findBooleanEntry(KEEP_ALIVE_WITHOUT_CALLS, false);

        // Specifies how the client chooses between multiple backend addresses
        // e.g. "pick_first" uses the first address only, "round_robin" cycles through all of them for client-side balancing
        this.loadBalancingPolicy = configG.findObjectEntry(
                LOAD_BALANCING_POLICY, LoadBalancingPolicy::valueOf, LoadBalancingPolicy.ROUND_ROBIN).toString();

        // Max size (in bytes) for incoming messages and message metadata from the server
        this.maxInboundMessageByteSize = configG.findIntEntry(MAX_INBOUND_MESSAGE_BYTE_SIZE, 4 << 20); // 4 MiB
        this.maxInboundMetadataByteSize = configG.findIntEntry(MAX_INBOUND_METADATA_BYTE_SIZE, 8 << 10); // 8 KiB
    }

    private String createTarget() {
        if (StringUtils.isEmpty(host)) {
            throw new IllegalArgumentException("Missing required gRPC host configuration");
        }
        if (port <= 0 || port > MAX_PORT_NUMBER) {
            throw new IllegalArgumentException(
                    String.format("Port \"%d\" is outside valid range [1, %d]", port, MAX_PORT_NUMBER));
        }
        return host + ":" + port;
    }

    /**
     * Creates a new {@link ManagedChannel} instance configured with the current factory settings.
     *
     * @return a new gRPC channel
     */
    protected final ManagedChannel create() {
        return Grpc.newChannelBuilder(this.target, InsecureChannelCredentials.create())
                .keepAliveTime(this.keepAliveMillis, TimeUnit.MILLISECONDS)
                .keepAliveTimeout(this.keepAliveTimeoutMillis, TimeUnit.MILLISECONDS)
                .keepAliveWithoutCalls(this.keepAliveWithoutCalls)
                .defaultLoadBalancingPolicy(this.loadBalancingPolicy)
                .maxInboundMessageSize(this.maxInboundMessageByteSize)
                .maxInboundMetadataSize(this.maxInboundMetadataByteSize)
                .build();
    }

    /**
     * Acquires a {@link ManagedChannel} from the manager.
     *
     * @return a new {@link ManagedChannel} for gRPC connections
     */
    public abstract ManagedChannel acquire();

    /**
     * For use with {@link ChannelManager} implementations that expect channel exclusivity. Tells the manager that a
     * {@link ManagedChannel} is no longer in use.
     *
     * @param channel the channel to release
     */
    public abstract void release(ManagedChannel channel);

    /**
     * Shuts down a {@link ManagedChannel} and frees its resources. Must be called on each channel at the end of its
     * lifecycle to prevent memory leaks. If left open, channels retain active thread pools, TCP connections, and internal
     * buffers.
     *
     * @param channel the channel to shut down
     */
    public abstract void shutdown(ManagedChannel channel);

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract void close();

    /**
     * Returns the configured host name of the gRPC channel
     *
     * @return the host name
     */
    public String getHost() {
        return host;
    }

    /**
     * Returns the configured port number of the gRPC channel
     *
     * @return the port number
     */
    public int getPort() {
        return port;
    }

    /**
     * These policies dictate how gRPC distributes remote procedure calls (RPCs) across available backend subchannels
     * resolved by the name resolver.
     */
    public enum LoadBalancingPolicy {
        /**
         * Distributes outgoing RPCs sequentially across all available healthy backend subchannels.
         * <p>
         * This policy connects to all addresses returned by the name resolver and cycles through them in a round-robin fashion
         * for each subsequent call. It is ideal for distributing traffic evenly among multiple replicated microservices.
         */
        ROUND_ROBIN("round_robin"),
        /**
         * Attempts to connect to the first resolved backend address and routes all traffic there.
         * <p>
         * This is the default gRPC behavior. The channel tries addresses in the order returned by the name resolver. If a
         * connection succeeds, all RPCs use that subchannel until the connection fails, at which point it tries the next
         * available address.
         */
        PICK_FIRST("pick_first");

        private final String policy;

        LoadBalancingPolicy(String policy) {
            this.policy = policy;
        }

        @Override
        public String toString() {
            return policy;
        }
    }

    /**
     * Exception type for failures with handling the gRPC connection pool, such as failed borrows.
     */
    public static class ChannelException extends RuntimeException {
        private static final long serialVersionUID = 3997386404035396614L;

        public ChannelException(String errorMessage, Throwable err) {
            super(errorMessage, err);
        }
    }
}
