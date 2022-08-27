package emissary.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.output.filter.IDropOffFilter;
import emissary.test.core.junit5.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DropOffPlaceTest extends UnitTest {

    DropOffPlace place = null;
    private Path tempDir;

    @BeforeEach
    public void createPlace() throws Exception {
        setUp();
        tempDir = Files.createTempDirectory("test");
        final Configurator cfg = new ServiceConfigGuide();
        cfg.addEntry("UNIX_ROOT", tempDir.toString());
        cfg.addEntry("OUTPUT_FILTER", "BLAH:emissary.output.filter.DataFilter");
        cfg.addEntry("OUTPUT_SPEC_BLAH", "%R%/xyzzy/%S%.%F%");
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
