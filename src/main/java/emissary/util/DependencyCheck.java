package emissary.util;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.util.shell.Executrix;

/**
 * Class that performs simple checks to determine if an executable, directory, and/or file exists on the host machine.
 */
public class DependencyCheck {

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
        return (exists);
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
        return (config.findEntriesAsSet(key));
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
            System.out.println("RequiredExecutable: " + reqExe + " exists: " + exists);
        }
        for (String reqDir : reqDirSet) {
            boolean exists = DependencyCheck.directoryExists(reqDir);
            System.out.println("RequiredDirectory " + reqDir + " exists: " + exists);
        }
        for (String reqFile : reqFileSet) {
            boolean exists = DependencyCheck.fileExists(reqFile);
            System.out.println("RequiredFile: " + reqFile + " exists: " + exists);
        }
        for (String optExe : optExeSet) {
            boolean exists = DependencyCheck.executableExists(optExe);
            System.out.println("OptionalExecutable: " + optExe + " exists: " + exists);
        }
        for (String optDir : optDirSet) {
            boolean exists = DependencyCheck.directoryExists(optDir);
            System.out.println("OptionalDirectory " + optDir + " exists: " + exists);
        }
        for (String optFile : optFileSet) {
            boolean exists = DependencyCheck.fileExists(optFile);
            System.out.println("OptionalFile: " + optFile + " exists: " + exists);
        }
    }

    public static void main(String[] args) throws IOException {
        DependencyCheck dependencyCheck = new DependencyCheck();
        dependencyCheck.printDependencyReport();
    }

}
