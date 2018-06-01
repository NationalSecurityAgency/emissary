package emissary.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Parameters(commandDescription = "Print commands or usage for subcommand")
public class HelpCommand implements EmissaryCommand {

    static final Logger LOG = LoggerFactory.getLogger(HelpCommand.class);

    public static String COMMAND_NAME = "help";

    @Parameter(arity = 1, description = "display usage for this subcommand ")
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
    public void run(JCommander jc) {
        setup();
        if (subcommands.size() == 0) {
            dumpCommands(jc);
        } else if (subcommands.size() > 1) {
            LOG.error("You can only see help for 1 command at a time");
            dumpCommands(jc);
        } else {
            String subcommand = getSubcommand();
            LOG.info("Detailed help for: " + subcommand);
            try {
                jc.usage(subcommand);
            } catch (ParameterException e) {
                LOG.error("ERROR: invalid command name: " + subcommand);
                dumpCommands(jc);
            }
        }
    }

    @Override
    public void setupCommand() {
        // nothing for this command
    }

    public static void dumpCommands(JCommander jc) {
        System.out.println("Available commands:");
        for (Entry<String, JCommander> cmd : jc.getCommands().entrySet()) {
            String name = cmd.getKey();
            String description = jc.getCommandDescription(name);
            LOG.info("\t" + String.format("%1$-15s", name) + " " + description);
        }
        LOG.info("Use 'help <command-name>' to see more detailed info about that command");
    }

    @Override
    public void outputBanner() {
        new Banner().dump();
    }

}
