package emissary.util.io;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.substringBeforeLast;

/**
 * A {@link ResourceReader} extended to find data files based purely on their location, ignoring the default naming
 * convention used by the {@link ResourceReader#findDataResourcesFor(Class)} method.
 * <p>
 * This class is primarily used to find payload files for Identification tests that can benefit from more
 * content-representative file names.
 * </p>
 */
public class GreedyResourceReader extends ResourceReader {
    private static final Logger logger = LoggerFactory.getLogger(GreedyResourceReader.class);

    public static final String PAYLOADS_FOLDER = "payloads";
    public static final String ANSWERS_FOLDER = "answers";

    public static final Predicate<String> IS_XML_FILE = filename -> "xml".equals(FilenameUtils.getExtension(filename));

    /**
     * Returns the project-relative paths of test files for the specified test class. The files should be underneath a
     * "payloads" subdirectory of the test class directory. Additional subdirectories can exist within the payloads
     * directory itself, and any files found within will be included in the results.
     * 
     * @param c test class for which to perform the search
     * @return list of project-relative test file paths
     */
    public List<String> findAllPayloadFilesFor(Class<?> c) {
        URL url = this.which(c);
        if (url == null || !url.getProtocol().equals("file")) {
            return Collections.emptyList();
        }

        List<String> results = new ArrayList<>(findAllFilesUnderClassNameSubDir(c, url, PAYLOADS_FOLDER));
        Collections.sort(results);
        return results;
    }

    /**
     * Returns the project-relative paths of test files for the specified test class. The files should be underneath a
     * "payloads" subdirectory of the test class directory. Additional subdirectories can exist within the payloads
     * directory itself, and any files found within will be included in the results.
     *
     * @param c test class for which to perform the search
     * @return list of project-relative test file paths
     */
    public List<String> findAllAnswerFilesFor(Class<?> c) {
        URL url = this.which(c);
        if (url == null || !url.getProtocol().equals("file")) {
            return Collections.emptyList();
        }

        List<String> results = findAllFilesUnderClassNameSubDir(c, url, ANSWERS_FOLDER, IS_XML_FILE);
        Collections.sort(results);
        return results;
    }

    /**
     * Finds all files beneath the specified subdirectory of the test class resource folder
     *
     * @param c test class for which the resource files exist
     * @param url location from which the classLoader looded the test class
     * @param subDirName subdirectory that contains the files
     * @return List of test resource file paths
     */
    private List<String> findAllFilesUnderClassNameSubDir(Class<?> c, URL url, final String subDirName) {
        return findAllFilesUnderClassNameSubDir(c, url, subDirName, StringUtils::isNotBlank);
    }

    /**
     * Finds the files beneath a given test class resource folder, filtered by a provided {@link Predicate<String>}
     *
     * @param c test class for which the resource files exist
     * @param url location from which the classLoader loaded the test class
     * @param subDirName subdirectory that contains the files
     * @param fileFilter Predicate used to filter the list of discovered files
     * @return List of test resource file paths
     */
    private List<String> findAllFilesUnderClassNameSubDir(Class<?> c, URL url, final String subDirName, final Predicate<String> fileFilter) {
        String classNameInPathFormat = getResourceName(c);
        Path subDir = Path.of(getFullPathOfTestClassResourceFolder(url, c), subDirName);
        File testClassDir = subDir.toFile();
        if (testClassDir.exists() && testClassDir.isDirectory()) {
            try (Stream<Path> theList = Files.walk(testClassDir.toPath())) {
                return theList.filter(Files::isRegularFile)
                        .map(testClassDir.toPath()::relativize)
                        .map(filePath -> classNameInPathFormat + "/" + subDirName + "/" + filePath)
                        .filter(fileFilter::test)
                        .collect(Collectors.toList());

            } catch (IOException e) {
                logger.debug("Failed to retrieve files for class {}", c.getName(), e);
            }
        }

        return Collections.emptyList();
    }

    /**
     * Gets the absolute path of a test class runtime resource folder
     * 
     * @param url URL from which the ClassLoader loaded the test class
     * @param c test class
     * @return test class folder path
     */
    protected String getFullPathOfTestClassResourceFolder(URL url, Class<?> c) {
        String classNameInPathFormat = getResourceName(c);
        if (url.getPath().contains(CLASS_SUFFIX)) {
            // return the URL minus the ".class" suffix
            return StringUtils.substringBeforeLast(url.getPath(), CLASS_SUFFIX);
        }

        return StringUtils.join(url.getPath(), "/", classNameInPathFormat);
    }
}
