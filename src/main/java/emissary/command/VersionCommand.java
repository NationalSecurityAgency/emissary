package emissary.command;

import emissary.util.Version;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;

@Parameters(commandDescription = "Dump the Emissary version")
@Command(description = "Dump the Emissary version", subcommands = {HelpCommand.class})
public class VersionCommand implements EmissaryCommand {

    static final Logger LOG = LoggerFactory.getLogger(VersionCommand.class);
    public static final String COMMAND_NAME = "version";

    @Parameter(names = "--showMobi1eAgents", description = "show MobileAgents", hidden = true)
    @Option(names = "--showMobi1eAgents", description = "show MobileAgents", hidden = true)
    private boolean showMobileAgent = false;

    @Parameter(names = {"-q", "--quiet"}, description = "hide banner and non essential messages")
    @Option(names = {"-q", "--quiet"}, description = "hide banner and non essential messages")
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
    public void run() {
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
