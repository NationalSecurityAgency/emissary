package emissary.config;

import emissary.core.EmissaryException;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractResourceTest extends UnitTest {

    private final String origConfigDir = System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY);

    @AfterEach
    void restoreDir() throws EmissaryException {
        if (origConfigDir != null) {
            System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, origConfigDir);
        }
        ConfigUtil.initialize();
    }

    @Test
    void testWriteResourceKeepsResolvedSuffix(@TempDir final Path sourceDir, @TempDir final Path outputDir) throws Exception {
        Files.writeString(sourceDir.resolve("emissary.place.MyPlace.yaml"), "PLACE_NAME: MyPlace\n", UTF_8);
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, sourceDir.toString());
        ConfigUtil.initialize();

        final ExtractResource resource = new ExtractResource();
        resource.setOutputDirectory(outputDir.toString());
        resource.writeResource("emissary.place.MyPlace.yaml");

        final List<String> written = listOutput(outputDir);
        assertEquals(List.of("emissary.place.MyPlace.yaml"), written);
        assertEquals("MyPlace",
                ConfigUtil.getConfigInfo("emissary.place.MyPlace.yaml").findStringEntry("PLACE_NAME"));
    }

    @Test
    void testGetResourceWithSuffix(@TempDir final Path sourceDir) throws Exception {
        Files.writeString(sourceDir.resolve("Foo.yaml"), "FOO: from-yaml\n", UTF_8);
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, sourceDir.toString());
        ConfigUtil.initialize();
        assertEquals("FOO: from-yaml\n", new ExtractResource().getResource("Foo.yaml"));
    }

    @Test
    void testPropSuffixIsUnchanged(@TempDir final Path sourceDir, @TempDir final Path outputDir) throws Exception {
        Files.writeString(sourceDir.resolve("emissary.test.Thing.properties"), "FOO=bar\n", UTF_8);
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, sourceDir.toString());
        ConfigUtil.initialize();

        final ExtractResource resource = new ExtractResource();
        resource.setOutputDirectory(outputDir.toString());
        resource.writeResource("emissary.test.Thing.properties");

        final List<String> written = listOutput(outputDir);
        assertEquals(List.of("emissary.test.Thing.properties"), written);
        assertTrue(Files.readString(outputDir.resolve("emissary.test.Thing.properties")).contains("FOO=bar"));
    }

    private static List<String> listOutput(final Path outputDir) throws IOException {
        try (Stream<Path> files = Files.list(outputDir)) {
            return files.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
        }
    }

    @Test
    void testMissingResourceStillReportsMissing(@TempDir final Path sourceDir) throws Exception {
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, sourceDir.toString());
        ConfigUtil.initialize();
        assertTrue(assertThrows(IOException.class, () -> new ExtractResource().getResource("emissary.test.NoSuchThing"))
                .getMessage().contains("No config stream"));
    }
}
