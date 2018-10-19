package emissary.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import emissary.util.shell.Executrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnixFile {

    private static Logger log = LoggerFactory.getLogger(UnixFile.class);

    /** The magic number configuration file. The file which contains all magic number entries */
    private List<File> magicFiles = new ArrayList<File>();

    /** The Magic number helper class */
    private MagicNumberUtil util = new MagicNumberUtil();

    /** The Binary file type description */
    public static final String FILETYPE_BINARY = "Binary File";

    /** The ASCII file type description */
    public static final String FILETYPE_ASCII = "ASCII File";

    /** The empty file type description */
    public static final String FILETYPE_EMPTY = emissary.core.Form.EMPTY;

    /**
     * Constructor to load instance using the specified File. If the specified file is invalid, an exception will be thrown
     * when attempting utilize the <code>execute</code> method.
     *
     * @param magicFile the <code>File</code> containing magic number entries
     */
    public UnixFile(File magicFile) throws IOException {
        new UnixFile(magicFile, false);
    }

    /**
     * Constructor to load instance using the specified File. If the specified file is invalid, an exception will be thrown
     * when attempting utilize the <code>execute</code> method.
     *
     * @param magicFile the <code>File</code> containing magic number entries
     * @param swallowParseException should we swallow Ignorable ParseException or bubble them up
     */
    public UnixFile(File magicFile, boolean swallowParseException) throws IOException {
        if (!magicFile.exists()) {
            throw new RuntimeException("Magic file not found at: " + magicFile.getAbsolutePath());
        }

        this.magicFiles.add(magicFile);
        util.load(magicFile, swallowParseException);
    }

    /**
     * Load multiple magic files into one identification engine
     *
     * @param magicPaths the String names of magic files to load
     */
    public UnixFile(List<String> magicPaths) throws IOException {
        new UnixFile(magicPaths, false);
    }

    /**
     * Load multiple magic files into one identification engine
     * 
     * @param magicPaths the String names of magic files to load
     * @param swallowParseException should we swallow Ignorable ParseException or bubble them up
     */
    public UnixFile(List<String> magicPaths, boolean swallowParseException) throws IOException {
        for (String mPath : magicPaths) {
            File mFile = new File(mPath);
            if (!mFile.exists() || !mFile.canRead()) {
                throw new RuntimeException("Magic file not found at " + mFile.getAbsolutePath());
            }
            this.magicFiles.add(mFile);
            util.load(mFile, swallowParseException);
        }
    }


    public int magicEntryCount() {
        return util.size();
    }

    /**
     * Behaves just like the UNIX file command. First performs a magic number test, then an ascii or binary file test. This
     * is also the same as calling <code>evaluateByMagicNumber (bytes :
     * byte[])</code> and then calling <code>evaluateBinaryProperty
     * (bytes : byte[])</code>
     */
    public String execute(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 1) {
            return FILETYPE_EMPTY;
        }

        String subjectFileType = evaluateByMagicNumber(bytes);

        if (subjectFileType != null) {
            return subjectFileType;
        } else {
            return evaluateBinaryProperty(bytes);
        }
    }

    /**
     * Statically tests a byte array to determine if the file representation can be of type ASCII or is binary. Simply
     * checks each byte value to be less then greater/equal then 127.
     */
    public static String evaluateBinaryProperty(byte[] bytes) {
        if (bytes == null || bytes.length < 1) {
            return FILETYPE_EMPTY;
        }

        for (int i = 0; i < bytes.length; i++) {
            try {
                if (bytes[i] < 32) {
                    return FILETYPE_BINARY;
                }
            } catch (Exception ignore) {
                log.error("Exception on evaulateBinaryProperty", ignore);
            }
        }
        return FILETYPE_ASCII;
    }

    /**
     * Evaluates the byte array against the collection of Magic numbers
     */
    public String evaluateByMagicNumber(byte[] bytes) throws IOException {
        return util.describe(bytes);
    }

    /**
     * Test standalone main
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            log.info("Usage: UnixFile [-v] magicfile file1 [file2 ...]");
            return;
        }

        boolean verbose = false;

        int argIndex = 0;
        if (args[argIndex].equalsIgnoreCase("-v")) {
            verbose = true;
            argIndex++;
        }

        UnixFile unixfile = null;

        File magicNumberFile = new File(args[argIndex++]);
        if (!magicNumberFile.exists()) {
            System.err.println("Could not find magic file: " + magicNumberFile.getAbsolutePath());
            return;
        } else {
            unixfile = new UnixFile(magicNumberFile);
            if (verbose) {
                System.err.println("Using magic file at: " + magicNumberFile.getAbsolutePath());
            }
        }

        while (argIndex < args.length) {
            File target = new File(args[argIndex]);
            if (!target.exists()) {
                System.out.println(args[argIndex] + ": UNREADABLE");
            } else if (target.isDirectory()) {
                System.out.println(args[argIndex] + ": DIRECTORY");
            } else {
                byte[] data = Executrix.readDataFromFile(args[argIndex]);
                System.out.println(args[argIndex] + ": " + unixfile.evaluateByMagicNumber(data));
            }
            argIndex++;
        }

    }
}
