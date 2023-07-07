package emissary.command;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

@Parameters(commandDescription = "Print commands or usage for subcommand")
@Command(name = "help", description = "Print commands or usage for subcommand")
public class HelpCommand implements EmissaryCommand {

    static final Logger LOG = LoggerFactory.getLogger(HelpCommand.class);

    public static final String COMMAND_NAME = "help";

    @Parameter(arity = 1, description = "display usage for this subcommand ")
    // @Option(names = "placeholder", arity = "1", description = "display usage for this subcommand ")
    @CommandLine.Parameters(
            paramLabel = "COMMAND",
            arity = "0..1",
            description = {"display usage for this subcommand "})
    public List<String> subcommands = new ArrayList<>();


    public List<String> getSubcommands() {
        return subcommands;
    }

    public String getSubcommand() {
        return subcommands.get(0);
    }

    @Override
    public String getCommandName() {
        return COMMAND_NAME;
    }

    @Override
    public void run() {
        setup();
        if (subcommands.isEmpty()) {
            // uh
        } else if (subcommands.size() > 1) {
            LOG.error("You can only see help for 1 command at a time");
        } else {
            String subcommand = getSubcommand();
            LOG.info("Detailed help for: {}", subcommand);
        }
    }

    public void run(JCommander jc) {
        setup();
        if (subcommands.isEmpty()) {
            dumpCommands(jc);
        } else if (subcommands.size() > 1) {
            LOG.error("You can only see help for 1 command at a time");
            dumpCommands(jc);
        } else {
            String subcommand = getSubcommand();
            LOG.info("Detailed help for: {}", subcommand);
            try {
                jc.usage(subcommand);
            } catch (ParameterException e) {
                LOG.error("ERROR: invalid command name: {}", subcommand);
                dumpCommands(jc);
            }
        }
    }

    public void run(CommandLine c) {
        setup();
        if (subcommands.isEmpty()) {
            dumpCommands(c);
        } else if (subcommands.size() > 1) {
            LOG.error("You can only see help for 1 command at a time");
            dumpCommands(c);
        } else {
            String subcommand = getSubcommand();
            LOG.info("Detailed help for: {}", subcommand);
            try {
                // TODO: placeholder
                c.usage(System.out);
            } catch (ParameterException e) {
                LOG.error("ERROR: invalid command name: {}", subcommand);
                dumpCommands(c);
            }
        }
    }

    @Override
    public void setupCommand() {
        // nothing for this command
    }

    public static void dumpCommands(JCommander jc) {
        LOG.info("Available commands:");
        for (Entry<String, JCommander> cmd : jc.getCommands().entrySet()) {
            final String name = cmd.getKey();
            final String description = jc.getCommandDescription(name);
            if (LOG.isInfoEnabled()) {
                LOG.info("\t {} {}", String.format("%1$-15s", name), description);
            }
        }
        LOG.info("Use 'help <command-name>' to see more detailed info about that command");
    }

    public static void dumpCommands(CommandLine cl) {
        LOG.info("Available commands:");
        for (Entry<String, CommandLine> cmd : cl.getSubcommands().entrySet()) {
            final String name = cmd.getKey();
            String[] descList = cl.getCommandSpec().usageMessage().description();
            final String description = descList.length == 0 ? "" : descList[0];
            if (LOG.isInfoEnabled()) {
                LOG.info("\t {} {}", String.format("%1$-15s", name), description);
            }
        }
        LOG.info("Use 'help <command-name>' to see more detailed info about that command");
    }

    @Override
    public void outputBanner() {
        new Banner().dump();
    }
}
