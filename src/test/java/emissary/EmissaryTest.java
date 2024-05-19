package emissary;

import emissary.command.BaseCommand;
import emissary.command.EmissaryCommand;
import emissary.core.EmissaryRuntimeException;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class EmissaryTest extends UnitTest {


    @Test
    void testDefaultCommands() {
        assertTrue(Emissary.EMISSARY_COMMANDS.size() > 0);
    }

    @Test
    void testDefaultCommandsUnmodifiable() {
        JunkCommand cmd = new JunkCommand();
        assertThrows(UnsupportedOperationException.class, () -> Emissary.EMISSARY_COMMANDS.put("junk", cmd));
    }

    @Test
    void testCommandNamesAreSorted() {
        Map<String, EmissaryCommand> cmds = new HashMap<>();
        cmds.put("aaaa", new JunkCommand());
        cmds.put("zzzz", new JunkCommand());
        cmds.put("zaza", new JunkCommand());
        cmds.put("eeee", new JunkCommand());

        Emissary emissary = new Emissary(cmds);
        ArrayList<String> sortedNames = new ArrayList<>(cmds.keySet());
        Collections.sort(sortedNames);
        ArrayList<String> namesAsStored = new ArrayList<>(emissary.getCommand().getSubcommands().keySet());

        assertIterableEquals(namesAsStored, sortedNames);
    }


    @Test
    void testExecuteWithNoArgs() {
        Emissary2 emissary = new Emissary2();

        emissary.execute(new String[] {});
        assertTrue(emissary.getOut().contains("One command is required"));
        assertTrue(emissary.getOut().contains("Return Code was: 1"));
    }

    @Test
    void testExecuteWithUndefinedCommand() {
        Emissary2 emissary = new Emissary2();

        emissary.execute(makeArgs("notherebro"));
        assertTrue(emissary.getOut().contains("Undefined command: [notherebro]"));
        assertTrue(emissary.getOut().contains("Return Code was: 1"));
    }

    @Test
    void testExecuteHelp() {
        Emissary2 emissary = new Emissary2();

        emissary.execute(makeArgs("help", "server"));
        assertTrue(emissary.getOut().contains("Detailed help for: server"));
        // can't assert exit 0 since it doesn't call System.exit(0)
        assertFalse(emissary.getOut().contains("Return Code was: 1"));
    }

    @Test
    void testExecuteHappyPath() {
        Map<String, EmissaryCommand> cmds = new HashMap<>();
        cmds.put("junk", new JunkCommand());

        Emissary2 emissary = new Emissary2(cmds);

        emissary.execute(makeArgs("junk"));
        assertTrue(emissary.getOut().contains("You got junk"));
        // can't assert exit 0 since it doesn't call System.exit(0)
        assertFalse(emissary.getOut().contains("Return Code was: 1"));
    }

    @Test
    void testExecuteCommandThrows() {
        Map<String, EmissaryCommand> cmds = new HashMap<>();
        cmds.put("broke", new BrokeCommand());

        Emissary2 emissary = new Emissary2(cmds);

        emissary.execute(makeArgs("broke"));
        assertTrue(emissary.getOut().contains("Command threw an exception"));
        assertTrue(emissary.getOut().contains("RuntimeException: Still broken here"));
        assertTrue(emissary.getOut().contains("Return Code was: 1"));
    }


    @Test
    void testVerbose() {
        Map<String, EmissaryCommand> cmds = new HashMap<>();
        // like is done in the emissary script
        System.setProperty("set.picocli.debug", "true");
        cmds.put("another", new AnotherBaseCommand());

        Emissary2 emissary = new Emissary2(cmds);

        emissary.execute(makeArgs("another"));
        assertTrue(emissary.getErr().contains("picocli INFO] Parsing 1 command line args [another]"));
    }

    private static String[] makeArgs(String... args) {
        String[] ret = new String[args.length];
        System.arraycopy(args, 0, ret, 0, args.length);
        return ret;
    }

    // need to replace System.exit so we have time to see results
    static class Emissary2 extends Emissary {

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
            addReturnCodeToOutContent("Return Code was: " + retCode);
        }

        public void addReturnCodeToOutContent(String retOutput) {
            try {
                outContent.write(retOutput.getBytes());
            } catch (IOException e) {
                fail("OutContent should never throw IOException", e);
            }
        }
    }

    @Command()
    static class JunkCommand implements EmissaryCommand {
        final Logger LOG = LoggerFactory.getLogger(JunkCommand.class);

        @Override
        public String getCommandName() {
            return "junk";
        }

        @Override
        public void run(CommandLine c) {
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

    @Command()
    static class BrokeCommand implements EmissaryCommand {

        @Override
        public String getCommandName() {
            return "broke";
        }

        @Override
        public void setupCommand() {
            // nothing to do here
        }

        @Override
        public void run(CommandLine c) {
            setup();
            throw new EmissaryRuntimeException("Still broken here");
        }

        @Override
        public void outputBanner() {
            // nothing thanks
        }
    }

    @Command()
    static class AnotherBaseCommand extends BaseCommand {
        // need to extend BaseCommand to get verbose options
        final Logger LOG = LoggerFactory.getLogger(AnotherBaseCommand.class);

        @Override
        public String getCommandName() {
            return "another";
        }

        @Override
        public void run(CommandLine c) {
            setup();
            LOG.info("Another great command run");
        }
    }

}
