package emissary.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.test.core.UnitTest;
import emissary.util.io.ResourceReader;
import emissary.util.shell.Executrix;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnixCommandPlaceTest extends UnitTest {
    private UnixCommandPlace place;
    private static Logger logger = LoggerFactory.getLogger(UnixCommandPlaceTest.class);
    private static String tmpdir = System.getProperty("java.io.tmpdir", ".").replace('\\', '/');
    private File scriptFile = new File(tmpdir, "testUnixCommand.sh");
    private static String W = "Президент Буш";
    private IBaseDataObject payload;
    private String FORM = "TEST";

    @Override
    @Before
    public void setUp() throws Exception {
        // read our default config for this place, not something else that got configured in
        try (InputStream is = new ResourceReader().getConfigDataAsStream(this.getClass())) {
            place = new UnixCommandPlace(is);
        } catch (Exception ex) {
            logger.error("Cannot create UnixCommandPlace", ex);
        }

        payload = DataObjectFactory.getInstance(new Object[] {"abcdefg".getBytes(), "myPayload", FORM});
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        place.shutDown();
        place = null;
        payload = null;
        if (scriptFile.exists()) {
            scriptFile.delete();
        }
        validateMockitoUsage();
    }

    @Test
    public void testUnixCommandPlaceStdout() throws Exception {
        assertNotNull("Place must be created", place);
        createScript(Executrix.OUTPUT_TYPE.STD);

        place.process(payload);
        byte[] altView = payload.getAlternateView("TEST_VIEW");
        assertNotNull("Alt view should have been created", altView);
        assertEquals("Payload should have same current form", FORM, payload.currentForm());
        assertEquals("Clean UTF-8 coming from the script must be maintained", W, new String(altView).trim());
    }

    @Test
    public void testUnixCommandPlaceFile() throws Exception {
        assertNotNull("Place must be created", place);
        place.setFileOutputCommand();
        createScript(Executrix.OUTPUT_TYPE.FILE);

        place.process(payload);
        byte[] altView = payload.getAlternateView("TEST_VIEW");
        assertNotNull("Alt view should have been created", altView);
        assertEquals("Payload should have same current form", FORM, payload.currentForm());
        assertEquals("Clean UTF-8 coming from the script must be maintained", W, new String(altView).trim());
    }

    @Test
    public void testUnixCommandPlaceLogging() throws Exception {
        assertNotNull("Place must be created", place);
        Logger mockLogger = mock(Logger.class);
        place.setLogger(mockLogger);
        createLogScript();
        place.process(payload);
        verify(mockLogger, times(LOG_MSGS.length)).info(anyString());
    }

    @Test
    public void testFileProcess() throws Exception {
        Executrix e = mock(Executrix.class);

        // set up three possible scenarios and force return codes from the execute method
        when(e.execute(eq(new String[] {"negative"}), isNull(StringBuilder.class), isA(StringBuilder.class))).thenReturn(-1);
        when(e.execute(eq(new String[] {"zero"}), isNull(StringBuilder.class), isA(StringBuilder.class))).thenReturn(0);
        when(e.execute(eq(new String[] {"positive"}), isNull(StringBuilder.class), isA(StringBuilder.class))).thenReturn(1);

        place.setExecutrix(e);

        // fake an output file and load it with some data
        String DATA = new String("test-test");
        File outputFile = new File(tmpdir, "output.out");
        IOUtils.write(DATA, new FileOutputStream(outputFile));

        // null is returned in situations with a non-zero return code
        assertNull(place.fileProcess(new String[] {"negative"}, outputFile.getAbsolutePath()));
        assertNull(place.fileProcess(new String[] {"positive"}, outputFile.getAbsolutePath()));

        // a successful execution will return the bytes of the specified output file
        assertEquals(DATA, new String(place.fileProcess(new String[] {"zero"}, outputFile.getAbsolutePath())));
    }

    @Test
    public void testStdOutProcess() throws Exception {
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
        FileOutputStream fos = startScript();

        // Add messages to the log file, name matched to serviceName from place key
        for (String msg : LOG_MSGS) {
            fos.write(("echo '" + msg + "' >> UCP.log\n").getBytes());
        }

        // Make some output
        fos.write("cat ${1} > ${2}\n".getBytes());
        fos.close();
        scriptFile.setExecutable(true); // jdk 1.6+ only
    }

    private FileOutputStream startScript() throws IOException {
        if (scriptFile.exists()) {
            scriptFile.delete();
        }
        FileOutputStream fos = new FileOutputStream(scriptFile);
        fos.write("#!/bin/bash\n".getBytes());
        return fos;
    }

    private void createScript(Executrix.OUTPUT_TYPE ot) throws IOException {
        FileOutputStream fos = startScript();
        fos.write(("echo '" + W + "'").getBytes());
        if (ot == Executrix.OUTPUT_TYPE.FILE) {
            fos.write(" > ${2}".getBytes());
        }
        fos.write('\n');
        fos.close();
        scriptFile.setExecutable(true); // jdk 1.6+ only
    }

}
