package emissary.util.io;

import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.Nullable;

public class FileIoUtils {

    /** This class is not meant to be instantiated. */
    private FileIoUtils() {}

    /**
     * Normalizes the provided paths and tests to see whether the file path is within the required base directory.
     *
     * @param requiredBase required base directory
     * @param filePath file path to be tested
     * @return the normalized file path
     * @throws IllegalArgumentException if the filePath contains illegal characters or is outside the required base
     *         directory
     */
    public static String filePathIsWithinBaseDirectory(final String requiredBase, final String filePath) throws IllegalArgumentException {

        if (StringUtils.isBlank(requiredBase)) {
            throw new IllegalArgumentException("requiredBase must not be blank");
        }

        if (StringUtils.isBlank(filePath)) {
            throw new IllegalArgumentException("filePath must not be blank");
        }

        // probably an overly simplistic test
        if (filePath.contains("..")) {
            throw new IllegalArgumentException("filePath contains illegal character sequence \"..\"");
        }
        Path normalizedBasePath = Paths.get(requiredBase).normalize().toAbsolutePath();
        Path normalizedFilePath = Paths.get(filePath).normalize().toAbsolutePath();

        // append path separator to defeat traversal via a sibling directory with a similar name
        if (!normalizedFilePath.startsWith(normalizedBasePath.toString() + "/")) {
            throw new IllegalArgumentException("Normalized file path (\"" + filePath + "\") is outside the required base path (\""
                    + requiredBase + "\")");
        }
        return normalizedFilePath.toString();
    }

    public static String cleanSpecPath(@Nullable String token) {
        return token == null ? null : token.replaceAll("[.]+", ".");
    }


}
