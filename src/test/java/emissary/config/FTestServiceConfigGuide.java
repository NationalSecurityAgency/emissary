package emissary.config;

import static emissary.util.io.UnitTestFileUtils.findFilesByExtension;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;

import emissary.core.EmissaryException;
import emissary.test.core.FunctionalTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(Parameterized.class)
public class FTestServiceConfigGuide extends FunctionalTest {

    private static final Logger logger = LoggerFactory.getLogger(FTestServiceConfigGuide.class);

    protected String resource;

    public FTestServiceConfigGuide(String resource) throws IOException {
        super(resource);
        this.resource = resource;
    }

    @Parameterized.Parameters
    public static Collection<?> data() throws IOException, EmissaryException {
        ConfigUtil.initialize();

        // look in config dir
        Collection<Path> configFiles = new ArrayList<>();

        for (String dir : ConfigUtil.getConfigDirs()) {
            configFiles.addAll(findFilesByExtension(Paths.get(dir), ".cfg"));
        }

        // look for cfg files under src
        Path root = Paths.get(ConfigUtil.projectRootDirectory()).getParent();
        configFiles.addAll(findFilesByExtension(Paths.get(root.toString(), "src"), ".cfg"));

        Collection<String[]> fileNames = new ArrayList<>();
        for (Path f : configFiles) {
            fileNames.add(new String[] {f.toString()});
        }

        return fileNames;
    }

    /**
     * Validates all config files in the "config" directory or down the src tree parse properly.
     */
    @Test
    public void testAllConfFiles() {
        Path underTest = Paths.get(resource).toAbsolutePath().normalize();
        logger.debug("Parsing config file:" + underTest);

        try {
            ConfigUtil.getConfigInfo(underTest.toString());
        } catch (IOException e) {
            fail("Caught error in file" + underTest + ": " + e.getMessage());
        }
    }
}
