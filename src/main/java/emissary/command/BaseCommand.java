package emissary.command;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.joran.util.ConfigurationWatchListUtil;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import emissary.command.converter.PathExistsConverter;
import emissary.command.converter.ProjectBaseConverter;
import emissary.config.ConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseCommand implements EmissaryCommand {

    public static final String COMMAND_NAME = "BaseCommand";

    static final Logger LOG = LoggerFactory.getLogger(BaseCommand.class);

    @Parameter(names = {"-c", "--config"}, description = "config dir, comma separated if multiple, defaults to <projectBase>/config",
            converter = PathExistsConverter.class)
    private Path config;

    public Path getConfig() {
        if (config == null) {
            config = Paths.get(getProjectBase().toAbsolutePath() + "/config");
            if (!Files.exists(config)) {
                throw new RuntimeException("Config dir not configured and " + config.toAbsolutePath().toString() + " does not exist");
            }
        }

        return config;
    }

    @Parameter(names = {"-b", "--projectBase"}, description = "defaults to PROJECT_BASE, errors if different", converter = ProjectBaseConverter.class)
    private Path projectBase = Paths.get(System.getenv("PROJECT_BASE"));

    public Path getProjectBase() {
        return projectBase;
    }

    @Parameter(names = "--logbackConfig", description = "logback configuration file, defaults to <configDir>/logback.xml")
    private String logbackConfig;

    public String getLogbackConfig() {
        if (logbackConfig == null) {
            return getConfig() + "/logback.xml";
        }
        return logbackConfig;
    }

    @Parameter(names = {"--flavor"}, description = "emissary config flavor, comma seperated for multiple")
    private String flavor;

    public String getFlavor() {
        return flavor;
    }

    protected void overrideFlavor(String flavor) {
        logInfo("Overriding current {} {} to {} ", ConfigUtil.CONFIG_FLAVOR_PROPERTY, getFlavor(), flavor);
        this.flavor = flavor;
        System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, getFlavor());
    }

    @Parameter(names = {"--binDir"}, description = "emissary bin dir, defaults to <projectBase>/bin")
    private Path binDir;

    public Path getBinDir() {
        if (binDir == null) {
            return Paths.get(getProjectBase().toAbsolutePath().toString() + "/bin");
        }
        return binDir;
    }

    @Parameter(names = {"--outputRoot"}, description = "root output directory, defaults to <projectBase>/localoutput")
    private Path outputDir;

    public Path getOutputDir() {
        if (outputDir == null) {
            return Paths.get(getProjectBase().toAbsolutePath().toString() + "/localoutput");
        }
        return outputDir;
    }

    @Parameter(names = {"--errorRoot"}, description = "root error directory, defaults to <projectBase>/localerrors")
    private Path errorDir;

    public Path getErrorDir() {
        if (errorDir == null) {
            return Paths.get(getProjectBase().toAbsolutePath().toString() + "/localerror");
        }
        return errorDir;
    }

    @Parameter(names = {"-q", "--quiet"}, description = "hide banner and non essential messages")
    private boolean quiet = false;

    public boolean getQuiet() {
        return quiet;
    }


    @Override
    public void setupCommand() {
        setupConfig();
    }

    public void setupConfig() {
        String pBase = getProjectBase().toAbsolutePath().toString();
        logInfo("{} is set to {} ", ConfigUtil.PROJECT_BASE_ENV, pBase);

        String cfg = getConfig().toAbsolutePath().toString();
        logInfo("Setting {} to {} ", ConfigUtil.CONFIG_DIR_PROPERTY, cfg);
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, cfg);

        String binDir = getBinDir().toAbsolutePath().toString();
        logInfo("Setting {} to {} ", ConfigUtil.CONFIG_BIN_PROPERTY, binDir);
        System.setProperty(ConfigUtil.CONFIG_BIN_PROPERTY, binDir);

        String outputRoot = getOutputDir().toAbsolutePath().toString();
        logInfo("Setting {} to {} ", ConfigUtil.CONFIG_OUTPUT_ROOT_PROPERTY, outputRoot);
        System.setProperty(ConfigUtil.CONFIG_OUTPUT_ROOT_PROPERTY, outputRoot);

        logInfo("Emissary error dir set to {} ", getErrorDir().toAbsolutePath().toString());

        if (getFlavor() != null) {
            logInfo("Setting {} to {} ", ConfigUtil.CONFIG_FLAVOR_PROPERTY, getFlavor());
            System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, getFlavor());
        }
    }

    /**
     * Create a new command and parse the args.
     * <p>
     * Useful for testings. Also calls setup so properties are set
     * 
     * @param clazz the Class of return type class
     * @param args vararg of Strings
     */
    public static <T extends EmissaryCommand> T parse(Class<T> clazz, String... args) throws InstantiationException, IllegalAccessException,
            ClassNotFoundException {
        T cmd = clazz.cast(Class.forName(clazz.getName()).newInstance());
        new JCommander(cmd, args); // sets the parameters by side effect
        cmd.setup();
        return cmd;
    }

    /**
     * Create a new command and parse the args
     * <p>
     * Useful for testings
     * 
     * @param clazz the Class of return type class
     * @param args vararg of Strings
     */
    public static <T extends EmissaryCommand> T parse(Class<T> clazz, List<String> args) throws InstantiationException, IllegalAccessException,
            ClassNotFoundException {
        return parse(clazz, args.toArray(new String[0]));
    }

    /*
     * Try to reinitialize the logback context with the configured file you may have 2 log files if anything logged before
     * we do this. Useful when you are running a server For troubleshooting, looking at the http://localhost:8001/lbConfig
     * when this works, you will see the initial logger and then new one
     */
    public void reinitLogback() {
        // Need to reinit because logback config uses ${emissary.node.name}-${emissary.node.port}
        // which are now set by the commands after logback is initialized

        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        URL logCfg = ConfigurationWatchListUtil.getMainWatchURL(loggerContext);

        if (logCfg != null && (logCfg.toString().endsWith("logback-test.xml") || (logCfg.toString().endsWith("logback-test.groovy")))) {
            // logCfg can be null if Emissary.setupLogbackForConsole is called
            LOG.warn("Not using {}, staying with test config {}", getLogbackConfig(), logCfg.toString());
            doLogbackReinit(loggerContext, logCfg.getPath());
        } else if (Files.exists(Paths.get(getLogbackConfig()))) {
            doLogbackReinit(loggerContext, getLogbackConfig());
        } else {
            LOG.warn("logback configuration not found {}, not reconfiguring logging", getLogbackConfig());
        }

    }

    private void doLogbackReinit(LoggerContext loggerContext, String configFilePath) {
        System.setProperty(ContextInitializer.CONFIG_FILE_PROPERTY, configFilePath);
        loggerContext.reset();
        ContextInitializer newContext = new ContextInitializer(loggerContext);
        try {
            newContext.autoConfig();
        } catch (JoranException e) {
            LOG.error("Problem reconfiguring logback with {}", getLogbackConfig(), e);
        }
    }

    public void logInfo(String format, Object... args) {
        if (getQuiet() == false) {
            LOG.info(format, args);
        }
    }

    public void outputBanner() {
        if (getQuiet() == false) {
            new Banner().dump();
        }
    }
}
