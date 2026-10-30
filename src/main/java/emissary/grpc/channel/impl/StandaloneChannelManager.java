package emissary.grpc.channel.impl;

import emissary.config.Configurator;
import emissary.grpc.channel.ChannelManager;
import emissary.grpc.channel.spi.ChannelManagerProvider;

import io.grpc.ManagedChannel;

/**
 * Manages a single shared {@link ChannelManager}. gRPC channels can handle many simultaneous connections, allowing
 * multiple Emissary threads to share one instance.
 */
public class StandaloneChannelManager extends ChannelManager {
    private final ManagedChannel channel;

    /**
     * Constructs a new gRPC connection factory using the provided host, port, and configuration.
     *
     * @param host gRPC service hostname or DNS target
     * @param port gRPC service port
     * @param configG configuration provider for channel parameters
     * @see ChannelManager
     */
    public StandaloneChannelManager(String host, int port, Configurator configG) {
        super(host, port, configG);
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

    public static final class Provider implements ChannelManagerProvider {
        @Override
        public Class<? extends ChannelManager> type() {
            return StandaloneChannelManager.class;
        }

        @Override
        public ChannelManager build(String host, int port, Configurator configG) {
            return new StandaloneChannelManager(host, port, configG);
        }
    }
}
