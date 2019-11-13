package emissary;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.MissingCommandException;
import com.google.common.annotations.VisibleForTesting;
import emissary.command.AgentsCommand;
import emissary.command.Banner;
import emissary.command.EmissaryCommand;
import emissary.command.EnvCommand;
import emissary.command.FeedCommand;
import emissary.command.HelpCommand;
import emissary.command.PeersCommand;
import emissary.command.PoolCommand;
import emissary.command.RunCommand;
import emissary.command.ServerCommand;
import emissary.command.StopCommand;
import emissary.command.TopologyCommand;
import emissary.command.VersionCommand;
import emissary.command.WhatCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point of the jar file
 * 
 * Parses command line arguments and delegates commands
 */
public class Emissary {
    private static final Logger LOG = LoggerFactory.getLogger(Emissary.class);

    private final JCommander jc = new JCommander();
    private final Map<String, EmissaryCommand> commands;

    public static Map<String, EmissaryCommand> EMISSARY_COMMANDS = new HashMap<>();

    private boolean bannerDumped = false;

    static {
        List<Class<? extends EmissaryCommand>> cmds =
                Arrays.asList(ServerCommand.class, HelpCommand.class, WhatCommand.class, TopologyCommand.class, FeedCommand.class,
                        AgentsCommand.class, PoolCommand.class, VersionCommand.class, RunCommand.class, EnvCommand.class, StopCommand.class,
                        PeersCommand.class);
        Map<String, EmissaryCommand> staticCopy = new HashMap<>();
        for (Class<? extends EmissaryCommand> clz : cmds) {
            EmissaryCommand cmd;
            try {
                cmd = clz.getDeclaredConstructor().newInstance();
                String name = cmd.getCommandName();
                staticCopy.put(name, cmd);
            } catch (ReflectiveOperationException e) {
                LOG.error("Couldn't make EMISSARY_COMMANDS", e);
                System.exit(1);
            }
        }
        EMISSARY_COMMANDS = Collections.unmodifiableMap(staticCopy);
    }

    @VisibleForTesting
    protected JCommander getJCommander() {
        return jc;
    }

    protected Emissary() {
        this(EMISSARY_COMMANDS);
    }

    protected Emissary(Map<String, EmissaryCommand> cmds) {
        commands = Collections.unmodifiableMap(cmds);
        // sort by command name and then add to jCommander
        for (String key : new TreeSet<String>(commands.keySet())) {
            jc.addCommand(key, commands.get(key));
        }
    }

    protected void execute(String[] args) {
        reconfigureLogHook(); // so we can capture everything for test, like the verbose output
        String shouldSetVerbose = System.getProperty("set.jcommander.debug");
        if (shouldSetVerbose != null && shouldSetVerbose.equals("true")) {
            // could also set system property JCommander.DEBUG
            // if that was set before adding commands to the jc object though, you would get logs
            // for adding parameter descriptions etc
            jc.setVerbose(1);
        }
        try {
            jc.parse(args);
            String commandName = jc.getParsedCommand();
            if (commandName == null) {
                dumpBanner();
                LOG.error("One command is required");
                HelpCommand.dumpCommands(jc);
                exit(1);
            }
            EmissaryCommand cmd = commands.get(commandName);
            dumpBanner(cmd);
            cmd.run(jc);
            // don't exit(0) here or things like server will not continue to run
        } catch (MissingCommandException e) {
            dumpBanner();
            LOG.error("Undefined command: {}", Arrays.toString(args));
            HelpCommand.dumpCommands(jc);
            exit(1);
        } catch (Exception e) {
            dumpBanner();
            LOG.error("Command threw an exception: {}", Arrays.toString(args), e);
            exit(1);
        }
    }

    private void dumpBanner(EmissaryCommand cmd) {
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
    static void setupLogbackForConsole() {
        // So it looks better when commands are run
        ch.qos.logback.classic.Logger root =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
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
    }

    static void redirectStdOutStdErr() {
        // no need for sysout-over-slf4j anymore, which as need for any calls, like jni, which only
        // output to stdout/stderr Last none logback message
        LOG.trace("Redefining stdout so logback and capture the output");
        System.setOut(new PrintStream(System.out) {
            public void print(String s) {
                LOG.info(s);
            }
        });

        LOG.trace("Redefining stderr so logback and capture the output");
        System.setErr(new PrintStream(System.err) {

            public void print(String s) {
                LOG.error(s);
            }
        });
    }


}
