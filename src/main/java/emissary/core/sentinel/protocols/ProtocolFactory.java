package emissary.core.sentinel.protocols;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.Factory;

import java.io.IOException;

public class ProtocolFactory {

    private static final String DEFAULT_PROTOCOL_CLASS = "emissary.core.sentinel.protocols.AgentProtocol";

    public static Protocol get(String conf) {
        try {
            Configurator config = ConfigUtil.getConfigInfo(conf);
            String protocolType = config.findStringEntry("PROTOCOL_CLASS", DEFAULT_PROTOCOL_CLASS);
            return (Protocol) Factory.create(protocolType, config);
        } catch (IOException e) {
            throw new IllegalArgumentException("There was a problem with the supplied config [" + conf + "]", e);
        }
    }

    private ProtocolFactory() {}
}
