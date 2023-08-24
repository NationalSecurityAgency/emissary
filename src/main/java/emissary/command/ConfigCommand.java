package emissary.command;

import emissary.client.EmissaryClient;
import emissary.client.response.ConfigsResponseEntity;
import emissary.server.api.Configs;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

import java.io.IOException;

@Command(description = "Test the configuration for a place", subcommands = {HelpCommand.class})
public class ConfigCommand extends HttpCommand {
    @Spec
    private CommandSpec spec;
    private static final Logger logger = LoggerFactory.getLogger(ConfigCommand.class);
    public static int DEFAULT_PORT = 8001;
    public static String COMMAND_NAME = "config";

    @Option(names = {"--place"}, description = "fully-qualified place", arity = "1", required = true)
    private String place;

    @Option(names = {"--detailed"}, description = "get verbose output when parsing the configs\nDefault: ${DEFAULT-VALUE}")
    private boolean detailed = false;

    @Option(names = {"--offline"}, description = "run the config command in offline mode (useful for local testing)\nDefault: ${DEFAULT-VALUE}")
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

        if (!offline && StringUtils.isNotBlank(getFlavor())) {
            throw new ParameterException(spec.commandLine(), "--flavor can only be specified in offline mode");
        }

        if (offline && StringUtils.isBlank(getFlavor())) {
            // default to standalone mode like servercommand
            overrideFlavor("STANDALONE");
        }
    }

    @Override
    public void run(CommandLine c) {
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
