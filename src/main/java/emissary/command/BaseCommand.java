package emissary.command;

import emissary.command.converter.PathExistsConverter;
import emissary.command.converter.ProjectBaseConverter;
import emissary.config.ConfigUtil;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.joran.util.ConfigurationWatchListUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Command(description = "Base Command")
public abstract class BaseCommand implements EmissaryCommand {
    static final Logger LOG = LoggerFactory.getLogger(BaseCommand.class);

    public static final String COMMAND_NAME = "BaseCommand";

    @Option(names = {"-c", "--config"}, description = "config dir, comma separated if multiple, defaults to <projectBase>/config",
            converter = PathExistsConverter.class)
    private Path config;

    @Option(names = {"-b", "--projectBase"}, description = "defaults to PROJECT_BASE, errors if different", converter = ProjectBaseConverter.class)
    private Path projectBase = Paths.get(System.getenv("PROJECT_BASE"));

    @Option(names = "--logbackConfig", description = "logback configuration file, defaults to <configDir>/logback.xml")
    private String logbackConfig;

    @Option(names = {"--flavor"}, description = "emissary config flavor, comma separated for multiple")
    private String flavor;

    @Option(names = {"--binDir"}, description = "emissary bin dir, defaults to <projectBase>/bin")
    private Path binDir;

    @Option(names = {"--outputRoot"}, description = "root output directory, defaults to <projectBase>/localoutput")
    private Path outputDir;

    @Option(names = {"--errorRoot"}, description = "root error directory, defaults to <projectBase>/localerrors")
    private Path errorDir;

    @Option(names = {"-q", "--quiet"}, description = "hide banner and non essential messages")
    private boolean quiet = false;

    public Path getConfig() {
        if (config == null) {
            config = getProjectBase().toAbsolutePath().resolve("config");
            if (!Files.exists(config)) {
                throw new IllegalArgumentException("Config dir not configured and " + config.toAbsolutePath() + " does not exist");
            }
        }

        return config;
    }

    public Path getProjectBase() {
        return projectBase;
    }

    public String getLogbackConfig() {
        if (logbackConfig == null) {
            return getConfig() + "/logback.xml";
        }
        return logbackConfig;
    }

    public String getFlavor() {
        return flavor;
    }

    protected void overrideFlavor(String flavor) {
        logInfo("Overriding current {} {} to {} ", ConfigUtil.CONFIG_FLAVOR_PROPERTY, getFlavor(), flavor);
        this.flavor = flavor;
        System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, getFlavor());
    }

    public Path getBinDir() {
        if (binDir == null) {
            return getProjectBase().toAbsolutePath().resolve("bin");
        }
        return binDir;
    }

    public Path getOutputDir() {
        if (outputDir == null) {
            return getProjectBase().toAbsolutePath().resolve("localoutput");
        }
        return outputDir;
    }

    public Path getErrorDir() {
        if (errorDir == null) {
            return getProjectBase().toAbsolutePath().resolve("localerror");
        }
        return errorDir;
    }

    public boolean getQuiet() {
        return quiet;
    }

    public boolean isVerbose() {
        return !getQuiet();
    }

    @Override
    public void setupCommand() {
        setupConfig();
    }

    public void setupConfig() {
        logInfo("{} is set to {} ", ConfigUtil.PROJECT_BASE_ENV, getProjectBase().toAbsolutePath().toString());
        setSystemProperty(ConfigUtil.CONFIG_DIR_PROPERTY, getConfig().toAbsolutePath().toString());
        setSystemProperty(ConfigUtil.CONFIG_BIN_PROPERTY, getBinDir().toAbsolutePath().toString());
        setSystemProperty(ConfigUtil.CONFIG_OUTPUT_ROOT_PROPERTY, getOutputDir().toAbsolutePath().toString());
        logInfo("Emissary error dir set to {} ", getErrorDir().toAbsolutePath().toString());
        if (getFlavor() != null) {
            setSystemProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, getFlavor());
        }
    }

    protected void setSystemProperty(String key, String value) {
        logInfo("Setting {} to {} ", key, value);
        System.setProperty(key, value);
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
        // new JCommander(cmd, args); // sets the parameters by side effect
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        PrintStream old = System.out;
        System.setOut(ps);

        CommandLine cl = new CommandLine(cmd);
        int code = cl.execute(args);

        System.out.flush();
        System.setOut(old);
        if (code == 2) {
            throw new ParameterException(cl, baos.toString());
        }
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
     *
     * Need to reinit because logback config uses ${emissary.node.name}-${emissary.node.port} which are now set by the
     * commands after logback is initialized
     */
    public void reinitLogback() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        URL logCfg = ConfigurationWatchListUtil.getMainWatchURL(loggerContext);

        if (logCfg != null && (logCfg.toString().endsWith("logback-test.xml") || (logCfg.toString().endsWith("logback-test.groovy")))) {
            // logCfg can be null if Emissary.setupLogbackForConsole is called
            LOG.warn("Not using {}, staying with test config {}", getLogbackConfig(), logCfg);
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
        if (isVerbose()) {
            LOG.info(format, args);
        }
    }

    @Override
    public void outputBanner() {
        if (isVerbose()) {
            new Banner().dump();
        }
    }
}
