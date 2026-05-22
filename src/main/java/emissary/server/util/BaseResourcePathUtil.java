package emissary.server.util;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.server.mvc.NavAction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Helper for retrieving the base resource path for templates.
 *
 * The base resource path is loaded once on class initialization, and made available for the main server config and any
 * action classes (and therefore templates) that need it.
 */
public final class BaseResourcePathUtil {
    private static final Logger logger = LoggerFactory.getLogger(NavAction.class);

    private static final Pattern VALID_BASE_RESOURCE_PATH = Pattern.compile("^(?:\\/[\\w-]+)*$");

    private static final String CONFIG_KEY = "BASE_RESOURCE_PATH";
    private static final String DEFAULT_BASE_RESOURCE_PATH = "";
    private static final String BASE_RESOURCE_PATH = initBaseResourcePath();

    private BaseResourcePathUtil() {}

    private static String initBaseResourcePath() {
        try {
            Configurator config = ConfigUtil.getConfigInfo(BaseResourcePathUtil.class);
            String baseResourcePath = config.findStringEntry(CONFIG_KEY, DEFAULT_BASE_RESOURCE_PATH);
            if (!VALID_BASE_RESOURCE_PATH.matcher(baseResourcePath).matches()) {
                logger.info("Invalid base resource path format; using default values");
                return DEFAULT_BASE_RESOURCE_PATH;
            }
            return baseResourcePath;
        } catch (IOException e) {
            logger.info("Unable to load configuration for BaseResourcePathUtil; using default values", e);
            return DEFAULT_BASE_RESOURCE_PATH;
        }
    }

    public static String getBaseResourcePath() {
        return BASE_RESOURCE_PATH;
    }

    /**
     * Validates if the given path matches the valid base resource path pattern. Living in its own function to make running
     * tests against it easier.
     *
     * @param path the path to validate
     * @return true if the path is valid, false otherwise
     */
    public static boolean isValidBaseResourcePath(String path) {
        return VALID_BASE_RESOURCE_PATH.matcher(path).matches();
    }
}
