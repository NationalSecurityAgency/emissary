package emissary.command;

import emissary.util.Version;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(description = "Dump the Emissary version", subcommands = {HelpCommand.class})
public class VersionCommand implements EmissaryCommand {

    protected static final Logger LOG = LoggerFactory.getLogger(VersionCommand.class);
    public static final String COMMAND_NAME = "version";

    @Option(names = {"-q", "--quiet"}, description = "hide banner and non essential messages\nDefault: ${DEFAULT-VALUE}")
    protected boolean quiet = false;

    public boolean getQuiet() {
        return quiet;
    }

    @Override
    public String getCommandName() {
        return "version";
    }

    @Override
    public void setupCommand() {
        // no op
    }

    @Override
    public void run(CommandLine c) {
        setup();
        LOG.info("Emissary Version: {}", new Version());
    }

    @Override
    public void outputBanner() {
        if (!getQuiet()) {
            new Banner().dump();
        }
    }
}
