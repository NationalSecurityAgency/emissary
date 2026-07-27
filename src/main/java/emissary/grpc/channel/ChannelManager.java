package emissary.grpc.channel;

import emissary.config.Configurator;

import io.grpc.ManagedChannel;
import org.apache.commons.pool2.ObjectPool;

/**
 * Wrapper for an {@link ObjectPool} created by a {@link ChannelPoolFactory}.
 */
public class ChannelManager implements AutoCloseable {
    private final ObjectPool<ManagedChannel> channelPool;
    private final String host;
    private final int port;

    public ChannelManager(String host, int port, Configurator configG) {
        this.channelPool = new ChannelPoolFactory(host, port, configG).newConnectionPool();
        this.host = host;
        this.port = port;
    }

    public ManagedChannel acquire() {
        return ChannelPoolFactory.acquireChannel(channelPool);
    }

    public void release(ManagedChannel channel) {
        ChannelPoolFactory.returnChannel(channel, channelPool);
    }

    public void shutdown(ManagedChannel channel) {
        ChannelPoolFactory.invalidateChannel(channel, channelPool);
    }

    @Override
    public void close() {
        channelPool.close();
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
