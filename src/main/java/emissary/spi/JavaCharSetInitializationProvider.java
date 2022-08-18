package emissary.spi;

import java.io.IOException;
import java.util.HashMap;

import emissary.util.JavaCharSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaCharSetInitializationProvider implements InitializationProvider {
    protected static Logger logger = LoggerFactory.getLogger(JavaCharSetInitializationProvider.class);

    @Override
    public void initialize() {
        if (!JavaCharSet.isInitialized()) {
            try {
                final emissary.config.Configurator config = emissary.config.ConfigUtil.getConfigInfo(JavaCharSet.class);
                JavaCharSet.initialize(config.findStringMatchMap("CHARSET_", true));
            } catch (IOException e) {
                logger.error("Error initializing JavaCharSet: ", e);
                JavaCharSet.initialize(new HashMap<>());
            }
        }
    }
}
