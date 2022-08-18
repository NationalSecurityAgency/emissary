package emissary.spi;

/**
 * SPI to initialize the Emissary server.
 *
 * Classes that implement this interface should be listed in
 * src/main/resources/META-INF/services/emissary.spi.InitializationProvider
 */
public interface InitializationProvider {
    void initialize();
}
