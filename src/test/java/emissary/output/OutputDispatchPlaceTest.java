package emissary.output;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.output.sink.ISink;
import emissary.test.core.junit5.UnitTest;

import jakarta.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputDispatchPlaceTest extends UnitTest {

    @Nullable
    OutputDispatchPlace place = null;
    private Path tempDir;

    @BeforeEach
    public void createPlace(@TempDir final Path tempDir) throws Exception {
        setUp();
        this.tempDir = tempDir;
        final Configurator cfg = new ServiceConfigGuide();
        cfg.addEntry("UNIX_ROOT", tempDir.toString());
        cfg.addEntry("OUTPUT_SINK", "BLAH:emissary.output.sink.JsonSink");
        cfg.addEntry("OUTPUT_PATH", tempDir.toString());
        this.place = new OutputDispatchPlace(cfg);
    }

    @AfterEach
    public void teardown() throws Exception {
        super.tearDown();
        this.place.shutDown();
        this.place = null;
        cleanupDirectoryRecursively(tempDir);
    }

    @Test
    void testSinkNames() {
        final ISink s = this.place.getSink("BLAH");
        assertNotNull(s, "Sink specified by name must be found");
        assertEquals("BLAH", s.getName(), "Sink must have correct name");
        assertNotNull(this.place.getSink("blah"), "Sink lookup must be case-insensitive");
    }

    @Test
    void configuresOneJournalDirectoryPerShippedSink(@TempDir final Path root) throws Exception {
        final ServiceConfigGuide cfg = new ServiceConfigGuide();
        cfg.addEntry("OUTPUT_PATH", root.toString());
        cfg.addEntry("OUTPUT_SINK", "emissary.output.sink.JsonSink");
        cfg.addEntry("OUTPUT_SINK", "emissary.output.sink.JsonMetadataSink");
        final OutputDispatchPlace dispatcher = new OutputDispatchPlace(cfg);
        try {
            assertEquals(List.of("JSON", "JSONM"), dispatcher.getSinkNamesList());
            final ISink json = dispatcher.getSink("json");
            final ISink jsonm = dispatcher.getSink("JSONM");
            assertNotNull(json);
            assertNotNull(jsonm);
            assertEquals(root.resolve("json"), json.getOutputDirectory());
            assertEquals(root.resolve("jsonm"), jsonm.getOutputDirectory());
        } finally {
            dispatcher.shutDown();
        }
    }

    @Test
    void testHdWrite() throws Exception {
        final IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is the data".getBytes(UTF_8));
        payload.setFileType("FTYPE");
        payload.setFilename("/this/is/a/testfile");
        final List<IBaseDataObject> payloadList = new ArrayList<>();
        payloadList.add(payload);

        final List<IBaseDataObject> val = this.place.agentProcessHeavyDuty(payloadList);
        assertEquals(0, val.size(), "Nothing returned from drop off");
        assertEquals(0, payloadList.get(0).currentFormSize(), "All current forms removed");

        // The roller framework writes to .bgpart files initially; final .json file appears after roll
        assertTrue(hasBgpartFiles(this.tempDir), "Output .bgpart file should exist in: " + this.tempDir);
    }

    @Test
    void testEmptyPayloadList() throws Exception {
        assertEquals(0, this.place.agentProcessHeavyDuty(new ArrayList<>()).size(), "Empty input is a no-op");
        assertFalse(hasBgpartFiles(this.tempDir), "Empty input must not write output");
    }

    @Test
    void testNotOutputableDeniedByPolicy() throws Exception {
        final IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is the data".getBytes(UTF_8));
        payload.setFileType("FTYPE");
        payload.setFilename("/this/is/a/testfile");
        payload.setOutputable(false);
        final List<IBaseDataObject> payloadList = new ArrayList<>();
        payloadList.add(payload);

        assertEquals(0, this.place.agentProcessHeavyDuty(payloadList).size(), "Nothing returned from drop off");
        assertFalse(hasBgpartFiles(this.tempDir), "Denied payload must not be written");
    }

    private static boolean hasBgpartFiles(final Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile)
                    .anyMatch(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".bgpart"));
        }
    }

    public static void cleanupDirectoryRecursively(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
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
