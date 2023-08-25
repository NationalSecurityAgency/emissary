package emissary.command.converter;

import emissary.test.core.junit5.UnitTest;
import emissary.util.io.UnitTestFileUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectBaseConverterTest extends UnitTest {

    private ProjectBaseConverter converter;
    private Path path;

    @BeforeEach
    public void setup() throws IOException {
        path = Files.createTempDirectory("config");
        converter = new ProjectBaseConverter("base");
    }

    @Override
    @AfterEach
    public void tearDown() throws IOException {
        UnitTestFileUtils.cleanupDirectoryRecursively(path);
    }

    @Test
    void convert() {
        Path result = converter.convert(System.getenv("PROJECT_BASE"));
        assertFalse(result.endsWith("/"));
    }

    @Test
    void convertNull() {
        Path result = converter.convert(null);
        assertFalse(result.endsWith("/"));
    }

    @Test
    void convertNotMatching() {
        // test
        String projectBase = path.toString() + "/";
        assertThrows(IllegalArgumentException.class, () -> converter.convert(projectBase));
    }

}
