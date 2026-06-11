package emissary.grpc.channel;

import emissary.config.Configurator;

import io.grpc.ChannelCredentials;

@FunctionalInterface
public interface ChannelManagerFactory {
    ChannelManager create(String host, int port, Configurator configG, ChannelCredentials credentials);
}
