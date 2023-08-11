package emissary.command;

import emissary.util.Version;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;

@Command(description = "Dump the Emissary version", subcommands = {HelpCommand.class})
public class VersionCommand implements EmissaryCommand {

    static final Logger LOG = LoggerFactory.getLogger(VersionCommand.class);
    public static final String COMMAND_NAME = "version";

    @Option(names = "--showMobi1eAgents", description = "show MobileAgents\nDefault: ${DEFAULT-VALUE}", hidden = true)
    private boolean showMobileAgent = false;

    @Option(names = {"-q", "--quiet"}, description = "hide banner and non essential messages\nDefault: ${DEFAULT-VALUE}")
    private boolean quiet = false;

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
        if (!showMobileAgent) {
            LOG.info("Emissary Version: {}", new Version().toString());
        } else {
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
            System.out.println("Showing MobileAgents");
            System.out.println(Version.mobileAgents);
        }
    }

    @Override
    public void outputBanner() {
        if (getQuiet() == false) {
            new Banner().dump();
        }
    }
}
