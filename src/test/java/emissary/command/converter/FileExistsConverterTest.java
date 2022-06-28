package emissary.command.converter;

import emissary.test.core.junit5.UnitTest;
import emissary.util.io.UnitTestFileUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileExistsConverterTest extends UnitTest {

    private FileExistsConverter converter;
    private Path path;

    @BeforeEach
    public void setup() throws IOException {
        path = Files.createTempDirectory("config");
        converter = new FileExistsConverter("path");
    }

    @Override
    @AfterEach
    public void tearDown() throws IOException {
        UnitTestFileUtils.cleanupDirectoryRecursively(path);
    }

    @Test
    void noTrailingSlash() {
        // test
        File result = converter.convert(path.toString());

        // verify
        assertFalse(result.toPath().endsWith("/"));
    }

    @Test
    void removeTrailingSlash() {
        // test
        File result = converter.convert(path.toString() + "/");

        // verify
        assertFalse(result.toPath().endsWith("/"));
    }

    @Test
    void convertFailed() {
        assertThrows(IllegalArgumentException.class, () -> converter.convert("hello"));
    }

}
