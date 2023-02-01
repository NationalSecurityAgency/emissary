package emissary.spi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ServiceLoader;

/**
 * Load SPI implementations to support initialization of the Emissary server.
 *
 * <pre>
 * - Loading is delegated to the {@link java.util.ServiceLoader}.
 * - Configured via src/main/resources/META-INF/services/emissary.spi.InitializationProvider
 * </pre>
 */
public class SPILoader {

    private static final Logger logger = LoggerFactory.getLogger(SPILoader.class);

    public static void load() {
        ServiceLoader<InitializationProvider> loader = ServiceLoader.load(InitializationProvider.class);

        loader.forEach(provider -> {
            provider.initialize();
            logger.info("Initialized {}", provider.getClass().getName());
        });
    }
}
