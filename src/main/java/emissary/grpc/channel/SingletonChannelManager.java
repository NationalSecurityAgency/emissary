package emissary.grpc.channel;

import emissary.config.Configurator;

import io.grpc.ChannelCredentials;
import io.grpc.ManagedChannel;

/**
 * Manages a single shared {@link ChannelManager}. gRPC channels can handle many simultaneous connections, allowing
 * multiple Emissary threads to share one instance.
 */
public class SingletonChannelManager extends ChannelManager {
    private final ManagedChannel channel;

    static {
        ChannelManagerRegistry.register(SingletonChannelManager.class, SingletonChannelManager::new);
    }

    /**
     * Constructs a new gRPC connection factory using the provided host, port, and configuration.
     *
     * @param host gRPC service hostname or DNS target
     * @param port gRPC service port
     * @param configG configuration provider for channel parameters
     * @see ChannelManager
     */
    public SingletonChannelManager(String host, int port, Configurator configG, ChannelCredentials credentials) {
        super(host, port, configG, credentials);
        channel = create();
    }

    @Override
    public ManagedChannel acquire() {
        return channel;
    }

    @Override
    public void release(ManagedChannel channel) {
        /* No-op */
    }

    @Override
    public void shutdown(ManagedChannel channel) {
        channel.shutdownNow();
    }

    @Override
    public void close() {
        if (!channel.isShutdown()) {
            shutdown(channel);
        }
    }
}
