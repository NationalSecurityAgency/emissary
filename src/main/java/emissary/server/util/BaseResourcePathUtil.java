package emissary.server.util;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.server.mvc.NavAction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Helper for retrieving the base resource path for templates.
 *
 * The base resource path is loaded once on class initialization, and made available for the main server config and any
 * action classes (and therefore templates) that need it.
 */
public final class BaseResourcePathUtil {
    private static final Logger logger = LoggerFactory.getLogger(NavAction.class);

    private static final String CONFIG_KEY = "BASE_RESOURCE_PATH";
    private static final String DEFAULT_BASE_RESOURCE_PATH = "";
    private static final String BASE_RESOURCE_PATH = initBaseResourcePath();


    private BaseResourcePathUtil() {}

    private static String initBaseResourcePath() {
        try {
            Configurator config = ConfigUtil.getConfigInfo(BaseResourcePathUtil.class);
            return config.findStringEntry(CONFIG_KEY, DEFAULT_BASE_RESOURCE_PATH);
        } catch (IOException e) {
            logger.info("Unable to load configuration for BaseResourcePathUtil; using default values", e);
            return DEFAULT_BASE_RESOURCE_PATH;
        }
    }

    public static String getBaseResourcePath() {
        return BASE_RESOURCE_PATH;
    }
}
