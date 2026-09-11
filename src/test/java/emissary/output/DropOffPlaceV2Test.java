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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropOffPlaceV2Test extends UnitTest {

    @Nullable
    DropOffPlaceV2 place = null;
    private Path tempDir;

    @BeforeEach
    public void createPlace(@TempDir final Path tempDir) throws Exception {
        setUp();
        this.tempDir = tempDir;
        final Configurator cfg = new ServiceConfigGuide();
        cfg.addEntry("UNIX_ROOT", tempDir.toString());
        cfg.addEntry("OUTPUT_SINK", "BLAH:emissary.output.sink.JsonSink");
        cfg.addEntry("OUTPUT_PATH", tempDir.toString());
        this.place = new DropOffPlaceV2(cfg);
    }

    @AfterEach
    public void teardown() throws Exception {
        super.tearDown();
        this.place.shutDown();
        this.place = null;
        cleanupDirectoryRecursively(tempDir);
    }

    @Test
    void testNamedSinkSetup() {
        final ISink s = this.place.getSink("BLAH");
        assertNotNull(s, "Sink specified by name must be found");
        assertEquals("BLAH", s.getName(), "Sink must have correct name");
    }

    @Test
    void testHdProcessingWritesOutput() throws Exception {
        final IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is the data".getBytes());
        payload.setFileType("FTYPE");
        payload.setFilename("/this/is/a/testfile");
        final List<IBaseDataObject> payloadList = new ArrayList<>();
        payloadList.add(payload);

        final List<IBaseDataObject> val = this.place.agentProcessHeavyDuty(payloadList);
        assertEquals(0, val.size(), "Nothing returned from drop off");
        assertEquals(0, payloadList.get(0).currentFormSize(), "All current forms removed");

        // The formatter's fileNameGenerator creates the filename, so look for any .json file
        assertTrue(hasJsonFiles(tempDir), "Output JSON file should exist in: " + tempDir);
    }

    private static boolean hasJsonFiles(final Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile)
                    .anyMatch(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".json"));
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
