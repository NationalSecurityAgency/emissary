package emissary.server.api;

import emissary.client.response.Config;
import emissary.client.response.ConfigList;
import emissary.client.response.ConfigsResponseEntity;
import emissary.config.ConfigEntry;
import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;

import com.google.common.collect.Lists;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static emissary.config.ConfigUtil.CONFIG_FILE_ENDING;
import static emissary.core.constants.Configurations.RESERVED_SERVICE_CONFIG_KEYS;

@Path("")
// context is /api
public class Configs {

    protected static final Pattern VALID_CONFIG_NAME = Pattern.compile("^[a-zA-Z0-9._-]+$");

    @GET
    @Path("/configuration/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response config(@PathParam("name") String name) {
        return getConfigs(name, false);
    }

    @GET
    @Path("/configuration/detailed/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response configDetail(@PathParam("name") String name) {
        return getConfigs(name, true);
    }

    /**
     * Get the configuration details for the specified class.
     *
     * @param name the fully qualified class name or config file
     * @param detailed true for verbose mode, false otherwise
     * @return the config response object
     */
    public Response getConfigs(String name, boolean detailed) {
        try {
            return Response.ok().entity(getConfigsResponse(name, detailed)).build();
        } catch (IOException e) {
            ConfigsResponseEntity cre = new ConfigsResponseEntity();
            cre.addError(e.getMessage());
            return Response.serverError().entity(cre).build();
        }
    }

    /**
     * Get the configuration details for the specified class.
     *
     * @param name the fully qualified class name or config file
     * @param detailed true for verbose mode, false otherwise
     * @return the config response object
     */
    public static ConfigsResponseEntity getConfigsResponse(String name, boolean detailed) throws IOException {
        final String cfg = validate(name);
        return detailed ? getEmissaryConfigDetailed(cfg) : getEmissaryConfig(cfg);
    }

    /**
     * Get the key/value pairs for a configuration file. Returns one output for all combined flavored configs.
     *
     * @param cfg the config file
     * @return the config response object
     */
    public static ConfigsResponseEntity getEmissaryConfig(final String cfg) throws IOException {
        ConfigList list = new ConfigList();
        list.addConfig(new Config(ConfigUtil.getFlavors(), combineConfigs(cfg, ConfigUtil.addFlavors(cfg)),
                normalizeEntries(ConfigUtil.getConfigInfo(cfg))));
        return new ConfigsResponseEntity(list);
    }

    /**
     * Get detailed output for a configuration file. Returns flavored config files one at a time and then the combined
     * output for all flavors.
     *
     * @param cfg the config file
     * @return the config response object
     */
    public static ConfigsResponseEntity getEmissaryConfigDetailed(final String cfg) throws IOException {
        ConfigList detailed = new ConfigList();
        List<String> flavors = ConfigUtil.getFlavors();

        // default config
        detailed.addConfig(new Config(Collections.emptyList(), Collections.singletonList(cfg),
                normalizeEntries(new ServiceConfigGuide(ConfigUtil.getConfigStream(cfg), cfg))));

        // flavored configs
        String[] flavoredCfgs = ConfigUtil.addFlavors(cfg);
        for (final String flavoredName : flavoredCfgs) {
            String flavor = StringUtils.substringBeforeLast(StringUtils.substringAfterLast(flavoredName, "-"), ".");
            addDetail(detailed, Collections.singletonList(flavor), Collections.singletonList(flavoredName), flavoredName);
        }

        // all together now - same output as getEmissaryConfig
        addDetail(detailed, flavors, combineConfigs(cfg, flavoredCfgs), cfg);

        return new ConfigsResponseEntity(detailed);
    }

    /**
     * Validate the provided class name
     *
     * @param config the full qualified class name or config file
     * @return the default configuration file for the class
     */
    protected static String validate(String config) {
        if (!VALID_CONFIG_NAME.matcher(config).matches() || config.contains("..") || config.endsWith(".")) {
            throw new IllegalArgumentException("Invalid config name: " + config);
        }
        return Strings.CS.appendIfMissing(config.trim(), CONFIG_FILE_ENDING);
    }

    /**
     * Combine all configs into a single list
     *
     * @param cfg the default config
     * @param flavoredConfigs the flavored configs
     * @return a combined list of flavored configs
     */
    protected static List<String> combineConfigs(final String cfg, String[] flavoredConfigs) {
        List<String> configs = Lists.newArrayList(cfg);
        configs.addAll(Arrays.asList(flavoredConfigs));
        return configs;
    }

    /**
     * Normalize the output generated from the merge of key/value pairs (sort, distinct, etc.)
     *
     * @param cfg the configured properties
     * @return a sorted, distinct list of properties
     */
    protected static List<ConfigEntry> normalizeEntries(Configurator cfg) {
        return cfg.getEntries().stream()
                .sorted(Comparator.comparingInt((ConfigEntry ce) -> RESERVED_SERVICE_CONFIG_KEYS.contains(ce.getKey()) ? 0 : 1)
                        .thenComparing(ConfigEntry::getKey)
                        .thenComparing(ConfigEntry::getValue))
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Add the configuration properties to the response object
     *
     * @param detailed the config list
     * @param flavors the flavors used to generate properties
     * @param configs the list of configs used to generate properties
     * @param cfgFile the config file
     */
    protected static void addDetail(ConfigList detailed, List<String> flavors, List<String> configs, String cfgFile) {
        try {
            detailed.addConfig(new Config(flavors, configs, normalizeEntries(ConfigUtil.getConfigInfo(cfgFile))));
        } catch (IOException e) {
            detailed.addConfig(new Config(flavors, configs, Collections.emptyList()));
        }
    }

}
