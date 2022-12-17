package emissary.command.converter;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathExistsReadableConverterTest extends UnitTest {

    private PathExistsReadableConverter converter;
    private Path path;

    @BeforeEach
    public void setup(@TempDir final Path path) throws IOException {
        this.path = path;
        converter = new PathExistsReadableConverter("path");
    }

    @Test
    void noTrailingSlash() {
        // test
        Path result = converter.convert(path.toString());

        // verify
        assertFalse(result.endsWith("/"));
    }

    @Test
    void removeTrailingSlash() {
        // test
        Path result = converter.convert(path.toString() + "/");

        // verify
        assertFalse(result.endsWith("/"));
    }

    @Test
    void unreadablePath() throws Exception {
        // setup
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_WRITE);
        Files.setPosixFilePermissions(path, perms);

        // test
        String cnvrt = path.toString();
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> converter.convert(cnvrt));

        // verify
        assertTrue(thrown.getMessage().contains("The option 'path' was configured with path '" + path + "' which is not readable"));

        // reset perms for cleanup
        perms.add(PosixFilePermission.OWNER_READ);
        Files.setPosixFilePermissions(path, perms);
    }
}
