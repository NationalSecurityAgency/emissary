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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
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
    protected static final Pattern SENSITIVE_KEY = Pattern
            .compile("(?i).*(PASSWORD|PASSWD|SECRET|TOKEN|CREDENTIAL|API[_-]?KEY|PRIVATE[_-]?KEY|SIGNING[_-]?KEY|ENCRYPTION[_-]?KEY).*");
    protected static final String REDACTED = "***REDACTED***";
    protected static final String ADMIN_ROLE = "admin";

    @GET
    @Path("/configuration/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response config(@PathParam("name") String name, @QueryParam("includeSensitive") boolean includeSensitive,
            @Context SecurityContext securityContext) {
        return getConfigs(name, false, includeSensitive && isAdmin(securityContext));
    }

    @GET
    @Path("/configuration/detailed/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response configDetail(@PathParam("name") String name, @QueryParam("includeSensitive") boolean includeSensitive,
            @Context SecurityContext securityContext) {
        return getConfigs(name, true, includeSensitive && isAdmin(securityContext));
    }

    /**
     * Get the configuration details for the specified class.
     *
     * @param name the fully qualified class name or config file
     * @param detailed true for verbose mode, false otherwise
     * @return the config response object
     */
    public Response getConfigs(String name, boolean detailed) {
        return getConfigs(name, detailed, false);
    }

    /**
     * Get the configuration details for the specified class.
     *
     * @param name the fully qualified class name or config file
     * @param detailed true for verbose mode, false otherwise
     * @param includeSensitive true to return sensitive values (requires the admin role at the HTTP layer)
     * @return the config response object
     */
    public Response getConfigs(String name, boolean detailed, boolean includeSensitive) {
        try {
            return Response.ok().entity(getConfigsResponse(name, detailed, includeSensitive)).build();
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
        return getConfigsResponse(name, detailed, true);
    }

    /**
     * Get the configuration details for the specified class.
     *
     * @param name the fully qualified class name or config file
     * @param detailed true for verbose mode, false otherwise
     * @param includeSensitive true to return sensitive values, false to redact them
     * @return the config response object
     */
    public static ConfigsResponseEntity getConfigsResponse(String name, boolean detailed, boolean includeSensitive) throws IOException {
        final String cfg = validate(name);
        return detailed ? getEmissaryConfigDetailed(cfg, includeSensitive) : getEmissaryConfig(cfg, includeSensitive);
    }

    /**
     * Get the key/value pairs for a configuration file. Returns one output for all combined flavored configs.
     *
     * @param cfg the config file
     * @return the config response object
     */
    public static ConfigsResponseEntity getEmissaryConfig(final String cfg) throws IOException {
        return getEmissaryConfig(cfg, true);
    }

    /**
     * Get the key/value pairs for a configuration file. Returns one output for all combined flavored configs.
     *
     * @param cfg the config file
     * @param includeSensitive true to return sensitive values, false to redact them
     * @return the config response object
     */
    public static ConfigsResponseEntity getEmissaryConfig(final String cfg, boolean includeSensitive) throws IOException {
        ConfigList list = new ConfigList();
        list.addConfig(new Config(ConfigUtil.getFlavors(), combineConfigs(cfg, ConfigUtil.addFlavors(cfg)),
                normalizeEntries(ConfigUtil.getConfigInfo(cfg), includeSensitive)));
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
        return getEmissaryConfigDetailed(cfg, true);
    }

    /**
     * Get detailed output for a configuration file. Returns flavored config files one at a time and then the combined
     * output for all flavors.
     *
     * @param cfg the config file
     * @param includeSensitive true to return sensitive values, false to redact them
     * @return the config response object
     */
    public static ConfigsResponseEntity getEmissaryConfigDetailed(final String cfg, boolean includeSensitive) throws IOException {
        ConfigList detailed = new ConfigList();
        List<String> flavors = ConfigUtil.getFlavors();

        // default config
        detailed.addConfig(new Config(Collections.emptyList(), Collections.singletonList(cfg),
                normalizeEntries(new ServiceConfigGuide(ConfigUtil.getConfigStream(cfg), cfg), includeSensitive)));

        // flavored configs
        String[] flavoredCfgs = ConfigUtil.addFlavors(cfg);
        for (final String flavoredName : flavoredCfgs) {
            String flavor = StringUtils.substringBeforeLast(StringUtils.substringAfterLast(flavoredName, "-"), ".");
            addDetail(detailed, Collections.singletonList(flavor), Collections.singletonList(flavoredName), flavoredName,
                    includeSensitive);
        }

        // all together now - same output as getEmissaryConfig
        addDetail(detailed, flavors, combineConfigs(cfg, flavoredCfgs), cfg, includeSensitive);

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
     * @param includeSensitive true to return sensitive values, false to redact them
     * @return a sorted, distinct list of properties
     */
    protected static List<ConfigEntry> normalizeEntries(Configurator cfg, boolean includeSensitive) {
        return cfg.getEntries().stream()
                .sorted(Comparator.comparingInt((ConfigEntry ce) -> RESERVED_SERVICE_CONFIG_KEYS.contains(ce.getKey()) ? 0 : 1)
                        .thenComparing(ConfigEntry::getKey)
                        .thenComparing(ConfigEntry::getValue))
                .map(entry -> redact(entry, includeSensitive))
                .distinct()
                .collect(Collectors.toList());
    }

    private static ConfigEntry redact(final ConfigEntry entry, final boolean includeSensitive) {
        if (includeSensitive || !SENSITIVE_KEY.matcher(entry.getKey()).matches()) {
            return entry;
        }
        return new ConfigEntry(entry.getKey(), REDACTED);
    }

    private static boolean isAdmin(final SecurityContext securityContext) {
        return securityContext != null && securityContext.isUserInRole(ADMIN_ROLE);
    }

    /**
     * Add the configuration properties to the response object
     *
     * @param detailed the config list
     * @param flavors the flavors used to generate properties
     * @param configs the list of configs used to generate properties
     * @param cfgFile the config file
     * @param includeSensitive true to return sensitive values, false to redact them
     */
    protected static void addDetail(ConfigList detailed, List<String> flavors, List<String> configs, String cfgFile,
            boolean includeSensitive) {
        try {
            detailed.addConfig(new Config(flavors, configs,
                    normalizeEntries(ConfigUtil.getConfigInfo(cfgFile), includeSensitive)));
        } catch (IOException e) {
            detailed.addConfig(new Config(flavors, configs, Collections.emptyList()));
        }
    }

}
