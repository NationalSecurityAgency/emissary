package emissary.command;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(description = "Emissary Command")
public interface EmissaryCommand extends Runnable {

    String COMMAND_NAME = "EmissaryCommand";

    String getCommandName();

    default void setup() {
        setupCommand();
    }

    // do whatever command specific you need
    void setupCommand();

    // The run method should call setup to work correctly
    void run(CommandLine c);

    // dump the banner
    void outputBanner();

    @Override
    default void run() {}
}
