/*
  $Id$
 */

package emissary.util.io;

import java.io.File;
import java.io.Serializable;
import java.security.SecureRandom;

/**
 * A class of utility methods for manipulating files.
 */


public class FileManipulator implements Serializable {

    static final long serialVersionUID = 365259266882118692L;

    /**
     * Create a unique filename and append it to the passed in path.
     */
    private static final String FILE_SEPARATOR = System.getProperty("file.separator", "/");

    // Use ThreadLocal to allocate a SecureRandom instance per thread instead
    // of Math.random() which uses the same instance of Random for all threads
    // that causes resource contention and is not as secure.
    private static final ThreadLocal<SecureRandom> secureRandomThreadLocal = ThreadLocal.withInitial(SecureRandom::new);

    public static String mkTempFile(final String dirPath, final String file) {
        String theFullPath;
        String tempName = file + (secureRandomThreadLocal.get().nextLong() & Long.MAX_VALUE);
        if (dirPath.endsWith(FILE_SEPARATOR) || dirPath.endsWith("/")) {
            theFullPath = dirPath + tempName;
        } else {
            theFullPath = dirPath + FILE_SEPARATOR + tempName;
        }

        // Check to make sure file does not already exist
        File theTempFile = new File(theFullPath);
        if (theTempFile.exists()) {
            tempName = "temp" + (secureRandomThreadLocal.get().nextLong() & Long.MAX_VALUE);
            if (dirPath.endsWith("/")) {
                theFullPath = dirPath + tempName;
            } else {
                theFullPath = dirPath + FILE_SEPARATOR + tempName;
            }

        }
        return theFullPath;
    }

    public static String mkTempFile(final String dirPath) {
        return (mkTempFile(dirPath, "temp"));
    }

    /** This class is not meant to be instantiated. */
    private FileManipulator() {}
}
