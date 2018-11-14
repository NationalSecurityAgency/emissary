package emissary;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.beust.jcommander.JCommander;
import emissary.command.BaseCommand;
import emissary.command.EmissaryCommand;
import emissary.test.core.UnitTest;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmissaryTest extends UnitTest {


    @Test
    public void testDefaultCommands() {
        assertThat(Emissary.EMISSARY_COMMANDS.size(), greaterThan(0));
    }

    @Test
    public void testDefaultCommandsUnmodifiable() {
        try {
            Emissary.EMISSARY_COMMANDS.put("junk", new JunkCommand());
            fail("Should have thrown");
        } catch (UnsupportedOperationException e) {
            // this is the right path
        }
    }

    @Test
    public void testCommandNamesAreSorted() {
        Map<String, EmissaryCommand> cmds = new HashMap<>();
        cmds.put("aaaa", new JunkCommand());
        cmds.put("zzzz", new JunkCommand());
        cmds.put("zaza", new JunkCommand());
        cmds.put("eeee", new JunkCommand());

        Emissary emissary = new Emissary(cmds);

        ArrayList<String> sortedNames = new ArrayList<String>(cmds.keySet());
        Collections.sort(sortedNames);
        ArrayList<String> namesAsStored = new ArrayList<String>(emissary.getJCommander().getCommands().keySet());

        assertThat(namesAsStored, contains(sortedNames.toArray()));
    }


    @Test
    public void testExecuteWithNoArgs() {
        Emissary2 emissary = new Emissary2();

        emissary.execute(new String[] {});
        assertThat(emissary.getOut(), containsString("One command is required"));
        assertThat(emissary.getOut(), containsString("Return Code was: 1"));
    }

    @Test
    public void testExecuteWithUndefinedCommand() {
        Emissary2 emissary = new Emissary2();

        emissary.execute(makeArgs("notherebro"));
        assertThat(emissary.getOut(), containsString("Undefined command: [notherebro]"));
        assertThat(emissary.getOut(), containsString("Return Code was: 1"));
    }

    @Test
    public void testExecuteHelp() {
        Emissary2 emissary = new Emissary2();

        emissary.execute(makeArgs("help", "server"));
        assertThat(emissary.getOut(), containsString("Detailed help for: server"));
        // can't assert exit 0 since it doesn't call System.exit(0)
        assertThat(emissary.getOut(), not(containsString("Return Code was: 1")));
    }

    @Test
    public void testExecuteHappyPath() {
        Map<String, EmissaryCommand> cmds = new HashMap<>();
        cmds.put("junk", new JunkCommand());

        Emissary2 emissary = new Emissary2(cmds);

        emissary.execute(makeArgs("junk"));
        assertThat(emissary.getOut(), containsString("You got junk"));
        // can't assert exit 0 since it doesn't call System.exit(0)
        assertThat(emissary.getOut(), not(containsString("Return Code was: 1")));
    }

    @Test
    public void testExecuteCommmandThrows() {
        Map<String, EmissaryCommand> cmds = new HashMap<>();
        cmds.put("broke", new BrokeCommand());

        Emissary2 emissary = new Emissary2(cmds);

        emissary.execute(makeArgs("broke"));
        assertThat(emissary.getOut(), containsString("Command threw an exception"));
        assertThat(emissary.getOut(), containsString("RuntimeException: Still broken here"));
        assertThat(emissary.getOut(), containsString("Return Code was: 1"));
    }


    @Test
    public void testVerbose() {
        Map<String, EmissaryCommand> cmds = new HashMap<>();
        // like is done in the emissary script
        System.setProperty("set.jcommander.debug", "true");
        cmds.put("another", new AnotherBaseCommand());

        Emissary2 emissary = new Emissary2(cmds);

        emissary.execute(makeArgs("another"));
        assertThat(emissary.getOut(), containsString("JCommander] Parsing \"another\""));
    }

    private String[] makeArgs(String... args) {
        String[] ret = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            ret[i] = args[i];
        }
        return ret;
    }

    // need to replace System.exit so we have time to see results
    class Emissary2 extends Emissary {

        final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        final ByteArrayOutputStream errContent = new ByteArrayOutputStream();

        public String getOut() {
            return outContent.toString();
        }

        public String getErr() {
            return errContent.toString();
        }

        protected Emissary2() {
            this(Emissary.EMISSARY_COMMANDS);
        }

        protected Emissary2(Map<String, EmissaryCommand> cmds) {
            super(cmds);
        }

        @Override
        // hook in Emissary so we can capture the output
        protected void reconfigureLogHook() {
            System.setOut(new PrintStream(outContent));
            System.setErr(new PrintStream(errContent));
        }

        @Override
        protected void exit(int retCode) {
            System.out.println("Return Code was: " + retCode);
        }
    }

    class JunkCommand implements EmissaryCommand {
        final Logger LOG = LoggerFactory.getLogger(JunkCommand.class);

        @Override
        public String getCommandName() {
            return "junk";
        }

        @Override
        public void run(JCommander jc) {
            setup();
            LOG.info("You got junk");
        }

        @Override
        public void setupCommand() {
            // nothing to do here
        }

        @Override
        public void outputBanner() {
            LOG.info("NOBANNER");
        }
    }

    class BrokeCommand implements EmissaryCommand {

        @Override
        public String getCommandName() {
            return "broke";
        }

        @Override
        public void setupCommand() {
            // nothing to do here
        }

        @Override
        public void run(JCommander jc) {
            setup();
            throw new RuntimeException("Still broken here");
        }

        @Override
        public void outputBanner() {
            // nothing thanks
        }
    }

    class AnotherBaseCommand extends BaseCommand {
        // need to extend BaseCommand to get verbose options
        final Logger LOG = LoggerFactory.getLogger(AnotherBaseCommand.class);

        @Override
        public String getCommandName() {
            return "another";
        }

        @Override
        public void run(JCommander jc) {
            setup();
            LOG.info("Another great command run");
        }

    }

}
