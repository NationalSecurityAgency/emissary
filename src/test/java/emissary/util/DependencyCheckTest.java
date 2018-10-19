package emissary.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.test.core.UnitTest;
import org.junit.Test;

public class DependencyCheckTest extends UnitTest {

    private final String BIN_DIR = System.getProperty(ConfigUtil.CONFIG_BIN_PROPERTY);

    @Test
    public void testExecutableExist() {
        boolean exists = DependencyCheck.executableExists("bash");
        assertTrue("Executable should exist", exists);
    }

    @Test
    public void testExecutableDoesNotExist() {
        boolean exists = DependencyCheck.executableExists("foobarbaz");
        assertFalse("Executable should not exist", exists);
    }

    @Test
    public void testDirectoryDoesNotExists() {
        boolean exists = DependencyCheck.directoryExists("./missing");
        assertFalse("Directory should not exist", exists);
    }

    @Test
    public void testFileDoesNotExists() {
        boolean exists = DependencyCheck.fileExists(BIN_DIR + "/missing.txt");
        assertFalse("File should not exist", exists);
    }

    @Test
    public void testDependencyReport() throws Exception {
        Configurator conf = new ServiceConfigGuide();
        conf.addEntry("REQUIRED_EXECUTABLE", "bash");
        conf.addEntry("REQUIRED_DIRECTORY", BIN_DIR);
        conf.addEntry("REQUIRED_FILE", "./scrips/run.sh");
        conf.addEntry("OPTIONAL_EXECUTABLE", "peterpan");
        conf.addEntry("OPTIONAL_DIRECTORY", "./captain-hook");
        conf.addEntry("OPTIONAL_FILE", "./scrips/wendy-can-fly.sh");
        DependencyCheck d = new DependencyCheck(conf);
        PrintStream originalOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream myOut = new PrintStream(baos);
        System.setOut(myOut);
        d.printDependencyReport();
        System.setOut(originalOut);
        myOut.close();
        assertTrue("Report must exist", baos.size() > 0);
    }
}
