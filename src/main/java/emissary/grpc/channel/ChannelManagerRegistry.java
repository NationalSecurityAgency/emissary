package emissary.grpc.channel;

import emissary.config.Configurator;

import io.grpc.ChannelCredentials;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChannelManagerRegistry {
    private ChannelManagerRegistry() {
        /* This utility class should not be instantiated */
    }

    private static final Map<Class<? extends ChannelManager>, ChannelManagerFactory> REGISTRY = new ConcurrentHashMap<>();

    public static void register(Class<? extends ChannelManager> clz, ChannelManagerFactory factory) {
        REGISTRY.put(clz, factory);
    }

    public static ChannelManager ofSubClass(
            Class<? extends ChannelManager> clz, String host, int port, Configurator configG, ChannelCredentials credentials) {

        ChannelManagerFactory factory = REGISTRY.get(clz);
        if (factory == null) {
            throw new IllegalArgumentException("No factory registered for class: " + clz.getName() +
                    ". Make sure the class is loaded so its static initialization block runs.");
        }
        return factory.create(host, port, configG, credentials);
    }
}
