package emissary.output;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
import emissary.test.core.UnitTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DropOffPlaceTest extends UnitTest {

    DropOffPlace place = null;
    DropOffUtil util = null;
    private Path tempDir;

    @Before
    public void createPlace() throws Exception {
        setUp();
        tempDir = Files.createTempDirectory("test");
        final Configurator cfg = new ServiceConfigGuide();
        cfg.addEntry("UNIX_ROOT", tempDir.toString());
        cfg.addEntry("OUTPUT_FILTER", "BLAH:emissary.output.filter.DataFilter");
        cfg.addEntry("OUTPUT_SPEC_BLAH", "%R%/xyzzy/%S%.%F%");
        this.place = new DropOffPlace(cfg);

    }

    @After
    public void teardown() throws Exception {
        super.tearDown();
        this.place.shutDown();
        this.place = null;
        cleanupDirectoryRecursively(tempDir);
    }

    @Test
    public void testNamedFilterSetup() {
        final IDropOffFilter f = this.place.getFilter("BLAH");
        assertNotNull("Filter specified by name must be found", f);
        assertEquals("Filter must have correct name", "BLAH", f.getFilterName());
        assertEquals("Filter must have found correct spec", "%R%/xyzzy/%S%.%F%", f.getOutputSpec());
    }

    @Test
    public void testWithNoValidOutputTypes() throws Exception {
        final IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is the data".getBytes());
        payload.setFileType("FTYPE");
        payload.setFilename("/this/is/a/testfile");
        final List<IBaseDataObject> payloadList = new ArrayList<IBaseDataObject>();
        payloadList.add(payload);
        final List<IBaseDataObject> val = this.place.agentProcessHeavyDuty(payloadList);
        assertEquals("All payloads still on list", 1, payloadList.size());
        assertEquals("Nothing returned from drop off", 0, val.size());
        assertEquals("All current forms removed", 0, payloadList.get(0).currentFormSize());
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
