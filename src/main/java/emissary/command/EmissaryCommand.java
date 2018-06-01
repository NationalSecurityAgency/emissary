package emissary.command;

import com.beust.jcommander.JCommander;

public interface EmissaryCommand {

    public static String COMMAND_NAME = "EmissaryCommand";

    // just get the constant
    public String getCommandName();

    default void setup() {
        setupCommand();
    }

    // do whatever command specific you need
    public void setupCommand();

    // The run method should call setup to work correctly
    public void run(JCommander jc);

    // dump the banner
    public void outputBanner();
}
