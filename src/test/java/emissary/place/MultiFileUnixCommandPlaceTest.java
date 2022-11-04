package emissary.place;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.test.core.junit5.UnitTest;
import emissary.util.io.ResourceReader;
import emissary.util.io.UnitTestFileUtils;
import emissary.util.shell.Executrix;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MultiFileUnixCommandPlaceTest extends UnitTest {
    private MultiFileUnixCommandPlace place;
    private static final Logger logger = LoggerFactory.getLogger(MultiFileUnixCommandPlaceTest.class);
    private Path workDir;
    private File scriptFile;
    private static final String W = "Президент Буш";
    private IBaseDataObject payload;
    private static final String FORM = "TEST";
    private static final String PAYLOAD_STRING = "abcdefg";

    @BeforeEach
    public void setUp(@TempDir Path workDir) throws Exception {

        this.scriptFile = new File(TMPDIR, "testMultiFileUnixCommand.sh");

        this.workDir = workDir;

        // We do this to make sure the place
        // reads our default config for it
        // not something else that got configured in
        ResourceReader rr = new ResourceReader();
        try (InputStream is = rr.getConfigDataAsStream(this.getClass())) {
            place = new MultiFileUnixCommandPlace(is);
            place.executrix.setTmpDir(workDir.toAbsolutePath().toString());
            place.executrix.setTmpDirFile(new File(workDir.toAbsolutePath().toString()));
            place.executrix.setCommand(TMPDIR + "/testMultiFileUnixCommand.sh <INPUT_NAME>");
        } catch (Exception ex) {
            logger.error("Cannot create MultiFileUnixCommandPlace", ex);
        }

        payload = DataObjectFactory.getInstance(new Object[] {PAYLOAD_STRING.getBytes(UTF_8), "myPayload", FORM});

        payload.putParameter("COPY_THIS", "copy value");
        payload.putParameter("IGNORE_THIS", "ignore value");
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        place = null;
        payload = null;
        Files.deleteIfExists(scriptFile.toPath());
        UnitTestFileUtils.cleanupDirectoryRecursively(workDir);
        validateMockitoUsage();
    }

    @Test
    void testMultiFileUnixCommandPlaceStdout() throws Exception {
        assertNotNull(place, "Place must be created");
        createScript(Executrix.OUTPUT_TYPE.STD, 2);

        List<IBaseDataObject> att = place.processHeavyDuty(payload);
        assertEquals(1, att.size(), "Attachments should be created");
        assertEquals("UNKNOWN", att.get(0).currentForm(), "Attachment current form set");
        assertEquals(W, new String(att.get(0).data(), UTF_8).trim(), "Clean UTF-8 coming from the script must in attachment");
        assertEquals("UCP-PROCESSED", payload.currentForm(), "Payload should have configured current form");
        assertEquals(W, new String(payload.data(), UTF_8).trim(), "Clean UTF-8 coming from script must be in parent");
        assertEquals(1, payload.currentFormSize(), "Single form remaining for parent");
        assertEquals(1, att.get(0).currentFormSize(), "Single form for child");

        assertEquals("copy value", att.get(0).getStringParameter("COPY_THIS"), "Child should have propagating metadata value");
        assertNull(att.get(0).getStringParameter("IGNORE_THIS"), "Child should not have non-propagating metadata value");
    }

    @Test
    void testMultiFileUnixCommandPlaceFile() throws Exception {
        assertNotNull(place, "Place must be created");
        place.setFileOutputCommand();
        createScript(Executrix.OUTPUT_TYPE.FILE, 2);

        List<IBaseDataObject> att = place.processHeavyDuty(payload);
        assertEquals("UCP-PROCESSED", payload.currentForm(), "Payload should have configured current form");
        assertEquals(2, att.size(), "Attachments should be created");
        assertEquals("UNKNOWN", att.get(0).currentForm(), "Attachment current form set");
        assertEquals(W, new String(att.get(0).data(), UTF_8).trim(), "Clean UTF-8 coming from the script must be maintained");
        assertEquals(1, payload.currentFormSize(), "Single form remaining for parent");
        assertEquals(1, att.get(0).currentFormSize(), "Single form for child");

        assertEquals("copy value", att.get(0).getStringParameter("COPY_THIS"), "Child should have propagating metadata value");
        assertNull(att.get(0).getStringParameter("IGNORE_THIS"), "Child should not have non-propagating metadata value");
    }

    @Test
    void testMultiFileUnixCommandPlaceFileWithSingleChildHandling() throws Exception {
        assertNotNull(place, "Place must be created");
        place.setFileOutputCommand();
        createScript(Executrix.OUTPUT_TYPE.FILE, 1);

        List<IBaseDataObject> att = place.processHeavyDuty(payload);
        assertEquals(0, att.size(), "Attachments should not be created");
        assertEquals("UNKNOWN", payload.currentForm(), "Current form set due to processing");
        assertEquals(W, new String(payload.data(), UTF_8).trim(), "Clean UTF-8 coming from the script must be maintained");
        assertEquals(1, payload.currentFormSize(), "Single form remaining for parent");

        assertEquals("copy value", payload.getStringParameter("COPY_THIS"), "Parent should have propagating metadata value");
        assertEquals("ignore value", payload.getStringParameter("IGNORE_THIS"), "Parent should still have non-propagating metadata value");
    }

    @Test
    void testMultiFileUnixCommandPlaceFileWithSingleChildAsOutput() throws Exception {
        assertNotNull(place, "Place must be created");
        place.setFileOutputCommand();
        createScript(Executrix.OUTPUT_TYPE.FILE, 1);

        place.preserveParentData = true;
        place.singleOutputAsChild = true;

        List<IBaseDataObject> att = place.processHeavyDuty(payload);
        assertEquals(1, att.size(), "One Attachment should be created");
        assertEquals(PAYLOAD_STRING, new String(payload.data(), UTF_8), "Parent data should be preserved");
        assertEquals(W, new String(att.get(0).data(), UTF_8).trim(), "Child payload should match script output");
    }

    @Test
    void testMultiFileUnixCommandPlaceLogging() throws Exception {
        assertNotNull(place, "Place must be created");
        Logger mockLogger = mock(Logger.class);
        place.setLogger(mockLogger);
        createLogScript();
        place.processHeavyDuty(payload);
        verify(mockLogger, times(LOG_MSGS.length)).info(anyString());

    }

    private static final String[] LOG_MSGS = {"ERROR script error message", "WARN script warn message", "INFO script info message",
            "DEBUG script debug message"};

    private void createLogScript() throws IOException {
        try (OutputStream fos = startScript()) {

            // Add messages to the log file, name matched to serviceName from place key
            for (String msg : LOG_MSGS) {
                fos.write(("echo '" + msg + "' >> UCP.log\n").getBytes(UTF_8));
            }

            // Make some output
            fos.write("cat ${1} ${2}\n".getBytes(UTF_8));
            scriptFile.setExecutable(true); // jdk 1.6+ only
        }
    }

    private OutputStream startScript() throws IOException {
        Files.deleteIfExists(scriptFile.toPath());
        OutputStream fos = Files.newOutputStream(scriptFile.toPath());
        fos.write("#!/bin/bash\n".getBytes(UTF_8));
        return fos;
    }

    private void createScript(Executrix.OUTPUT_TYPE ot, int outputCount) throws IOException {
        try (OutputStream fos = startScript()) {
            // Write a line to either stdout or outfile.one
            fos.write(("echo '" + W + "'").getBytes(UTF_8));
            if (ot == Executrix.OUTPUT_TYPE.FILE) {
                fos.write(" > outfile.one".getBytes(UTF_8));
            }
            fos.write('\n');

            // Write a line to outfile.two
            if (outputCount == 2) {
                fos.write(("echo '" + W + "'").getBytes(UTF_8));
                fos.write(" > outfile.two".getBytes(UTF_8));
                fos.write('\n');
            }

            scriptFile.setExecutable(true); // jdk 1.6+ only
        }
    }

}
