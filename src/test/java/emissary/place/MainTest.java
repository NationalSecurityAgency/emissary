package emissary.place;

import static emissary.place.Main.filePathIsWithinBaseDirectory;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import emissary.core.DataObjectFactory;
import emissary.core.Form;
import emissary.core.IBaseDataObject;
import emissary.core.Namespace;
import emissary.core.ResourceException;
import emissary.test.core.junit5.UnitTest;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MainTest extends UnitTest {
    private final String className = "emissary.place.sample.DevNullPlace";
    private final String[] defaultArgs = {"-s"};

    @TempDir
    public Path testOutputFolder;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        Path dataFile = Paths.get(TMPDIR, "testmain.dat");
        try (OutputStream ros = Files.newOutputStream(dataFile)) {
            ros.write("abcdefghijklmnopqrstuvwxyz".getBytes());
        } catch (IOException ex) {
            fail("Unable to create test file", ex);
        }
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        // Clear out namespace
        for (String key : Namespace.keySet()) {
            Namespace.unbind(key);
        }
        Path dataFile = Paths.get(TMPDIR, "testmain.dat");
        Files.deleteIfExists(dataFile);
    }

    @Test
    void testStandardOptions() {
        Options o = Main.getStandardOptions();
        assertNotNull(o, "Standard options should be produced");

        Main m = new Main(className, defaultArgs);
        Options mo = m.getOptions();
        assertNotNull(mo, "Standard options from instance should be produced");

        assertEquals(o.getOptions().size(), mo.getOptions().size(), "Standard options are used by default");

    }

    @Test
    void testParseDefaultValues() {
        Main m = new Main(className, defaultArgs);
        m.parseArguments();
        assertTrue(m.isSilent(), "Default args sets silent");
        assertFalse(m.isRecursive(), "Default args are not recursive");
        assertFalse(m.isVerbose(), "Default args are not verbose");
        assertEquals(className + ".cfg", m.getConfigLocation(), "Default config location should stick");
        assertEquals(emissary.core.Form.UNKNOWN, m.getCurrentForm(), "Default current form should be there");
        assertEquals(0, m.getFileArgs().size(), "No files should be left");
    }

    @Test
    void testParseReverseValues() {
        String[] newArgs = {"-v", "-R"};
        Main m = new Main(className, newArgs);
        m.parseArguments();
        assertFalse(m.isSilent(), "These args are not silent");
        assertTrue(m.isVerbose(), "These args are verbose");
        assertTrue(m.isRecursive(), "These args are recursive");
        assertEquals(0, m.getFileArgs().size(), "No files should be left");
    }

    @Test
    void testParseConfigLoc() {
        String[] newArgs = {"-c", "emissary.place.sample.ToLowerPlace.cfg"};
        Main m = new Main(className, newArgs);
        m.parseArguments();
        assertEquals(newArgs[1], m.getConfigLocation(), "Specified config location should stick");
        assertEquals(0, m.getFileArgs().size(), "No files should be left");
    }

    @Test
    void testCurrentForm() {
        String[] newArgs = {"-t", "FOOBAR"};
        Main m = new Main(className, newArgs);
        m.parseArguments();
        assertEquals(newArgs[1], m.getCurrentForm(), "Specified current form should stick");
        assertEquals(0, m.getFileArgs().size(), "No files should be left");
    }

    @Test
    void testSetParams() {
        String[] newArgs = {"-p", "FOO=BAR"};
        Main m = new Main(className, newArgs);
        m.parseArguments();
        IBaseDataObject payload = DataObjectFactory.getInstance("aaa".getBytes(), "test", "UNKNOWN");
        payload.setParameters(m.getParameters());
        assertEquals("BAR", payload.getStringParameter("FOO"), "Specified parameter should stick");
    }

    @Test
    void testLoggerLevels() {
        // Careful, DevNull and root category will not be reset after this
        String[] newArgs = {"-l", "FATAL", "-L", "INFO"};
        Main m = new Main(className, newArgs);
        m.parseArguments();
        assertEquals(0, m.getFileArgs().size(), "No files should be left");
    }

    @Test
    void testFilesRemaining() {
        String[] newArgs = {"-s", "foo.txt", "bar.txt"};
        Main m = new Main(className, newArgs);
        m.parseArguments();
        assertEquals(2, m.getFileArgs().size(), "Two files should be left");
    }

    @Test
    void testPrintHooks() {
        String[] args = {"-X", TMPDIR + "/testmain.dat"};
        MainWithHooks m = new MainWithHooks(className, args);
        m.run();
        assertTrue(m.printhook[0], "PrePrintHook must be called");
        assertTrue(m.printhook[1], "PostPrintHook must be called");
    }

    @Test
    void testProcHooks() {
        String[] args = {"-X", TMPDIR + "/testmain.dat"};
        MainWithHooks m = new MainWithHooks(className, args);
        m.run();
        assertTrue(m.processhook[0], "PreProcessHook must be called");
        assertTrue(m.processhook[1], "PostProcessHook must be called");
    }

    @Test
    void testArgumentHooks() {
        String[] args = {"-X", "foo.txt"};
        MainWithHooks m = new MainWithHooks(className, args);
        m.parseArguments();
        assertTrue(m.arghook[0], "PreArgumentsHook must be called");
        assertTrue(m.arghook[1], "PostArgumentsHook must be called");
    }

    @Test
    void testSplitHook() throws IOException {
        String[] args = {"-s", "-d", TMPDIR, "-S", TMPDIR + "/testmain.dat"};
        MainWithHooks m = new MainWithHooks(className, args);
        m.run();
        assertEquals(1, m.getFileArgs().size(), "Must process args and leave file");
        assertTrue(m.splithook[0], "PreSplitHook must be called");
        assertTrue(m.splithook[1], "PostSplitHook must be called");
        Path expectedSplitFile = Paths.get(TMPDIR, "testmain.dat." + Form.UNKNOWN);
        assertTrue(Files.exists(expectedSplitFile), "File must exist after split");
        Files.deleteIfExists(expectedSplitFile);
    }

    @Test
    void testExceptionHandlingInPlaceProcessing() {
        String[] args = {"-s", "-X", TMPDIR + "/testmain.dat"};
        Main m = new Main(OOMPlace.class.getName(), args);
        m.parseArguments();
        assertDoesNotThrow(m::run);
    }

    @Test
    void testMetaDataFromChildrenAndExtractedRecordsIsCaptured() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Main m = new Main("emissary.place.MainTest$PlaceTest", new String[] {"-m", ".*"});
        m.setOutputStream(new PrintStream(baos));
        m.run();

        IBaseDataObject payload = DataObjectFactory.getInstance("aaa".getBytes(), "test", "UNKNOWN");
        payload.putParameter("PARENT_KEY", "parent value");
        m.processPayload(payload);

        String s = baos.toString();
        assertTrue(s.contains("PARENT_KEY"), "Should have recorded parent metadata - " + s);
        assertTrue(s.contains("CHILD_KEY"), "Should have recorded child metadata - " + s);
        assertTrue(s.contains("RECORD_KEY"), "Should have recorded record metadata - " + s);
    }

    @Test
    void testHandleSplitOutput() throws IOException {
        Path outputFolder = Files.createDirectories(testOutputFolder.resolve("testmain-split.dat"));
        String[] args = {"-S", "-d", outputFolder.toRealPath().toString()};
        Main m = new Main(this.className, args);
        m.parseArguments();
        try {
            m.run();
        } catch (Throwable t) {
            fail("Main runner allowed exception to escape", t);
        }
        IBaseDataObject payload = DataObjectFactory.getInstance("aaa".getBytes(), "test", "UNKNOWN");
        List<IBaseDataObject> atts = new ArrayList<>();
        IBaseDataObject att1 = DataObjectFactory.getInstance("bbb".getBytes(), "safe_attempt", "SAFE_ATTEMPT");
        atts.add(att1);
        IBaseDataObject att2 = DataObjectFactory.getInstance("ccc".getBytes(), "escape_attempt", "./../../pwned/ESCAPE_ATTEMPT");
        atts.add(att2);

        IBaseDataObject att3 = DataObjectFactory.getInstance("ccc".getBytes(), "attempt", "./../../testmain.dat_sibling/ESCAPE_ATTEMPT");
        atts.add(att3);

        m.handleSplitOutput(payload, atts);

        // validate that output files "test.UNKNOWN" and "safe_attempt.SAFE_ATTEMPT" were created, but file
        // "escape_attempt../../../pwned/ESCAPE_ATTEMPT" was not
        String normalizedPath = outputFolder + "/" + payload.shortName() + "." + payload.currentForm();
        assertTrue(Files.exists(Paths.get(normalizedPath).normalize()), "File \"" + normalizedPath + "\" should have been created");

        normalizedPath = outputFolder + "/" + att1.shortName() + "." + att1.currentForm();
        assertTrue(Files.exists(Paths.get(normalizedPath).normalize()), "File \"" + normalizedPath + "\" should have been created");

        normalizedPath = outputFolder + "/" + att2.shortName() + "." + att2.currentForm();
        assertFalse(Files.exists(Paths.get(normalizedPath).normalize()), "File \"" + normalizedPath + "\" should have NOT been created");

        normalizedPath = outputFolder + "/" + att3.shortName() + "." + att3.currentForm();
        assertFalse(Files.exists(Paths.get(normalizedPath).normalize()), "File \"" + normalizedPath + "\" should have NOT been created");
    }

    @Test
    void testFilePathIsWithinBaseDirectory() {

        String basePath = testOutputFolder.resolve("foo").toString();
        assertEquals(basePath + "/somefile", filePathIsWithinBaseDirectory(basePath, basePath + "/somefile"));
        assertEquals(basePath + "/otherfile", filePathIsWithinBaseDirectory(basePath, basePath + "//otherfile"));
        assertEquals(basePath + "/foo/otherfile", filePathIsWithinBaseDirectory(basePath, basePath + "/./foo/otherfile"));
        assertEquals(basePath + "/sub/otherfile", filePathIsWithinBaseDirectory(basePath, basePath + "/sub/././otherfile"));

        // Each of these should thrown an Exception
        assertThrows(IllegalArgumentException.class, () -> filePathIsWithinBaseDirectory(basePath, "/var/log/somelog"));

        assertThrows(IllegalArgumentException.class,
                () -> filePathIsWithinBaseDirectory(basePath, basePath + "/../foo2/otherfile"),
                "Expected an IllegalArgumentException from input " + basePath + "/../foo2/otherfile");

        assertThrows(IllegalArgumentException.class,
                () -> filePathIsWithinBaseDirectory(basePath, basePath + "/../../somefile"),
                "Expected an IllegalArgumentException from input " + basePath + "/../../somefile");

        assertThrows(IllegalArgumentException.class,
                () -> filePathIsWithinBaseDirectory(basePath, basePath + "/path/../../../otherpath"),
                "Expected an IllegalArgumentException from input " + basePath + "/path/../../../otherpath");
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
        public List<IBaseDataObject> processHeavyDuty(IBaseDataObject payload) throws ResourceException {
            IBaseDataObject extr = DataObjectFactory.getInstance("cde".getBytes(), "test-att-2", "UNKNOWN");
            extr.putParameter("RECORD_KEY", "record value");
            payload.addExtractedRecord(extr);

            IBaseDataObject child = DataObjectFactory.getInstance("cde".getBytes(), "test-att-1", "UNKNOWN");
            payload.putParameter("CHILD_KEY", "child value");

            List<IBaseDataObject> children = new ArrayList<>();
            children.add(child);

            return children;
        }

        @Override
        public void process(IBaseDataObject payload) throws ResourceException {}
    }
}
