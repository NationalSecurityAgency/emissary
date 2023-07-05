package emissary.output;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.output.filter.IDropOffFilter;
import emissary.test.core.junit5.UnitTest;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropOffPlaceTest extends UnitTest {

    DropOffPlace place = null;
    private Path tempDir;

    @BeforeEach
    public void createPlace(@TempDir final Path tempDir) throws Exception {
        setUp();
        this.tempDir = tempDir;
        final Configurator cfg = new ServiceConfigGuide();
        cfg.addEntry("UNIX_ROOT", tempDir.toString());
        cfg.addEntry("OUTPUT_FILTER", "BLAH:emissary.output.filter.DataFilter");
        cfg.addEntry("OUTPUT_SPEC_BLAH", "%R%/xyzzy/%S%.%F%");
        cfg.addEntry("OUTPUT_COMPLETION_PAYLOAD_SIZE", "TRUE");
        this.place = new DropOffPlace(cfg);
    }

    @AfterEach
    public void teardown() throws Exception {
        super.tearDown();
        this.place.shutDown();
        this.place = null;
        cleanupDirectoryRecursively(tempDir);
    }

    @Test
    void testNamedFilterSetup() {
        final IDropOffFilter f = this.place.getFilter("BLAH");
        assertNotNull(f, "Filter specified by name must be found");
        assertEquals("BLAH", f.getFilterName(), "Filter must have correct name");
        assertEquals("%R%/xyzzy/%S%.%F%", f.getOutputSpec(), "Filter must have found correct spec");
    }

    @Test
    void testWithNoValidOutputTypes() throws Exception {
        final IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is the data".getBytes());
        payload.setFileType("FTYPE");
        payload.setFilename("/this/is/a/testfile");
        final List<IBaseDataObject> payloadList = new ArrayList<>();
        payloadList.add(payload);
        final List<IBaseDataObject> val = this.place.agentProcessHeavyDuty(payloadList);
        assertEquals(1, payloadList.size(), "All payloads still on list");
        assertEquals(0, val.size(), "Nothing returned from drop off");
        assertEquals(0, payloadList.get(0).currentFormSize(), "All current forms removed");
    }


    @Test
    void testOutputMessageContainsPayloadSize() throws Exception {
        final LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        final Logger rootLogger = lc.getLogger(Logger.ROOT_LOGGER_NAME);

        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        byte[] content = "This is the data".getBytes();
        String expected = "payload size: " + content.length + " bytes";

        try {

            appender.start();
            rootLogger.addAppender(appender);

            final IBaseDataObject payload = DataObjectFactory.getInstance();
            payload.setData(content);

            payload.setCurrentForm("FOO");
            payload.setFileType("FTYPE");
            payload.setFilename("/this/is/a/testfile");
            final List<IBaseDataObject> payloadList = new ArrayList<>();
            payloadList.add(payload);

            final List<IBaseDataObject> val = this.place.agentProcessHeavyDuty(payloadList);
            assertTrue(appender.list.stream().anyMatch(i -> i.getFormattedMessage().endsWith(expected)));
        } finally {
            rootLogger.detachAppender(appender);
            appender.stop();
        }
    }


    @Test
    void testOutputMessageHandlesNullPayloadArray() throws Exception {
        final LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        final Logger rootLogger = lc.getLogger(Logger.ROOT_LOGGER_NAME);

        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        String fileType = "FTYPE";
        String expected = "with filetype: " + fileType;

        try {
            appender.start();
            rootLogger.addAppender(appender);

            final IBaseDataObject payload = DataObjectFactory.getInstance();
            payload.setCurrentForm("FOO");
            payload.setFileType(fileType);
            payload.setFilename("/this/is/a/testfile");

            // payload.setData(xx); is never called; this causes DataFilter.filter to fail,
            // but that's outside the scope of this test's concern

            final List<IBaseDataObject> payloadList = new ArrayList<>();
            payloadList.add(payload);

            final List<IBaseDataObject> val = this.place.agentProcessHeavyDuty(payloadList);
            assertTrue(appender.list.stream().anyMatch(i -> i.getFormattedMessage().endsWith(expected)));
        } finally {
            rootLogger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void testOutputObjectMetric() throws ParseException, JsonProcessingException {
        // Setup
        final IBaseDataObject tld = DataObjectFactory.getInstance();
        tld.setId("TEST-UUID-VALUE");
        tld.setFileType("test-type");
        tld.setParameter("FLOW", "test-flow");
        Date currentData = new Date();
        Date upstreamDropOff = new Date(currentData.getTime() - (30 * 60000));
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        tld.setParameter("UPSTREAM-DROPOFF", df.format(upstreamDropOff));
        List<String> tldMetricsFields = new ArrayList<>();
        tldMetricsFields.add("FILETYPE");
        tldMetricsFields.add("FLOW");

        // Run
        Map<String, String> outputMap = place.outputObjectMetrics(tld, tldMetricsFields);

        // Verify
        assertTrue(outputMap.containsKey("FILETYPE"));
        assertEquals("test-type", outputMap.get("FILETYPE"));
        assertTrue(outputMap.containsKey("FLOW"));
        assertEquals("test-flow", outputMap.get("FLOW"));
        assertTrue(outputMap.containsKey("ProcessingLatency"));
        assertTrue(outputMap.containsKey("InternalId"));
    }

    public static void cleanupDirectoryRecursively(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
