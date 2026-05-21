package emissary.server.mvc;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.server.EmissaryServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Helper for calculating the base resource path for templates.
 *
 * The base resource path is loaded once on class initialization from the main server config.
 */
public final class BaseResourcePathUtil {

    private static final Logger LOG = LoggerFactory.getLogger(BaseResourcePathUtil.class);
    private static final String CONFIG_KEY = "BASE_RESOURCE_PATH";
    private static final String DEFAULT_BASE_RESOURCE_PATH = "";
    private static final String BASE_RESOURCE_PATH = initBaseResourcePath();

    private BaseResourcePathUtil() {
        // utility class
    }

    private static String initBaseResourcePath() {
        try {
            Configurator config = ConfigUtil.getConfigInfo(EmissaryServer.class);
            return config.findStringEntry(CONFIG_KEY, DEFAULT_BASE_RESOURCE_PATH);
        } catch (IOException e) {
            LOG.debug("Unable to read {} config, defaulting base resource path to empty", BaseResourcePathUtil.class.getSimpleName(), e);
            return DEFAULT_BASE_RESOURCE_PATH;
        }
    }

    public static String getBaseResourcePath() {
        return BASE_RESOURCE_PATH;
    }
}
