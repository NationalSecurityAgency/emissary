package emissary.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.test.core.UnitTest;
import emissary.util.io.ResourceReader;
import emissary.util.shell.Executrix;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiFileUnixCommandPlaceTest extends UnitTest {
    private MultiFileUnixCommandPlace place;
    private static Logger logger = LoggerFactory.getLogger(MultiFileUnixCommandPlaceTest.class);
    private static String tmpdir = System.getProperty("java.io.tmpdir", ".").replace('\\', '/');
    private File scriptFile = new File(tmpdir, "testMultiFileUnixCommand.sh");
    private static String W = "Президент Буш";
    private IBaseDataObject payload;
    private String FORM = "TEST";

    @Override
    @Before
    public void setUp() throws Exception {


        // We do this to make sure the place
        // reads our default config for it
        // not something else that got configured in
        ResourceReader rr = new ResourceReader();
        InputStream is = null;
        try {
            is = rr.getConfigDataAsStream(this.getClass());
            place = new MultiFileUnixCommandPlace(is);
        } catch (Exception ex) {
            logger.error("Cannot create MultiFileUnixCommandPlace", ex);
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException ignore) {
                // empty catch block
            }
        }

        payload = DataObjectFactory.getInstance(new Object[] {"abcdefg".getBytes(), "myPayload", FORM});

        payload.putParameter("COPY_THIS", "copy value");
        payload.putParameter("IGNORE_THIS", "ignore value");

    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        place = null;
        payload = null;
        if (scriptFile.exists()) {
            scriptFile.delete();
        }
        validateMockitoUsage();
    }

    @Test
    public void testMultiFileUnixCommandPlaceStdout() throws Exception {
        assertNotNull("Place must be created", place);
        createScript(Executrix.OUTPUT_TYPE.STD, 2);

        List<IBaseDataObject> att = place.processHeavyDuty(payload);
        assertEquals("Attachments should be created", 1, att.size());
        assertEquals("Attachment current form set", "UNKNOWN", att.get(0).currentForm());
        assertEquals("Clean UTF-8 coming from the script must in attachment", W, new String(att.get(0).data()).trim());
        assertEquals("Payload should have configured current form", "UCP-PROCESSED", payload.currentForm());
        assertEquals("Clean UTF-8 coming from script must be in parent", W, new String(payload.data()).trim());
        assertEquals("Single form remaining for parent", 1, payload.currentFormSize());
        assertEquals("Single form for child", 1, att.get(0).currentFormSize());

        assertEquals("Child should have propagating metadata value", "copy value", att.get(0).getStringParameter("COPY_THIS"));
        assertNull("Child should not have non-propagating metadata value", att.get(0).getStringParameter("IGNORE_THIS"));
    }

    @Test
    public void testMultiFileUnixCommandPlaceFile() throws Exception {
        assertNotNull("Place must be created", place);
        place.setFileOutputCommand();
        createScript(Executrix.OUTPUT_TYPE.FILE, 2);

        List<IBaseDataObject> att = place.processHeavyDuty(payload);
        assertEquals("Payload should have configured current form", "UCP-PROCESSED", payload.currentForm());
        assertEquals("Attachments should be created", 2, att.size());
        assertEquals("Attachment current form set", "UNKNOWN", att.get(0).currentForm());
        assertEquals("Clean UTF-8 coming from the script must be maintained", W, new String(att.get(0).data()).trim());
        assertEquals("Single form remaining for parent", 1, payload.currentFormSize());
        assertEquals("Single form for child", 1, att.get(0).currentFormSize());

        assertEquals("Child should have propagating metadata value", "copy value", att.get(0).getStringParameter("COPY_THIS"));
        assertNull("Child should not have non-propagating metadata value", att.get(0).getStringParameter("IGNORE_THIS"));
    }

    @Test
    public void testMultiFileUnixCommandPlaceFileWithSingleChildHandling() throws Exception {
        assertNotNull("Place must be created", place);
        place.setFileOutputCommand();
        createScript(Executrix.OUTPUT_TYPE.FILE, 1);

        List<IBaseDataObject> att = place.processHeavyDuty(payload);
        assertEquals("Attachments should not be created", 0, att.size());
        assertEquals("Current form set due to processing", "UNKNOWN", payload.currentForm());
        assertEquals("Clean UTF-8 coming from the script must be maintained", W, new String(payload.data()).trim());
        assertEquals("Single form remaining for parent", 1, payload.currentFormSize());

        assertEquals("Parent should have propagating metadata value", "copy value", payload.getStringParameter("COPY_THIS"));
        assertEquals("Parent should still have non-propagating metadata value", "ignore value", payload.getStringParameter("IGNORE_THIS"));
    }

    @Test
    public void testMultiFileUnixCommandPlaceLogging() throws Exception {
        assertNotNull("Place must be created", place);
        Logger mockLogger = mock(Logger.class);
        place.setLogger(mockLogger);
        createLogScript();
        place.processHeavyDuty(payload);
        verify(mockLogger, times(LOG_MSGS.length)).info(anyString());

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
        fos.write("cat ${1} ${2}\n".getBytes());
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

    private void createScript(Executrix.OUTPUT_TYPE ot, int outputCount) throws IOException {
        FileOutputStream fos = startScript();

        // Write a line to either stdout or outfile.one
        fos.write(("echo '" + W + "'").getBytes());
        if (ot == Executrix.OUTPUT_TYPE.FILE) {
            fos.write(" > outfile.one".getBytes());
        }
        fos.write('\n');

        // Write a line to outfile.two
        if (outputCount == 2) {
            fos.write(("echo '" + W + "'").getBytes());
            fos.write(" > outfile.two".getBytes());
            fos.write('\n');
        }

        fos.close();
        scriptFile.setExecutable(true); // jdk 1.6+ only
    }

}
