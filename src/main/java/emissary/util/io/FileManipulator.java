/*
  $Id$
 */

package emissary.util.io;

import java.io.File;
import java.io.Serializable;

/**
 * A class of utility methods for manipulating files.
 */


public class FileManipulator implements Serializable {

    static final long serialVersionUID = 365259266882118692L;

    /**
     * Create a unique filename and append it to the passed in path.
     */
    private static String FS = System.getProperty("file.separator", "/");

    public static String mkTempFile(final String dirPath, final String file) {
        // Date now = new Date(System.currentTimeMillis());
        String theFullPath = "";
        String tempName = file + (int) (Math.rint(Math.random() * 10000));
        if (dirPath.endsWith(FS) || dirPath.endsWith("/")) {
            theFullPath = dirPath + tempName;
        } else {
            theFullPath = dirPath + FS + tempName;
        }

        // Check to make sure file does not already exist
        File theTempFile = new File(theFullPath);
        if (theTempFile.exists()) {
            tempName = "temp" + (int) (Math.rint(Math.random() * 10000));

            if (dirPath.endsWith("/")) {
                theFullPath = dirPath + tempName;
            } else {
                theFullPath = dirPath + FS + tempName;
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
