package emissary.spi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaCharSetInitializationProvider implements InitializationProvider {
    protected static Logger logger = LoggerFactory.getLogger(JavaCharSetInitializationProvider.class);

    @Override
    public void initialize() {
        // Initialize charset mappings
        emissary.util.JavaCharSetLoader.initialize();
        logger.debug("Initialized charset mapping subsystem...");
    }
}
