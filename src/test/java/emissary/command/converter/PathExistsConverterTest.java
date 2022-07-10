package emissary.command.converter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import emissary.test.core.UnitTest;
import emissary.util.io.UnitTestFileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PathExistsConverterTest extends UnitTest {

    private PathExistsConverter converter;
    private Path path;

    @BeforeEach
    public void setup() throws IOException {
        path = Files.createTempDirectory("config");
        converter = new PathExistsConverter("path");
    }

    @Override
    @AfterEach
    public void tearDown() throws IOException {
        UnitTestFileUtils.cleanupDirectoryRecursively(path);
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
    void convertFailed() {
        assertThrows(RuntimeException.class, () -> converter.convert("hello"));
    }

}
