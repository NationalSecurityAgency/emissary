package emissary.grpc.channel;

import emissary.config.Configurator;

import io.grpc.ChannelCredentials;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for managing gRPC {@link io.grpc.ManagedChannel} connections.
 * <p>
 * Configuration Keys:
 * <ul>
 * <li>{@code GRPC_CHANNEL_KEEP_ALIVE_MILLIS} - Time to wait before sending a ping on idle, default={@code 60000L}</li>
 * <li>{@code GRPC_CHANNEL_KEEP_ALIVE_TIMEOUT_MILLIS} - Timeout for receiving ping ACKs, default={@code 30000L}</li>
 * <li>{@code GRPC_CHANNEL_KEEP_ALIVE_WITHOUT_CALLS} - Send pings even when no RPCs are active if {@code true},
 * default={@code false}</li>
 * <li>{@code GRPC_CHANNEL_LOAD_BALANCING_POLICY} - gRPC load balancing policy, default={@code "round_robin"}</li>
 * <li>{@code GRPC_CHANNEL_MAX_INBOUND_MESSAGE_BYTE_SIZE} - Max inbound gRPC message size, default={@code 4194304}</li>
 * <li>{@code GRPC_CHANNEL_MAX_INBOUND_METADATA_BYTE_SIZE} - Max inbound gRPC metadata size, default={@code 8192}</li>
 * </ul>
 */
public abstract class ChannelManager implements AutoCloseable {
    public static final String GRPC_CHANNEL_PREFIX = "GRPC_CHANNEL_";
    public static final String KEEP_ALIVE_MILLIS = GRPC_CHANNEL_PREFIX + "KEEP_ALIVE_MILLIS";
    public static final String KEEP_ALIVE_TIMEOUT_MILLIS = GRPC_CHANNEL_PREFIX + "KEEP_ALIVE_TIMEOUT_MILLIS";
    public static final String KEEP_ALIVE_WITHOUT_CALLS = GRPC_CHANNEL_PREFIX + "KEEP_ALIVE_WITHOUT_CALLS";
    public static final String LOAD_BALANCING_POLICY = GRPC_CHANNEL_PREFIX + "LOAD_BALANCING_POLICY";
    public static final String MAX_INBOUND_MESSAGE_BYTE_SIZE = GRPC_CHANNEL_PREFIX + "MAX_INBOUND_MESSAGE_BYTE_SIZE";
    public static final String MAX_INBOUND_METADATA_BYTE_SIZE = GRPC_CHANNEL_PREFIX + "MAX_INBOUND_METADATA_BYTE_SIZE";

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
    protected final ChannelCredentials channelCredentials;

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
        this.target = host + ":" + port; // target may be a host or dns service

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

        this.channelCredentials = createChannelCredentials(configG);
    }

    /**
     * Creates a new {@link ChannelCredentials} object with all security and encryption configurations necessary for
     * establishing a gRPC connection. Base class returns {@link InsecureChannelCredentials}, which asserts that no client
     * identity, authentication, or encryption is to be used. Subclasses should override this method as necessary.
     *
     * @param configG any necessary configurations for creating the credentials object
     * @return gRPC channel credentials
     */
    protected ChannelCredentials createChannelCredentials(Configurator configG) {
        return InsecureChannelCredentials.create();
    }

    public static ChannelManager ofSubClass(String className, String host, int port, Configurator configG) {
        try {
            return ofSubClass(Class.forName(className).asSubclass(ChannelManager.class), host, port, configG);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Cannot find class: " + className, e);
        }
    }

    public static ChannelManager ofSubClass(Class<? extends ChannelManager> clz, String host, int port, Configurator configG) {
        try {
            return clz.getDeclaredConstructor(String.class, int.class, Configurator.class).newInstance(host, port, configG);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to instantiate new ChannelManager: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a new {@link ManagedChannel} instance configured with the current factory settings.
     *
     * @return a new gRPC channel
     */
    protected final ManagedChannel create() {
        return Grpc.newChannelBuilder(this.target, this.channelCredentials)
                .keepAliveTime(this.keepAliveMillis, TimeUnit.MILLISECONDS)
                .keepAliveTimeout(this.keepAliveTimeoutMillis, TimeUnit.MILLISECONDS)
                .keepAliveWithoutCalls(this.keepAliveWithoutCalls)
                .defaultLoadBalancingPolicy(this.loadBalancingPolicy)
                .maxInboundMessageSize(this.maxInboundMessageByteSize)
                .maxInboundMetadataSize(this.maxInboundMetadataByteSize)
                .build();
    }

    public abstract ManagedChannel acquire();

    public abstract void release(ManagedChannel channel);

    public abstract void shutdown(ManagedChannel channel);

    @Override
    public abstract void close();

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public enum LoadBalancingPolicy {
        ROUND_ROBIN("round_robin"), PICK_FIRST("pick_first");

        private final String policy;

        LoadBalancingPolicy(String policy) {
            this.policy = policy;
        }

        @Override
        public String toString() {
            return policy;
        }
    }
}
