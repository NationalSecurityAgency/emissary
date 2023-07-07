package emissary.command;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunCommandIT extends UnitTest {

    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        outContent = null;
        errContent = null;
    }

    @Test
    void testClassMustExist() {
        String clazzName = "com.junk.Who";
        Exception e = assertThrows(Exception.class, () -> {
            RunCommand cmd = RunCommand.parse(RunCommand.class, clazzName);
            cmd.run();
        });
        assertTrue(e.getMessage().contains("Could not find fully qualified class named " + clazzName));
    }

    @Test
    void testClassIsRunWhenFound() throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        String clazzName = "emissary.command.RunCommandIT";
        RunCommand cmd = RunCommand.parse(RunCommand.class, clazzName);

        captureStdOutAndStdErrAndRunCommand(cmd);

        assertTrue(outContent.toString().contains("I am a test that runs myself.  My args are []"));
    }

    @Test
    void testClassIsRunWhenFoundWithArgs() throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        String clazzName = "emissary.command.RunCommandIT";
        String arg1 = "asdf";
        String arg2 = "dkdke";
        String arg3 = "k3k23k";
        RunCommand cmd = RunCommand.parse(RunCommand.class, clazzName, arg1, arg2, arg3);

        captureStdOutAndStdErrAndRunCommand(cmd);

        assertTrue(outContent.toString().contains("I am a test that runs myself.  My args are [" + arg1 + ", " + arg2 + ", " + arg3 + "]"));
    }

    @Test
    void testFlagArgsPassedThrough() throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        String clazzName = "emissary.command.RunCommandIT";
        String stopJcommanderProcessing = "--";
        String arg1 = "-f";
        String arg2 = "somefile";
        String arg3 = "--greatestArg";
        String arg4 = "ever";
        RunCommand cmd = RunCommand.parse(RunCommand.class, clazzName, stopJcommanderProcessing, arg1, arg2, arg3, arg4);

        captureStdOutAndStdErrAndRunCommand(cmd);

        assertTrue(outContent.toString().contains("I am a test that runs myself.  My args are [" + arg1 + ", " + arg2 + ", " + arg3 + ", "
                + arg4 + "]"));

    }

    private void captureStdOutAndStdErrAndRunCommand(RunCommand cmd) {
        PrintStream origOut = System.out;
        PrintStream origErr = System.err;
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
        cmd.run();
        System.setOut(origOut);
        System.setErr(origErr);
    }

    public static void main(String[] args) {
        System.out.println("I am a test that runs myself.  My args are " + Arrays.toString(args));
    }

}
