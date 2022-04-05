package emissary.spi;

import emissary.directory.EmissaryNode;

/**
 * SPI to initialize the Emissary server. Classes that implement this interface and are listed in
 * src/main/resources/META-INF/services/emissary.spi.InitializationProvider will be delegated to by the
 * {@link java.util.ServiceLoader} in {@link EmissaryNode#configureEmissaryServer()}
 */
public interface InitializationProvider {
    void initialize();
}
