package emissary.grpc.channel.spi;

import emissary.config.Configurator;
import emissary.grpc.channel.ChannelManager;

public interface ChannelManagerProvider {
    Class<? extends ChannelManager> type();

    ChannelManager build(String host, int port, Configurator configG);
}
