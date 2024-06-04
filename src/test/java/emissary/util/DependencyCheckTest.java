package emissary.util;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyCheckTest extends UnitTest {

    private static final String BIN_DIR = System.getProperty(ConfigUtil.CONFIG_BIN_PROPERTY);

    @Test
    void testExecutableExist() {
        boolean exists = DependencyCheck.executableExists("bash");
        assertTrue(exists, "Executable should exist");
    }

    @Test
    void testExecutableDoesNotExist() {
        boolean exists = DependencyCheck.executableExists("foobarbaz");
        assertFalse(exists, "Executable should not exist");
    }

    @Test
    void testDirectoryDoesNotExists() {
        boolean exists = DependencyCheck.directoryExists("./missing");
        assertFalse(exists, "Directory should not exist");
    }

    @Test
    void testFileDoesNotExists() {
        boolean exists = DependencyCheck.fileExists(BIN_DIR + "/missing.txt");
        assertFalse(exists, "File should not exist");
    }

    @Test
    void testDependencyReport() {
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
        assertTrue(baos.size() > 0, "Report must exist");
    }
}
