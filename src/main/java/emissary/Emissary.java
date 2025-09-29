package emissary;

import emissary.command.AgentsCommand;
import emissary.command.Banner;
import emissary.command.ConfigCommand;
import emissary.command.DirectoryCommand;
import emissary.command.EmissaryCommand;
import emissary.command.EnvCommand;
import emissary.command.FeedCommand;
import emissary.command.HelpCommand;
import emissary.command.PeersCommand;
import emissary.command.PoolCommand;
import emissary.command.ServerCommand;
import emissary.command.TopologyCommand;
import emissary.command.VersionCommand;
import emissary.util.GitRepositoryState;
import emissary.util.io.LoggingPrintStream;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.MissingParameterException;
import picocli.CommandLine.UnmatchedArgumentException;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

/**
 * Main entry point of the jar file
 * 
 * Parses command line arguments and delegates commands
 */
@SuppressWarnings("ImmutableMemberCollection")
public class Emissary {
    private static final Logger LOG = LoggerFactory.getLogger(Emissary.class);

    private final CommandLine cli = new CommandLine(EmissaryCommand.class);
    private final Map<String, EmissaryCommand> commands;

    public static final Map<String, EmissaryCommand> EMISSARY_COMMANDS;

    private boolean bannerDumped = false;

    static {
        List<Class<? extends EmissaryCommand>> commandClasses =
                Arrays.asList(ServerCommand.class, HelpCommand.class, TopologyCommand.class, FeedCommand.class,
                        AgentsCommand.class, PoolCommand.class, VersionCommand.class, EnvCommand.class,
                        PeersCommand.class, ConfigCommand.class, DirectoryCommand.class);
        Map<String, EmissaryCommand> staticCopy = new HashMap<>();
        for (Class<? extends EmissaryCommand> commandClass : commandClasses) {
            try {
                EmissaryCommand command = commandClass.getDeclaredConstructor().newInstance();
                staticCopy.put(command.getCommandName(), command);
            } catch (ReflectiveOperationException e) {
                LOG.error("Couldn't make EMISSARY_COMMANDS", e);
                System.exit(1);
            }
        }
        EMISSARY_COMMANDS = Collections.unmodifiableMap(staticCopy);
    }

    @VisibleForTesting
    protected CommandLine getCommand() {
        return cli;
    }

    protected Emissary() {
        this(EMISSARY_COMMANDS);
    }

    protected Emissary(Map<String, EmissaryCommand> cmds) {
        commands = Collections.unmodifiableMap(cmds);
        // sort by command name and then add to Picocli
        for (String key : new TreeSet<>(commands.keySet())) {
            cli.addSubcommand(key, commands.get(key));
        }
    }

    protected void execute(String[] args) {
        reconfigureLogHook(); // so we can capture everything for test, like the verbose output
        String shouldSetVerbose = System.getProperty("set.picocli.debug");
        if (shouldSetVerbose != null && shouldSetVerbose.equals("true")) {
            CommandLine.tracer().setLevel(CommandLine.TraceLevel.INFO);
        }
        try {
            cli.parseArgs(args);
            List<String> commandNames = cli.getParseResult().originalArgs();
            if (commandNames.isEmpty()) {
                dumpBanner();
                LOG.error("One command is required");
                HelpCommand.dumpCommands(cli);
                exit(1);
            }
            String commandName = commandNames.get(0);
            EmissaryCommand cmd = commands.get(commandName);
            dumpBanner(cmd);
            if (Arrays.asList(args).contains(ServerCommand.COMMAND_NAME)) {
                dumpVersionInfo();
            }
            cmd.run(cli);
            // don't exit(0) here or things like server will not continue to run
        } catch (MissingParameterException e) {
            dumpBanner();
            LOG.error(e.getMessage());
            HelpCommand.dumpHelp(cli, args[0]);
            exit(1);
        } catch (UnmatchedArgumentException e) {
            dumpBanner();
            LOG.error("Undefined command: {}", Arrays.toString(args));
            LOG.error("\t {}", e.getLocalizedMessage());
            HelpCommand.dumpCommands(cli);
            exit(1);
        } catch (RuntimeException e) {
            dumpBanner();
            LOG.error("Command threw an exception: {}", Arrays.toString(args), e);
            exit(1);
        }
    }

    private void dumpBanner(@Nullable EmissaryCommand cmd) {
        if (!bannerDumped) {
            bannerDumped = true;
            if (cmd == null) {
                new Banner().dump();
            } else {
                cmd.outputBanner();
            }
            setupLogging();
        }
    }

    private void dumpBanner() {
        dumpBanner(null);
    }

    protected void dumpVersionInfo() {
        if (LOG.isInfoEnabled()) {
            LOG.info(GitRepositoryState.dumpVersionInfo(GitRepositoryState.getRepositoryState(), "Emissary"));
        }
    }

    @VisibleForTesting
    protected void reconfigureLogHook() {
        // overridden in EmissaryTest
    }

    @VisibleForTesting
    // so we can stop exiting long enough to look at the return code
    protected void exit(int retCode) {
        System.exit(retCode);
    }

    public static void main(String[] args) {
        new Emissary().execute(args);
    }

    protected void setupLogging() {
        redirectStdOutStdErr();
        setupLogbackForConsole();
        // hook so we can capture stuff in tests
        reconfigureLogHook();
    }

    /*
     * Modify the logback stuff, about to run a command
     * 
     * Reinit with a config file if running something like a server where you want the expanded format,
     */
    public static LoggerContext setupLogbackForConsole() {
        // So it looks better when commands are run
        ch.qos.logback.classic.Logger root =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.detachAndStopAllAppenders();
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        lc.reset();

        PatternLayoutEncoder ple = new PatternLayoutEncoder();
        ple.setPattern("%msg%n");
        ple.setContext(lc);
        ple.start();

        ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
        consoleAppender.setEncoder(ple);
        consoleAppender.setContext(lc);
        consoleAppender.start();

        root.addAppender(consoleAppender);
        root.setLevel(Level.INFO);
        root.setAdditive(false);
        return lc;
    }

    @SuppressWarnings("SystemOut")
    static void redirectStdOutStdErr() {
        // no need for sysout-over-slf4j anymore, which as need for any calls, like jni, which only
        // output to stdout/stderr Last none logback message
        LOG.trace("Redefining stdout so logback and capture the output");
        System.setOut(new LoggingPrintStream(System.out, "STDOUT", LOG, org.slf4j.event.Level.INFO, 30, TimeUnit.SECONDS));

        LOG.trace("Redefining stderr so logback and capture the output");
        System.setErr(new LoggingPrintStream(System.err, "STDERR", LOG, org.slf4j.event.Level.ERROR, 30, TimeUnit.SECONDS));
    }
}
