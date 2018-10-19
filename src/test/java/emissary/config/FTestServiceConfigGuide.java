package emissary.config;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import emissary.core.EmissaryException;
import emissary.test.core.FunctionalTest;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(Parameterized.class)
public class FTestServiceConfigGuide extends FunctionalTest {

    private static final Logger logger = LoggerFactory.getLogger(FTestServiceConfigGuide.class);

    public FTestServiceConfigGuide(String resource) throws IOException {
        super(resource);
        this.resource = resource;
    }

    @Parameterized.Parameters
    public static Collection<?> data() throws EmissaryException {
        ConfigUtil.initialize();

        // look in config dir
        Collection<File> configFiles = new ArrayList<>();
        for (String dir : ConfigUtil.getConfigDirs()) {
            configFiles.addAll(FileUtils.listFiles(new File(dir), new String[] {"cfg"}, true));
        }

        // look for cfg files under src
        configFiles.addAll(FileUtils.listFiles(new File(ConfigUtil.projectRootDirectory() + "/src"), new String[] {"cfg"}, true));

        Collection<String[]> fileNames = new ArrayList<String[]>();
        for (File f : configFiles) {
            fileNames.add(new String[] {f.toString()});
        }

        return fileNames;
    }

    protected String resource;

    /**
     * Validates all config files in the "config" directory or down the src tree parse properly.
     */
    @Test
    public void testAllConfFiles() {
        File underTest = new File(resource);
        logger.debug("Parsing config file:" + underTest.getAbsolutePath());

        try {
            ConfigUtil.getConfigInfo(underTest.getAbsolutePath());
        } catch (IOException e) {
            fail("Caught error in file" + underTest.getAbsolutePath() + ": " + e.getMessage());
        }
    }
}
