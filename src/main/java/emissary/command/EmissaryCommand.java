package emissary.command;

import com.beust.jcommander.JCommander;

public interface EmissaryCommand {

    String COMMAND_NAME = "EmissaryCommand";

    String getCommandName();

    default void setup() {
        setupCommand();
    }

    // do whatever command specific you need
    void setupCommand();

    // The run method should call setup to work correctly
    void run(JCommander jc);

    // dump the banner
    void outputBanner();
}
