package emissary.config;

import static emissary.util.io.UnitTestFileUtils.findFilesByExtension;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Stream;

import emissary.core.EmissaryException;
import emissary.test.core.FunctionalTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FTestServiceConfigGuide extends FunctionalTest {

    private static final Logger logger = LoggerFactory.getLogger(FTestServiceConfigGuide.class);

    public static Stream<? extends Arguments> data() throws IOException, EmissaryException {
        ConfigUtil.initialize();

        // look in config dir
        Collection<Path> configFiles = new ArrayList<>();

        for (String dir : ConfigUtil.getConfigDirs()) {
            configFiles.addAll(findFilesByExtension(Paths.get(dir), ".cfg"));
        }

        // look for cfg files under src
        Path root = Paths.get(ConfigUtil.projectRootDirectory()).getParent();
        configFiles.addAll(findFilesByExtension(Paths.get(root.toString(), "src"), ".cfg"));

        return configFiles.stream().map(p -> Arguments.of(p.toString()));
    }

    /**
     * Validates all config files in the "config" directory or down the src tree parse properly.
     */
    @ParameterizedTest
    @MethodSource("data")
    void testAllConfFiles(String resource) {
        Path underTest = Paths.get(resource).toAbsolutePath().normalize();
        logger.debug("Parsing config file:" + underTest);
        assertDoesNotThrow(() -> ConfigUtil.getConfigInfo(underTest.toString()));
    }
}
