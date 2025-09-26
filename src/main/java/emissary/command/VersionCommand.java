package emissary.command;

import emissary.util.GitRepositoryState;

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
        return COMMAND_NAME;
    }

    @Override
    public void setupCommand() {
        // no op
    }

    @Override
    public void run(CommandLine c) {
        setup();
        if (LOG.isInfoEnabled()) {
            LOG.info(GitRepositoryState.dumpVersionInfo(GitRepositoryState.getRepositoryState(), "Emissary"));
        }
    }

    @Override
    public void outputBanner() {
        if (!getQuiet()) {
            new Banner().dump();
        }
    }
}
