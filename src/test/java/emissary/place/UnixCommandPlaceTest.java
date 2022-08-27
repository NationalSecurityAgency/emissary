package emissary.place;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.test.core.junit5.UnitTest;
import emissary.util.io.ResourceReader;
import emissary.util.shell.Executrix;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class UnixCommandPlaceTest extends UnitTest {
    private UnixCommandPlace place;
    private static final Logger logger = LoggerFactory.getLogger(UnixCommandPlaceTest.class);
    private final Path scriptFile = Paths.get(TMPDIR, "testUnixCommand.sh");
    private static final String W = "Президент Буш";
    private IBaseDataObject payload;
    private final String FORM = "TEST";

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        // read our default config for this place, not something else that got configured in
        try (InputStream is = new ResourceReader().getConfigDataAsStream(this.getClass())) {
            place = new UnixCommandPlace(is);
            place.executrix.setTmpDir(TMPDIR);
            place.executrix.setCommand(TMPDIR + "/testUnixCommand.sh <INPUT_NAME> <OUTPUT_NAME>");
        } catch (Exception ex) {
            logger.error("Cannot create UnixCommandPlace", ex);
        }

        payload = DataObjectFactory.getInstance(new Object[] {"abcdefg".getBytes(), "myPayload", FORM});
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        place.shutDown();
        place = null;
        payload = null;
        Files.deleteIfExists(scriptFile);
        validateMockitoUsage();
    }

    @Test
    void testUnixCommandPlaceStdout() throws Exception {
        assertNotNull(place, "Place must be created");
        createScript(Executrix.OUTPUT_TYPE.STD);

        place.process(payload);
        byte[] altView = payload.getAlternateView("TEST_VIEW");
        assertNotNull(altView, "Alt view should have been created");
        assertEquals(FORM, payload.currentForm(), "Payload should have same current form");
        assertEquals(W, new String(altView).trim(), "Clean UTF-8 coming from the script must be maintained");
    }

    @Test
    void testUnixCommandPlaceFile() throws Exception {
        assertNotNull(place, "Place must be created");
        place.setFileOutputCommand();
        createScript(Executrix.OUTPUT_TYPE.FILE);

        place.process(payload);
        byte[] altView = payload.getAlternateView("TEST_VIEW");
        assertNotNull(altView, "Alt view should have been created");
        assertEquals(FORM, payload.currentForm(), "Payload should have same current form");
        assertEquals(W, new String(altView).trim(), "Clean UTF-8 coming from the script must be maintained");
    }

    @Test
    void testUnixCommandPlaceLogging() throws Exception {
        assertNotNull(place, "Place must be created");
        Logger mockLogger = mock(Logger.class);
        place.setLogger(mockLogger);
        createLogScript();
        place.process(payload);
        verify(mockLogger, times(LOG_MSGS.length)).info(anyString());
    }

    @Test
    void testFileProcess() throws Exception {
        Executrix e = mock(Executrix.class);

        // set up three possible scenarios and force return codes from the execute method
        when(e.execute(eq(new String[] {"negative"}), (StringBuilder) isNull(), isA(StringBuilder.class))).thenReturn(-1);
        when(e.execute(eq(new String[] {"zero"}), (StringBuilder) isNull(), isA(StringBuilder.class))).thenReturn(0);
        when(e.execute(eq(new String[] {"positive"}), (StringBuilder) isNull(), isA(StringBuilder.class))).thenReturn(1);

        place.setExecutrix(e);

        // fake an output file and load it with some data
        String DATA = "test-test";
        Path outputFile = Paths.get(TMPDIR, "output.out");

        try {
            IOUtils.write(DATA, Files.newOutputStream(outputFile), StandardCharsets.UTF_8);

            // null is returned in situations with a non-zero return code
            assertNull(place.fileProcess(new String[] {"negative"}, outputFile.toAbsolutePath().toString()));
            assertNull(place.fileProcess(new String[] {"positive"}, outputFile.toAbsolutePath().toString()));

            // a successful execution will return the bytes of the specified output file
            assertEquals(DATA, new String(place.fileProcess(new String[] {"zero"}, outputFile.toAbsolutePath().toString())));
        } finally {
            Files.deleteIfExists(outputFile);
        }
    }

    @Test
    void testStdOutProcess() {
        Executrix e = mock(Executrix.class);

        // set up three possible scenarios and force return codes from the execute method
        when(e.execute(eq(new String[] {"negative"}), isA(StringBuilder.class), isA(StringBuilder.class), eq(place.charset))).thenReturn(-1);
        when(e.execute(eq(new String[] {"zero"}), isA(StringBuilder.class), isA(StringBuilder.class), eq(place.charset))).thenReturn(0);
        when(e.execute(eq(new String[] {"positive"}), isA(StringBuilder.class), isA(StringBuilder.class), eq(place.charset))).thenReturn(1);

        place.setExecutrix(e);

        // null is returned in situations with a non-zero return code
        assertNull(place.stdOutProcess(new String[] {"negative"}, false));
        assertNull(place.stdOutProcess(new String[] {"positive"}, false));

        // we didn't actually execute anything, so the result is empty
        assertEquals("", new String(place.stdOutProcess(new String[] {"zero"}, false)));
    }

    private static final String[] LOG_MSGS = {"ERROR script error message", "WARN script warn message", "INFO script info message",
            "DEBUG script debug message"};

    private void createLogScript() throws IOException {
        try (OutputStream fos = startScript()) {

            // Add messages to the log file, name matched to serviceName from place key
            for (String msg : LOG_MSGS) {
                fos.write(("echo '" + msg + "' >> UCP.log\n").getBytes());
            }

            // Make some output
            fos.write("cat ${1} > ${2}\n".getBytes());
            scriptFile.toFile().setExecutable(true); // jdk 1.6+ only
        }
    }

    private OutputStream startScript() throws IOException {
        Files.deleteIfExists(scriptFile);
        OutputStream fos = Files.newOutputStream(scriptFile);
        fos.write("#!/bin/bash\n".getBytes());
        return fos;
    }

    private void createScript(Executrix.OUTPUT_TYPE ot) throws IOException {
        try (OutputStream fos = startScript()) {
            fos.write(("echo '" + W + "'").getBytes());
            if (ot == Executrix.OUTPUT_TYPE.FILE) {
                fos.write(" > ${2}".getBytes());
            }
            fos.write('\n');
            scriptFile.toFile().setExecutable(true); // jdk 1.6+ only
        }
    }

}
