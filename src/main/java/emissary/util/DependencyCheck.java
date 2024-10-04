package emissary.util;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.util.shell.Executrix;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * Class that performs simple checks to determine if an executable, directory, and/or file exists on the host machine.
 */
public class DependencyCheck {

    private static final Logger logger = LoggerFactory.getLogger(DependencyCheck.class);

    Configurator config;

    public static final String REQUIRED_EXECUTABLE = "REQUIRED_EXECUTABLE";
    public static final String REQUIRED_DIRECTORY = "REQUIRED_DIRECTORY";
    public static final String REQUIRED_FILE = "REQUIRED_FILE";
    public static final String OPTIONAL_EXECUTABLE = "OPTIONAL_EXECUTABLE";
    public static final String OPTIONAL_DIRECTORY = "OPTIONAL_DIRECTORY";
    public static final String OPTIONAL_FILE = "OPTIONAL_FILE";

    public DependencyCheck() throws IOException {
        config = ConfigUtil.getConfigInfo(DependencyCheck.class);
    }

    public DependencyCheck(Configurator config) {
        this.config = config;
    }

    /**
     * Static method to determine if an executable is found.
     * 
     * @param executable name (can be fully qualified path).
     * @return return true if any of the executable is found, false otherwise.
     */
    public static boolean executableExists(String executable) {
        boolean exists = false;
        String[] command = new String[] {"which", executable};
        Executrix exec = new Executrix();
        int returnVal = exec.execute(command);
        if (returnVal == 0) {
            exists = true;
        }
        return exists;
    }

    /**
     * Static method to determine if a fully qualified directory exists
     *
     * @param pathToDir path to the directory to check
     * @return true if exist, false otherwise.
     */
    public static boolean directoryExists(String pathToDir) {
        boolean dirExists = false;
        {
            try {
                dirExists = new File(pathToDir).getCanonicalFile().isDirectory();
            } catch (IOException e) {
                dirExists = false;
            }
        }
        return dirExists;
    }

    /**
     * Static method to determine if a fully qualified file exists
     *
     * @param pathToFile path to the file to check
     * @return true if exists, false otherwise.
     */
    public static boolean fileExists(String pathToFile) {
        boolean dirExists = false;
        {
            try {
                dirExists = new File(pathToFile).getCanonicalFile().isFile();
            } catch (IOException e) {
                dirExists = false;
            }
        }
        return dirExists;
    }

    public Set<String> getDependencies(String key) {
        return config.findEntriesAsSet(key);
    }

    public void printDependencyReport() {
        Set<String> reqExeSet = getDependencies(REQUIRED_EXECUTABLE);
        Set<String> reqDirSet = getDependencies(REQUIRED_DIRECTORY);
        Set<String> reqFileSet = getDependencies(REQUIRED_FILE);
        Set<String> optExeSet = getDependencies(OPTIONAL_EXECUTABLE);
        Set<String> optDirSet = getDependencies(OPTIONAL_DIRECTORY);
        Set<String> optFileSet = getDependencies(OPTIONAL_FILE);

        for (String reqExe : reqExeSet) {
            boolean exists = DependencyCheck.executableExists(reqExe);
            logger.info("RequiredExecutable: {} exists: {}", reqExe, exists);
        }
        for (String reqDir : reqDirSet) {
            boolean exists = DependencyCheck.directoryExists(reqDir);
            logger.info("RequiredDirectory {} exists: {}", reqDir, exists);
        }
        for (String reqFile : reqFileSet) {
            boolean exists = DependencyCheck.fileExists(reqFile);
            logger.info("RequiredFile: {} exists: {}", reqFile, exists);
        }
        for (String optExe : optExeSet) {
            boolean exists = DependencyCheck.executableExists(optExe);
            logger.info("OptionalExecutable: {} exists: {}", optExe, exists);
        }
        for (String optDir : optDirSet) {
            boolean exists = DependencyCheck.directoryExists(optDir);
            logger.info("OptionalDirectory {} exists: {}", optDir, exists);
        }
        for (String optFile : optFileSet) {
            boolean exists = DependencyCheck.fileExists(optFile);
            logger.info("OptionalFile: {} exists: {}", optFile, exists);
        }
    }

    public static void main(String[] args) throws IOException {
        DependencyCheck dependencyCheck = new DependencyCheck();
        dependencyCheck.printDependencyReport();
    }

}
