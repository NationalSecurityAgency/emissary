package emissary.command;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;

import com.beust.jcommander.JCommander;
import emissary.test.core.UnitTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RunCommandIT extends UnitTest {

    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        outContent = null;
        errContent = null;
    }

    @Test
    public void testClassMustExist() throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        String clazzName = "com.junk.Who";
        try {
            RunCommand cmd = RunCommand.parse(RunCommand.class, clazzName);
            cmd.run(new JCommander());
            fail("Should have thrown exception");
        } catch (Exception e) {
            assertThat(e.getMessage(), containsString("Could not find fully qualified class named " + clazzName));
        }
    }

    @Test
    public void testClassIsRunWhenFound() throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        String clazzName = "emissary.command.RunCommandIT";
        RunCommand cmd = RunCommand.parse(RunCommand.class, clazzName);

        captureStdOutAndStdErrAndRunCommand(cmd);

        assertThat(outContent.toString(), containsString("I am a test that runs myself.  My args are []"));
    }

    @Test
    public void testClassIsRunWhenFoundWithArgs() throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        String clazzName = "emissary.command.RunCommandIT";
        String arg1 = "asdf";
        String arg2 = "dkdke";
        String arg3 = "k3k23k";
        RunCommand cmd = RunCommand.parse(RunCommand.class, clazzName, arg1, arg2, arg3);

        captureStdOutAndStdErrAndRunCommand(cmd);

        assertThat(outContent.toString(), containsString("I am a test that runs myself.  My args are [" + arg1 + ", " + arg2 + ", " + arg3 + "]"));
    }

    @Test
    public void testFlagArgsPassedThrough() throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        String clazzName = "emissary.command.RunCommandIT";
        String stopJcommanderProcessing = "--";
        String arg1 = "-f";
        String arg2 = "somefile";
        String arg3 = "--greatestArg";
        String arg4 = "ever";
        RunCommand cmd = RunCommand.parse(RunCommand.class, clazzName, stopJcommanderProcessing, arg1, arg2, arg3, arg4);

        captureStdOutAndStdErrAndRunCommand(cmd);

        assertThat(outContent.toString(), containsString("I am a test that runs myself.  My args are [" + arg1 + ", " + arg2 + ", " + arg3 + ", "
                + arg4 + "]"));

    }

    private void captureStdOutAndStdErrAndRunCommand(RunCommand cmd) {
        PrintStream origOut = System.out;
        PrintStream origErr = System.err;
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
        cmd.run(new JCommander());
        System.setOut(origOut);
        System.setErr(origErr);
    }

    public static void main(String[] args) {
        System.out.println("I am a test that runs myself.  My args are " + Arrays.toString(args));
    }

}
