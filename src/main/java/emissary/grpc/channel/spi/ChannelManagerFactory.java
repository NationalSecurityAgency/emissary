package emissary.grpc.channel.spi;

import emissary.config.Configurator;
import emissary.grpc.channel.ChannelManager;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

public class ChannelManagerFactory {
    public static final String CHANNEL_MANAGER_CLASS_NAME = ChannelManager.GRPC_CHANNEL_PREFIX + "MANAGER_CLASS_NAME";

    private final ChannelManagerConstructor constructor;

    public ChannelManagerFactory(Configurator configG, String defaultClassName) {
        this(configG.findStringEntry(CHANNEL_MANAGER_CLASS_NAME, defaultClassName));
    }

    private ChannelManagerFactory(String channelManagerClassName) {
        this(channelManagerClassName, ServiceLoader.load(ChannelManagerProvider.class));
    }

    private ChannelManagerFactory(String channelManagerClassName, Iterable<ChannelManagerProvider> loadedProviders) {
        Map<String, ChannelManagerConstructor> factories = new HashMap<>();
        for (ChannelManagerProvider provider : loadedProviders) {
            String providerType = provider.type().getName();
            if (factories.containsKey(providerType)) {
                throw new IllegalStateException("Duplicate Provider name: " + providerType);
            }
            factories.put(providerType, provider::build);
        }
        if (!factories.containsKey(channelManagerClassName)) {
            throw new IllegalArgumentException("No Provider registered for: " + channelManagerClassName);
        }
        constructor = factories.get(channelManagerClassName);
    }

    public ChannelManager build(String host, int port, Configurator configG) {
        return constructor.build(host, port, configG);
    }

    @FunctionalInterface
    private interface ChannelManagerConstructor {
        ChannelManager build(String host, int port, Configurator configG);
    }
}
