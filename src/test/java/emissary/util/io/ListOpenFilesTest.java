package emissary.util.io;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import emissary.test.core.UnitTest;
import emissary.util.shell.Executrix;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ListOpenFilesTest extends UnitTest {

    private ListOpenFiles instance;
    private Path tmpDir;
    private Path file;

    @BeforeEach
    public void setUp(@TempDir final Path tmpDir) throws Exception {
        super.setUp();
        this.instance = new ListOpenFiles();
        this.tmpDir = tmpDir;
        this.file = Paths.get(tmpDir.toString(), "open");
        Files.write(file, "test".getBytes(UTF_8));
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        UnitTestFileUtils.cleanupDirectoryRecursively(this.tmpDir);
        super.tearDown();
    }

    @Test
    void isOpen() throws IOException {
        assumeTrue(new Executrix().execute(new String[] {"lsof", "-v"}) == 0);

        // open a file an test
        try (InputStream stream = Files.newInputStream(file)) {
            assertTrue(instance.isOpen(file));
        }
        assertFalse(instance.isOpen(file));

        // test for a file that does not exist
        assertFalse(instance.isOpen(Paths.get(tmpDir.toString(), "dne")));
    }
}
