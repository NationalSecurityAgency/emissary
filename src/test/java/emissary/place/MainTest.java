package emissary.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import emissary.core.DataObjectFactory;
import emissary.core.Form;
import emissary.core.IBaseDataObject;
import emissary.core.Namespace;
import emissary.test.core.UnitTest;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class MainTest extends UnitTest {
    private String className = "emissary.place.sample.DevNullPlace";
    private String[] defaultArgs = {"-s"};

    @Override
    @Before
    public void setUp() throws Exception {


        try {
            File dataFile = new File(TMPDIR + "/testmain.dat");
            FileOutputStream ros = new FileOutputStream(dataFile);
            ros.write("abcdefghijklmnopqrstuvwxyz".getBytes());
            ros.close();
        } catch (IOException ex) {
            fail("Unable to create test file: " + ex.getMessage());
        }
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        // Clear out namespace
        for (String key : Namespace.keySet()) {
            Namespace.unbind(key);
        }
        File dataFile = new File(TMPDIR + "/testmain.dat");
        dataFile.delete();
    }

    @Test
    public void testStandardOptions() {
        Options o = Main.getStandardOptions();
        assertNotNull("Standard options should be produced", o);

        Main m = new Main(className, defaultArgs);
        Options mo = m.getOptions();
        assertNotNull("Standard options from instance should be produced", mo);

        assertEquals("Standard options are used by default", o.getOptions().size(), mo.getOptions().size());

    }

    @Test
    public void testParseDefaultValues() {
        Main m = new Main(className, defaultArgs);
        m.parseArguments();
        assertTrue("Default args sets silent", m.isSilent());
        assertFalse("Default args are not recursive", m.isRecursive());
        assertFalse("Default args are not verbose", m.isVerbose());
        assertEquals("Default config location should stick", className + ".cfg", m.getConfigLocation());
        assertEquals("Default current form should be there", emissary.core.Form.UNKNOWN, m.getCurrentForm());
        assertEquals("No files should be left", 0, m.getFileArgs().size());
    }

    @Test
    public void testParseReverseValues() {
        String[] newArgs = {"-v", "-R"};
        Main m = new Main(className, newArgs);
        m.parseArguments();
        assertFalse("These args are not silent", m.isSilent());
        assertTrue("These args are verbose", m.isVerbose());
        assertTrue("These args are recursive", m.isRecursive());
        assertEquals("No files should be left", 0, m.getFileArgs().size());
    }

    @Test
    public void testParseConfigLoc() {
        String[] newArgs = {"-c", "emissary.place.sample.ToLowerPlace.cfg"};
        Main m = new Main(className, newArgs);
        m.parseArguments();
        assertEquals("Specified config location should stick", newArgs[1], m.getConfigLocation());
        assertEquals("No files should be left", 0, m.getFileArgs().size());
    }

    @Test
    public void testCurrentForm() {
        String[] newArgs = {"-t", "FOOBAR"};
        Main m = new Main(className, newArgs);
        m.parseArguments();
        assertEquals("Specified current form should stick", newArgs[1], m.getCurrentForm());
        assertEquals("No files should be left", 0, m.getFileArgs().size());
    }

    @Test
    public void testSetParams() {
        String[] newArgs = {"-p", "FOO=BAR"};
        Main m = new Main(className, newArgs);
        m.parseArguments();
        IBaseDataObject payload = DataObjectFactory.getInstance("aaa".getBytes(), "test", "UNKNOWN");
        payload.setParameters(m.getParameters());
        assertEquals("Specified parameter should stick", "BAR", payload.getStringParameter("FOO"));
    }

    @Test
    public void testLoggerLevels() {
        // Careful, DevNull and root category will not be reset after this
        String[] newArgs = {"-l", "FATAL", "-L", "INFO"};
        Main m = new Main(className, newArgs);
        m.parseArguments();
        assertEquals("No files should be left", 0, m.getFileArgs().size());
    }

    @Test
    public void testFilesRemaining() {
        String[] newArgs = {"-s", "foo.txt", "bar.txt"};
        Main m = new Main(className, newArgs);
        m.parseArguments();
        assertEquals("Two files should be left", 2, m.getFileArgs().size());
    }

    @Test
    public void testPrintHooks() {
        String[] args = {"-X", TMPDIR + "/testmain.dat"};
        MainWithHooks m = new MainWithHooks(className, args);
        m.run();
        assertTrue("PrePrintHook must be called", m.printhook[0]);
        assertTrue("PostPrintHook must be called", m.printhook[1]);
    }

    @Test
    public void testProcHooks() {
        String[] args = {"-X", TMPDIR + "/testmain.dat"};
        MainWithHooks m = new MainWithHooks(className, args);
        m.run();
        assertTrue("PreProcessHook must be called", m.processhook[0]);
        assertTrue("PostProcessHook must be called", m.processhook[1]);
    }

    @Test
    public void testArgumentHooks() {
        String[] args = {"-X", "foo.txt"};
        MainWithHooks m = new MainWithHooks(className, args);
        m.parseArguments();
        assertTrue("PreArgumentsHook must be called", m.arghook[0]);
        assertTrue("PostArgumentsHook must be called", m.arghook[1]);
    }

    @Test
    public void testSplitHook() {
        String[] args = {"-s", "-d", TMPDIR, "-S", TMPDIR + "/testmain.dat"};
        MainWithHooks m = new MainWithHooks(className, args);
        m.run();
        assertEquals("Must process args and leave file", 1, m.getFileArgs().size());
        assertTrue("PreSplitHook must be called", m.splithook[0]);
        assertTrue("PostSplitHook must be called", m.splithook[1]);
        File expectedSplitFile = new File(TMPDIR, "testmain.dat." + Form.UNKNOWN);
        assertTrue("File must exist after split", expectedSplitFile.exists());
        expectedSplitFile.delete();
    }

    @Test
    public void testExceptionHandlingInPlaceProcessing() {
        String[] args = {"-s", "-X", TMPDIR + "/testmain.dat"};
        Main m = new Main(OOMPlace.class.getName(), args);
        m.parseArguments();
        try {
            m.run();
        } catch (Throwable t) {
            fail("Main runner allowed exception to escape: " + t);
        }
    }

    @Test
    public void testMetaDataFromChildrenAndExtractedRecordsIsCaptured() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Main m = new Main("emissary.place.MainTest$PlaceTest", new String[] {"-m", ".*"});
        m.setOutputStream(new PrintStream(baos));
        m.run();

        IBaseDataObject payload = DataObjectFactory.getInstance("aaa".getBytes(), "test", "UNKNOWN");
        payload.putParameter("PARENT_KEY", "parent value");
        m.processPayload(payload);

        String s = baos.toString();
        assertTrue("Should have recorded parent metadata - " + s, s.indexOf("PARENT_KEY") > -1);
        assertTrue("Should have recorded child metadata - " + s, s.indexOf("CHILD_KEY") > -1);
        assertTrue("Should have recorded record metadata - " + s, s.indexOf("RECORD_KEY") > -1);
    }


    // Override the hooks. The calls to super in these
    // hooks are not normal, but help us mark the super class
    // hooks as having been tested.
    private static final class MainWithHooks extends Main {
        public boolean[] printhook = {false, false};
        public boolean[] arghook = {false, false};
        public boolean[] processhook = {false, false};
        public boolean[] splithook = {false, false};

        public MainWithHooks(String className, String[] args) {
            super(className, args);
        }

        @Override
        protected boolean prePrintHook(IBaseDataObject payload, List<IBaseDataObject> att) {
            super.prePrintHook(payload, att);
            printhook[0] = true;
            return false;
        }

        @Override
        protected void postPrintHook(IBaseDataObject payload, List<IBaseDataObject> att) {
            super.postPrintHook(payload, att);
            printhook[1] = true;
        }

        @Override
        protected boolean preArgumentsHook(CommandLine cmd) {
            super.preArgumentsHook(cmd);
            arghook[0] = true;
            return true;
        }

        @Override
        protected void postArgumentsHook(CommandLine cmd) {
            super.postArgumentsHook(cmd);
            arghook[1] = true;
        }

        @Override
        protected boolean preProcessHook(IBaseDataObject payload, List<IBaseDataObject> attachments) {
            super.preProcessHook(payload, attachments);
            processhook[0] = true;
            // Form checked in split hook test for file
            if (payload.currentForm() == null) {
                payload.setCurrentForm(Form.UNKNOWN);
            }
            return true;
        }

        @Override
        protected void postProcessHook(IBaseDataObject payload, List<IBaseDataObject> att) {
            super.postProcessHook(payload, att);
            processhook[1] = true;
        }

        @Override
        protected boolean preSplitHook(IBaseDataObject payload, List<IBaseDataObject> att) {
            super.preSplitHook(payload, att);
            splithook[0] = true;
            return true;
        }

        @Override
        protected void postSplitHook(IBaseDataObject payload, List<IBaseDataObject> att) {
            super.postSplitHook(payload, att);
            splithook[1] = true;
        }
    }

    public static class PlaceTest extends ServiceProviderPlace {
        public PlaceTest(String configInfo, String dir, String placeLoc) throws IOException {
            super();
        }

        @Override
        public List<IBaseDataObject> processHeavyDuty(IBaseDataObject payload) throws emissary.core.ResourceException {
            IBaseDataObject extr = DataObjectFactory.getInstance("cde".getBytes(), "test-att-2", "UNKNOWN");
            extr.putParameter("RECORD_KEY", "record value");
            payload.addExtractedRecord(extr);

            IBaseDataObject child = DataObjectFactory.getInstance("cde".getBytes(), "test-att-1", "UNKNOWN");
            payload.putParameter("CHILD_KEY", "child value");

            List<IBaseDataObject> children = new ArrayList<IBaseDataObject>();
            children.add(child);

            return children;
        }

        @Override
        public void process(IBaseDataObject payload) throws emissary.core.ResourceException {}
    }


}
