package emissary.command;

import emissary.client.EmissaryClient;
import emissary.client.response.ConfigsResponseEntity;
import emissary.server.api.Configs;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Parameters(commandDescription = "Test the configuration for a place")
public class ConfigCommand extends HttpCommand {

    private static final Logger logger = LoggerFactory.getLogger(ConfigCommand.class);
    public static int DEFAULT_PORT = 8001;
    public static String COMMAND_NAME = "config";

    @Parameter(names = {"--place"}, description = "fully-qualified place", arity = 1)
    private String place;

    @Parameter(names = {"--detailed"}, description = "get verbose output when parsing the configs")
    private boolean detailed = false;

    @Parameter(names = {"--offline"}, description = "run the config command in offline mode (useful for local testing)")
    private boolean offline = false;

    @Override
    public String getCommandName() {
        return COMMAND_NAME;
    }

    @Override
    public int getDefaultPort() {
        return DEFAULT_PORT;
    }

    @Override
    public void setupCommand() {
        setupHttp();
        if (offline && StringUtils.isBlank(getFlavor())) {
            // default to standalone mode like servercommand
            overrideFlavor("STANDALONE");
        }
    }

    @Override
    public void run(JCommander jc) {
        setup();
        try {
            ConfigsResponseEntity entity = offline ? getOfflineConfigs() : getConfigs();
            entity.dumpToConsole();
        } catch (Exception e) {
            LOG.error("Problem getting configs: {}", e.getMessage());
        }
    }

    public ConfigsResponseEntity getConfigs() {
        String endpoint = getScheme() + "://" + getHost() + ":" + getPort() + "/api/configuration/" + (detailed ? "detailed/" : "") + this.place;
        LOG.debug("Hitting {}", endpoint);
        EmissaryClient client = new EmissaryClient();
        return client.send(new HttpGet(endpoint)).getContent(ConfigsResponseEntity.class);
    }

    public ConfigsResponseEntity getOfflineConfigs() throws IOException {
        logger.debug("Offline mode");
        return Configs.getConfigsResponse(place, detailed);
    }

}
