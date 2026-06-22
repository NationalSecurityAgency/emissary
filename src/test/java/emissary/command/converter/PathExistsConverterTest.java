package emissary.command.converter;

import emissary.test.core.junit5.UnitTest;
import emissary.util.io.UnitTestFileUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        assertThrows(IllegalArgumentException.class, () -> converter.convert("hello"));
    }

    @Test
    void blankRejected() {
        assertThrows(IllegalArgumentException.class, () -> converter.convert(""));
        assertThrows(IllegalArgumentException.class, () -> converter.convert("   "));
    }

    @Test
    void trimsWhitespace() {
        // leading/trailing whitespace is trimmed before resolving the path
        Path result = converter.convert("  " + path + "  ");

        assertEquals(path, result);
    }

}
