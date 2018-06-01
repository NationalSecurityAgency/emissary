package emissary.command;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import emissary.client.EmissaryClient;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Parameters(commandDescription = "Output the configured values for certain properties")
public class EnvCommand extends HttpCommand {

    static final Logger LOG = LoggerFactory.getLogger(EnvCommand.class);

    public static int DEFAULT_PORT = 8001;

    public int getDefaultPort() {
        return DEFAULT_PORT;
    }

    @Override
    public String getCommandName() {
        return "env";
    }

    @Parameter(names = {"--bashable"}, description = "format output for sourcing by bash")
    private boolean bashable = false;

    public boolean getBashable() {
        return bashable;
    }

    @Override
    public boolean getQuiet() {
        // if bashable, make it quiet too
        if (bashable) {
            return true;
        }
        return super.getQuiet();
    }

    @Override
    public void run(JCommander jc) {
        String endpoint = getScheme() + "://" + getHost() + ":" + getPort() + "/api/env";

        if (getBashable()) {
            ch.qos.logback.classic.Logger root =
                    (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
            root.detachAndStopAllAppenders();
            setup();
            LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
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

            // still gotta hide org.eclipse.jetty.util.log INFO
            ch.qos.logback.classic.Logger jettyUtilLogger = lc.getLogger("org.eclipse.jetty.util.log");
            jettyUtilLogger.setLevel(Level.WARN);

            // also add .sh to the endpoint
            endpoint = endpoint + ".sh";
            LOG.info("# generated from env command at {}", endpoint);
        } else {
            setup(); // go ahead an log it
        }
        EmissaryClient client = new EmissaryClient();
        LOG.info(client.send(new HttpGet(endpoint)).getContentString());
    }

    @Override
    public void setupCommand() {
        setupEnv();
    }

    public void setupEnv() {
        setupConfig();
    }

    @Override
    public void outputBanner() {
        // override so we can use the getQuiet defined here
        if (getQuiet() == false) {
            new Banner().dump();
        }
    }


}
