package emissary.server.util;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.EmissaryRuntimeException;

import java.io.IOException;

/**
 * Helper for calculating the base resource path for templates.
 *
 * The base resource path is loaded once on class initialization, and made available for the main server config and any
 * action classes (and therefore templates) that need it.
 */
public final class BaseResourcePathUtil {
    private static final String CONFIG_KEY = "BASE_RESOURCE_PATH";
    private static final String DEFAULT_BASE_RESOURCE_PATH = "";
    private static final String BASE_RESOURCE_PATH = initBaseResourcePath();

    private BaseResourcePathUtil() {}

    private static String initBaseResourcePath() {
        try {
            Configurator config = ConfigUtil.getConfigInfo(BaseResourcePathUtil.class);
            return config.findStringEntry(CONFIG_KEY, DEFAULT_BASE_RESOURCE_PATH);
        } catch (IOException e) {
            throw new EmissaryRuntimeException("Unable to load configuration for BaseResourcePathUtil", e);
        }
    }

    public static String getBaseResourcePath() {
        return BASE_RESOURCE_PATH;
    }
}
