package emissary.config;

import static emissary.config.ConfigUtil.getConfigDirs;
import static emissary.util.io.UnitTestFileUtils.findFilesByExtension;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

import emissary.core.EmissaryException;
import emissary.test.core.FunctionalTest;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FTestServiceConfigGuide extends FunctionalTest {

    private static final Logger logger = LoggerFactory.getLogger(FTestServiceConfigGuide.class);

    public static Collection<String> data() throws IOException, EmissaryException {
        // look in config dir
        Collection<Path> configFiles = new ArrayList<>();
        for (String dir : getConfigDirs()) {
            configFiles.addAll(findFilesByExtension(Paths.get(dir), ".cfg"));
        }

        // look for cfg files under src
        Path root = Paths.get(ConfigUtil.projectRootDirectory()).getParent();
        configFiles.addAll(findFilesByExtension(Paths.get(root.toString(), "src"), ".cfg"));

        return configFiles.stream().map(Path::toString).collect(Collectors.toList());
    }

    /**
     * Validates all config files in the "config" directory or down the src tree parse properly.
     */
    @Test
    public void testAllConfFiles() throws EmissaryException, IOException {
        for (String resource : data()) {
            Path underTest = Paths.get(resource).toAbsolutePath().normalize();
            logger.info("Parsing config file:" + underTest);

            try {
                ConfigUtil.getConfigInfo(underTest.toString());
            } catch (IOException e) {
                fail("Caught error in file" + underTest + ": " + e.getMessage());
            }
        }
    }
}
