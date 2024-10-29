package emissary.spi;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.util.JavaCharSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;

public class JavaCharSetInitializationProvider implements InitializationProvider {
    protected static final Logger logger = LoggerFactory.getLogger(JavaCharSetInitializationProvider.class);

    @Override
    public void initialize() {
        if (!JavaCharSet.isInitialized()) {
            try {
                final Configurator config = ConfigUtil.getConfigInfo(JavaCharSet.class);
                JavaCharSet.initialize(config.findStringMatchMap("CHARSET_", true));
            } catch (IOException e) {
                logger.error("Error initializing JavaCharSet: ", e);
                JavaCharSet.initialize(new HashMap<>());
            }
        }
    }
}
