package emissary.util.io;

import static java.nio.file.Files.exists;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.isReadable;
import static java.nio.file.Files.isWritable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalOutputUtil {

    private static final Logger logger = LoggerFactory.getLogger(LocalOutputUtil.class);

    private static final Set<PosixFilePermission> DEFAULT_PERMISSIONS = new HashSet<>(Arrays.asList(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_EXECUTE));

    /**
     * Create the local output directories
     *
     * @param outputPath the directory to create
     */
    public static void setup(Path outputPath) {
        setup(outputPath, false);
    }

    /**
     * Create the local output directories
     *
     * @param outputPath the directory to create
     * @param exitOnError if true the error is unrecoverable and causes a System exit
     */
    public static void setup(Path outputPath, boolean exitOnError) {
        setup(outputPath, DEFAULT_PERMISSIONS, exitOnError);
    }

    /**
     * Create the local output directories
     *
     * @param outputPath the directory to create
     * @param permissions the file permissions of the directory to create
     */
    public static void setup(Path outputPath, Set<PosixFilePermission> permissions) {
        setup(outputPath, permissions, false);
    }

    /**
     * Create the local output directories
     *
     * @param outputPath the directory to create
     * @param permissions the file permissions of the directory to create
     * @param exitOnError if true the error is unrecoverable and causes a System exit
     */
    public static void setup(Path outputPath, Set<PosixFilePermission> permissions, boolean exitOnError) {
        if (!Files.exists(outputPath)) {
            logger.info("Attempting to create output directory {}", outputPath);
            try {
                Files.createDirectories(outputPath, PosixFilePermissions.asFileAttribute(permissions));
            } catch (IOException e) {
                logger.error("Unable to create output directory {}, exiting immediately. ", outputPath, e);
                if (exitOnError) {
                    System.exit(1);
                }
            }
        }
    }

    /**
     * Validate the Path we are using for files
     */
    public static void validate(Path outputPath) throws IOException {
        if (!exists(outputPath)) {
            throw new FileNotFoundException("The output file path does not exist: " + outputPath + ".");
        } else if (!isDirectory(outputPath)) {
            throw new IllegalArgumentException("The output file path is not a directory: " + outputPath + ".");
        } else if (!(isReadable(outputPath) && isWritable(outputPath))) {
            throw new IllegalAccessError("The output path is not readable and writable: " + outputPath + ".");
        }
    }

    private LocalOutputUtil() {}
}
