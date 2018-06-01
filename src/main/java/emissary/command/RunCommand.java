package emissary.command;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Parameters(commandDescription = "Run arbitrary class with optional args")
public class RunCommand extends BaseCommand {

    private static final Logger LOG = LoggerFactory.getLogger(RunCommand.class);

    @Parameter(
            variableArity = true,
            required = true,
            description = "fully qualified class name to run with remaining arguments passed on as args to that classes main method.  Use -- to stop processing strings as args and pass them along.")
    public List<String> args = new ArrayList<>();

    @Override
    public String getCommandName() {
        return "run";
    }

    @Override
    public void run(JCommander jc) {
        setup();
        // make a class from whatever name
        String clazzName = args.get(0);
        String[] clazzArgs = null;
        LOG.info("Class to run is {}", clazzName);
        try {
            Class<?> clazz = Class.forName(clazzName);
            Method meth = clazz.getMethod("main", String[].class);
            if (args.size() > 1) {
                // run with rests of args
                clazzArgs = new String[args.size() - 1];
                for (int i = 1; i < args.size(); i++) {
                    clazzArgs[i - 1] = args.get(i);
                }
                LOG.info("Running with {}", Arrays.toString(clazzArgs));
                meth.invoke(null, (Object) clazzArgs);
            } else {
                LOG.info("Running no args");
                // run class
                meth.invoke(null, (Object) new String[0]);
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Could not find fully qualified class named " + clazzName);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
            e.printStackTrace();
            throw new RuntimeException("Problem calling main from " + clazzName + " with " + Arrays.toString(clazzArgs) + " : " + e.getMessage());
        }


    }

    @Override
    public void setupCommand() {
        setupRun();
    }

    public void setupRun() {
        setupConfig();
    }


}
