package emissary.spi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetadataDictionaryInitializationProvider implements InitializationProvider {
    protected static Logger logger = LoggerFactory.getLogger(MetadataDictionaryInitializationProvider.class);

    @Override
    public void initialize() {
        // / Initialize the metadata dictionary
        emissary.core.MetadataDictionary.initialize();
        logger.debug("Initialized the metadata dictionary...");
    }
}
