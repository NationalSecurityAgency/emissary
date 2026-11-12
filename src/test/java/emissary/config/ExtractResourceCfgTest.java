package emissary.config;

import emissary.core.EmissaryException;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ExtractResourceCfgTest extends UnitTest {

    private final String origConfigDir = System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY);

    @AfterEach
    void restoreDir() throws EmissaryException {
        if (origConfigDir != null) {
            System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, origConfigDir);
        }
        ConfigUtil.initialize();
    }

    @Test
    void suffixlessCfgNameIsDiscovered(@TempDir final Path sourceDir, @TempDir final Path outputDir) throws Exception {
        Files.writeString(sourceDir.resolve("emissary.place.MyPlace.cfg"), "PLACE_NAME = MyPlace\n", UTF_8);
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, sourceDir.toString());
        ConfigUtil.initialize();

        final ExtractResource resource = new ExtractResource();
        resource.setOutputDirectory(outputDir.toString());
        resource.writeResource("emissary.place.MyPlace");

        try (Stream<Path> files = Files.list(outputDir)) {
            final List<String> written = files.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
            assertEquals(List.of("emissary.place.MyPlace.cfg"), written);
        }
    }
}
