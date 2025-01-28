package emissary.util.shell;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.directory.KeyManipulator;
import emissary.util.io.FileManipulator;

import com.google.common.primitives.Ints;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import javax.annotation.Nullable;

import static emissary.core.constants.Configurations.PLACE_NAME;
import static emissary.core.constants.Configurations.SERVICE_KEY;

/**
 * This class wraps up things related to exec-ing of external processes and reading and writing disk files.
 */
@SuppressWarnings("AvoidObjectArrays")
public class Executrix {
    private static final Logger logger = LoggerFactory.getLogger(Executrix.class);

    private static final int DEFAULT_VM_SIZE_LIMIT = 200000;
    private static final int DEFAULT_CPU_TIME_LIMIT = 300;
    private static final long DEFAULT_PROCESS_MAX_MILLIS = 300 * 1000L; // 5 min

    public enum OUTPUT_TYPE {
        STD, FILE
    }

    protected String command;
    protected String inFileEnding;
    protected String outFileEnding;
    protected String output;
    protected String order;
    protected String numArgs;
    protected String tmpDir;
    protected File tmpDirFile;
    protected int minimumDataSize;
    protected int maximumDataSize;
    protected String placeName;
    protected int vmSizeLimit;
    protected int cpuTimeLimit;
    protected long processMaxMillis;

    // Pieces and parts of file and path names
    public static final int DIR = 0;
    public static final int BASE = 1;
    public static final int BASE_PATH = 2;
    public static final int IN = 3;
    public static final int OUT = 4;
    public static final int INPATH = 5;
    public static final int OUTPATH = 6;

    /**
     * Create using all defaults
     */
    public Executrix() {
        configure(null);
    }

    /**
     * Create configuring from config source
     * 
     * @param configG the configuration items to use
     */
    public Executrix(final Configurator configG) {
        configure(configG);
    }

    /**
     * Configure all the extra command stuff along with the normal config Config Items read here are:
     * 
     * <ul>
     * <li>EXEC_COMMAND: the command to execute</li>
     * <li>IN_FILE_ENDING: extension of input file, default is none</li>
     * <li>OUT_FILE_ENDING: extension of output file, default is .out if input extension is blank, none otherwise</li>
     * <li>OUTPUT_TYPE: FILE or STD for where the output of EXEC_COMMAND goes, default STD</li>
     * <li>ORDER: default is NORMAL</li>
     * <li>NUM_ARGS: obsolete</li>
     * <li>TEMP_DIR: default is java.io.tmpdir</li>
     * <li>MINIMUM_DATA_SIZE: default is 0</li>
     * <li>MAXIMUM_DATA_SIZE: default is 64*1024</li>
     * <li>PLACE_NAME: also required by places in general</li>
     * <li>VM_SIZE_LIMIT: default is 200000 for ulimit argument</li>
     * <li>CPU_TIME_LIMIT: default is 300 seconds for ulimit argument</li>
     * <li>PROCESS_MAX_MILLIS: default is 300000 (5 Min) for process Watchdog. Set to 0 to disable watchdog use</li>
     * </ul>
     * 
     * @param configGArg the configuration stream
     */
    protected void configure(@Nullable final Configurator configGArg) {
        final Configurator configG = (configGArg != null) ? configGArg : new ServiceConfigGuide();

        this.command = configG.findStringEntry("EXEC_COMMAND", "echo 'YouForGotToSetEXEC_COMMAND' | tee bla.txt");
        this.inFileEnding = configG.findStringEntry("IN_FILE_ENDING", "");
        this.outFileEnding = configG.findStringEntry("OUT_FILE_ENDING", this.inFileEnding.isEmpty() ? ".out" : "");
        this.output = configG.findStringEntry("OUTPUT_TYPE", "STD");
        this.order = configG.findStringEntry("ORDER", "NORMAL");
        this.numArgs = configG.findStringEntry("NUM_ARGS", "");
        this.tmpDir = configG.findStringEntry("TEMP_DIR", System.getProperty("java.io.tmpdir", "/tmp"));
        this.tmpDirFile = new File(this.tmpDir);
        this.minimumDataSize = configG.findIntEntry("MINIMUM_DATA_SIZE", 0);
        this.maximumDataSize = configG.findIntEntry("MAXIMUM_DATA_SIZE", 64 * 1024);
        this.placeName = configG.findStringEntry(PLACE_NAME, null);
        if (this.placeName == null) {
            final String key = configG.findStringEntry(SERVICE_KEY, null);
            this.placeName = key == null ? "UNKNOWN" : KeyManipulator.getServiceName(key);
        }
        this.placeName = this.placeName.replace(' ', '_');
        this.vmSizeLimit = configG.findIntEntry("VM_SIZE_LIMIT", DEFAULT_VM_SIZE_LIMIT);
        this.cpuTimeLimit = configG.findIntEntry("CPU_TIME_LIMIT", DEFAULT_CPU_TIME_LIMIT);
        // Set to 0 to disable watchdog monitoring
        this.processMaxMillis = configG.findLongEntry("PROCESS_MAX_MILLIS", DEFAULT_PROCESS_MAX_MILLIS);
    }

    /**
     * Creates a set of temp file names (does not do any disk activity)
     *
     * @return new {@link TempFileNames} instance
     */
    public TempFileNames createTempFilenames() {
        return new TempFileNames(this.tmpDir, this.placeName, this.inFileEnding, this.outFileEnding);
    }

    /**
     * Make a set of temp file names (does not do any disk activity)
     *
     * @deprecated see {@link #createTempFilenames}
     */
    @Deprecated
    public String[] makeTempFilenames() {
        final TempFileNames tfn = createTempFilenames();

        final String[] names = new String[7];
        names[DIR] = tfn.getTempDir();
        names[BASE] = tfn.getBase();
        names[BASE_PATH] = tfn.getBasePath();
        names[IN] = tfn.getIn();
        names[OUT] = tfn.getOut();
        names[INPATH] = tfn.getInputFilename();
        names[OUTPATH] = tfn.getOutputFilename();

        return names;

    }

    /**
     * Read entire file from disk to a byte array
     * 
     * @param theFileName the name of the file to read
     * @throws IOException on error
     * @see #readFile(String,int)
     */
    public static byte[] readFile(final String theFileName) throws IOException {
        return readFile(theFileName, -1);
    }

    /**
     * Read portion of a file from disk to a byte array
     * 
     * @param theFileName the name of the file to read
     * @param length the max bytes to read or -1 for all
     * @throws IOException on error
     */
    public static byte[] readFile(final String theFileName, final int length) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(theFileName, "r")) {
            byte[] theContent = new byte[length == -1 || length >= raf.length() ? Ints.saturatedCast(raf.length()) : length];
            raf.readFully(theContent);
            return theContent;
        }
    }

    /**
     * Write byte array slice to a file, swallow exception
     * 
     * @param theContent bytes to write
     * @param pos starting position in theContent byte array
     * @param len number of bytes to write
     * @param filename the file to write to
     * @param append if true we append to the file
     * @return true if it worked
     */
    public static boolean writeDataToFile(@Nullable final byte[] theContent, final int pos, final int len, @Nullable final String filename,
            final boolean append) {
        if (filename == null) {
            return false;
        }

        final File dir = new File(filename).getParentFile();
        if (dir != null && !dir.exists()) {
            final boolean status = dir.mkdirs();
            if (!status) {
                logger.warn("Unable to create directory path to file {}", filename);
                return false;
            }
        }

        if (filename.isEmpty()) {
            logger.warn("Empty file name in writeFile:{}", filename);
            return false;
        }
        if (theContent == null) {
            logger.warn("Null content in writeFile:{}", filename);
            return false;
        }
        try {
            writeFile(theContent, pos, len, filename, append);
            return true;
        } catch (IOException e) {
            logger.error("writeDataToFile({}) exception", filename, e);
        }
        return false;
    }

    /**
     * Write byte array data to a file, swallow exception
     * 
     * @param theContent bytes to write
     * @param theFileName the file to write to
     * @return true if it worked
     */
    public static boolean writeDataToFile(@Nullable final byte[] theContent, final String theFileName) {
        if (theContent == null) {
            logger.warn("Null content in writeDataToFile({})", theFileName);
            return false;
        }
        return writeDataToFile(theContent, 0, theContent.length, theFileName, false);
    }

    /**
     * Write byte array data to a file, swallow exception
     * 
     * @param theContent bytes to write
     * @param theFileName the file to write to
     * @param append if true we append to the file
     * @return true if it worked
     */
    public static boolean writeDataToFile(@Nullable final byte[] theContent, final String theFileName, final boolean append) {
        if (theContent == null) {
            logger.warn("Null content in writeDataToFile({})", theFileName);
            return false;
        }
        return writeDataToFile(theContent, 0, theContent.length, theFileName, append);
    }

    /**
     * Write byte array slice to file
     * 
     * @param theContent source data
     * @param pos starting offset of slice
     * @param len length of slice
     * @param filename destination filename
     * @param append true if existing file should be appended to
     */
    public static void writeFile(final byte[] theContent, final int pos, final int len, final String filename, final boolean append)
            throws IOException {
        try (FileOutputStream theOutput = new FileOutputStream(filename, append);
                BufferedOutputStream theStream = new BufferedOutputStream(theOutput)) {
            theStream.write(theContent, pos, len);
        }
    }

    /**
     * Write byte array data to file
     * 
     * @param theContent source data
     * @param theFileName destination filename
     */
    public static void writeFile(final byte[] theContent, final String theFileName) throws IOException {
        writeFile(theContent, 0, theContent.length, theFileName, false);
    }

    /**
     * Write byte array data to file with append flag
     * 
     * @param theContent source data
     * @param theFileName destination filename
     * @param append true if existing file should be appended to
     */
    public static void writeFile(final byte[] theContent, final String theFileName, final boolean append) throws IOException {
        writeFile(theContent, 0, theContent.length, theFileName, append);
    }

    /**
     * Read data from file name passed in and return data read in a byte array. Just like readFile but does not throw an
     * exception
     * 
     * @param theFileName disk location to read from
     * @return byte array containing the data or null on io exception
     * @see #readFile(String)
     */
    public static byte[] readDataFromFile(final String theFileName) {
        return readDataFromFile(theFileName, false);
    }

    /**
     * Read data from file name passed in and return data read in a byte array. Just like readFile but does not throw an
     * exception
     * 
     * @param theFileName disk location to read from
     * @param quiet don't log any exceptions if true
     * @return byte array containing the data or null on io exception
     * @see #readFile(String)
     */
    @Nullable
    public static byte[] readDataFromFile(final String theFileName, final boolean quiet) {
        try {
            return readFile(theFileName, -1);
        } catch (IOException e) {
            if (!quiet) {
                logger.warn("readDataFromFile({}) Exception: ", theFileName, e);
            }
            return null;
        }
    }

    /**
     * Read data from random access file passed in and return data read in a byte array.
     * 
     * @param raf the random access file
     * @return byte array containing the data or null on io exception
     */
    public static byte[] readDataFromFile(final RandomAccessFile raf) {
        return readDataFromFile(raf, 0, -1);
    }

    /**
     * Read data from random access file passed in and return data read in a byte array.
     * 
     * @param raf the random access file
     * @param offset the offset in the channel
     * @param length the maximum byte count to read or -1 for all
     * @return byte array containing the data or null on io exception
     */
    @Nullable
    public static byte[] readDataFromFile(final RandomAccessFile raf, final int offset, final int length) {
        // Seek to offset specified
        try {
            raf.seek(offset);
        } catch (IOException ex) {
            logger.warn("Seek to {} on file failed", offset, ex);
            return null;
        }

        final long remain;
        try {
            remain = raf.length() - raf.getFilePointer();
        } catch (IOException iox) {
            logger.warn("Cannot get size of file", iox);
            return null;
        }

        // Size the result array
        final byte[] data;
        if ((remain > 0) && ((remain < length) || (length == -1))) {
            data = new byte[(int) remain];
        } else if (length > 0 && remain > 0) {
            data = new byte[length];
        } else {
            return null;
        }

        // Grab the data from the raf
        try {
            raf.readFully(data);
        } catch (EOFException ex) {
            logger.warn("RandomAccessFile underflow trying for {}", data.length, ex);
        } catch (IOException ex) {
            logger.warn("Unable to read from random access file", ex);
        }
        return data;
    }

    /**
     * Read all byte data from a channel and return in an array
     * 
     * @param channel the channel containing the data
     * @return bytes of data or null on exception
     */
    public static byte[] readDataFromChannel(final SeekableByteChannel channel) {
        return readDataFromChannel(channel, 0, -1);
    }

    /**
     * Read byte data from a channel and return in an array
     * 
     * @param channel the channel containing the data
     * @param offset the offset in the channel
     * @param length the maximum byte count to read or -1 for all
     * @return bytes of data or null on exception
     */
    @Nullable
    public static byte[] readDataFromChannel(@Nullable final SeekableByteChannel channel, final long offset, final int length) {
        if (channel == null) {
            return null;
        }

        final long size;
        try {
            size = channel.size();
        } catch (IOException iox) {
            logger.warn("Unable to get channel size", iox);
            return null;
        }

        if ((offset >= 0) && (offset < size)) {
            try {
                channel.position(offset);
            } catch (IOException iox) {
                logger.warn("Cannot position channel to offset {}", offset, iox);
                return null;
            }
        } else {
            logger.warn("Negative or out of bounds offset supplied");
            return null;
        }

        long remain = -1L;
        try {
            remain = size - channel.position();
        } catch (IOException iox) {
            logger.warn("Cannot get size of channel", iox);
        }

        // Size the result array
        final byte[] data;
        if ((remain < length) || (length == -1)) {
            data = new byte[(int) remain];
        } else {
            data = new byte[length];
        }

        // Grab the data from the channel
        try {
            final ByteBuffer buf = ByteBuffer.wrap(data);
            int totRead = 0;
            while (totRead < data.length) {
                int read = channel.read(buf);
                if (read == -1) {
                    break;
                }
                totRead += read;
            }
        } catch (BufferUnderflowException ex) {
            logger.warn("Buffer underflow trying for {}", data.length, ex);
        } catch (IOException iox) {
            logger.warn("Unable to read from channel", iox);
        }
        return data;
    }

    /**
     * Copy file given string names
     * 
     * @param infile the file to copy from
     * @param outfile the file to copy to
     */
    public static void copyFile(final String infile, final String outfile) throws IOException {
        final File fin = new File(infile);
        final File fout = new File(outfile);
        copyFile(fin, fout);
    }

    /**
     * Copy file given file objects
     * 
     * @param frm the file to copy from
     * @param to the file to copy to
     */
    public static void copyFile(final File frm, final File to) throws IOException {
        final byte[] buf = new byte[1024];
        try (InputStream fis = Files.newInputStream(frm.toPath());
                OutputStream fos = new BufferedOutputStream(Files.newOutputStream(to.toPath()))) {
            int len;
            while ((len = fis.read(buf)) != -1) {
                fos.write(buf, 0, len);
            }
        }
    }

    /**
     * Executes a command in a new process through Runtime Exec
     * 
     * @param cmd the string command to execute
     * @return process exit status
     */
    public int execute(final String cmd) {
        return execute(new String[] {cmd}, (StringBuilder) null, (StringBuilder) null, (String) null);
    }

    /**
     * Executes a command in a new process through Runtime Exec
     * 
     * @param cmd the string command to execute
     * @param out destination for the standard output as a string
     * @return process exit status
     */
    public int execute(final String cmd, final StringBuffer out) {
        return execute(new String[] {cmd}, out, (StringBuffer) null, (String) null);
    }

    /**
     * Executes a command in a new process through Runtime Exec
     * 
     * @param cmd the string command to execute
     * @param out destination for the standard output as a string
     * @return process exit status
     */
    public int execute(final String cmd, final StringBuilder out) {
        return execute(new String[] {cmd}, out, (StringBuilder) null, (String) null);
    }

    /**
     * Executes a command in a new process through Runtime Exec
     * 
     * @param cmd the string command to execute
     * @param out destination for the standard output as a string
     * @param err destination for the standard error
     * @return process exit status
     */
    public int execute(final String cmd, final StringBuffer out, final StringBuffer err) {
        return execute(new String[] {cmd}, out, err, (String) null);
    }

    /**
     * Executes a command in a new process through Runtime Exec
     * 
     * @param cmd the string command to execute
     * @param out destination for the standard output as a string
     * @param err destination for the standard error
     * @return process exit status
     */
    public int execute(final String cmd, final StringBuilder out, final StringBuilder err) {
        return execute(new String[] {cmd}, out, err, (String) null);
    }

    /**
     * Executes a command in a new process through Runtime Exec
     * 
     * @param cmd the string command to execute
     * @param out destination for the standard output as a string
     * @param err destination for the standard error
     * @param charset character set of the output stream
     * @return process exit status
     */
    public int execute(final String cmd, final StringBuffer out, final StringBuffer err, final String charset) {
        return execute(new String[] {cmd}, out, err, charset);
    }

    /**
     * Executes a command in a new process through Runtime Exec
     * 
     * @param cmd the string command to execute
     * @param out destination for the standard output as a string
     * @param err destination for the standard error
     * @param charset character set of the output stream
     * @return process exit status
     */
    public int execute(final String cmd, final StringBuilder out, final StringBuilder err, final String charset) {
        return execute(new String[] {cmd}, out, err, charset);
    }

    /**
     * Executes a command in a new process through Runtime Exec
     *
     * @param cmd the string command to execute
     * @param data the input data to the command
     * @return process exit status
     */
    public int execute(final String cmd, final byte[] data) {
        return execute(new String[] {cmd}, data, null);
    }

    /**
     * Executes a command in a new process through Runtime Exec
     *
     * @param cmd the string command to execute
     * @param data the input data to the command
     * @param out the destination to capture the standard output as a string
     * @return process exit status
     */
    public int execute(final String cmd, final byte[] data, final StringBuilder out) {
        return execute(new String[] {cmd}, data, out, null);
    }

    /**
     * Executes a command in a new process through Runtime Exec
     *
     * @param cmd the string command to execute
     * @param data the input data to the command
     * @param out the destination to capture the standard output as a string
     * @param err the destination to capture the standard error
     * @return process exit status
     */
    public int execute(final String cmd, final byte[] data, final StringBuilder out, final StringBuilder err) {
        return execute(new String[] {cmd}, data, out, err, null);
    }

    /**
     * Executes a command in a new process through Runtime Exec
     *
     * @param cmd the string command to execute
     * @param data the input data to the command
     * @param out the destination to capture the standard output as a string
     * @param err the destination to capture the standard error
     * @param charset character set of the output
     * @return process exit status
     */
    public int execute(final String cmd, final byte[] data, final StringBuilder out, final StringBuilder err, final String charset) {
        return execute(new String[] {cmd}, data, out, err, charset, null);
    }

    /**
     * Executes a command in a new process through Runtime Exec
     * 
     * @param cmd the command and arguments to execute
     * @return process exit status
     */
    public int execute(final String[] cmd) {
        return execute(cmd, (StringBuilder) null, (StringBuilder) null, (String) null);
    }

    /**
     * Executes a command in a new process through Runtime Exec
     * 
     * @param cmd the command and arguments to execute
     * @param out the destination to capture the standard output as a string
     * @return process exit status
     */
    public int execute(final String[] cmd, final StringBuffer out) {
        return execute(cmd, out, (StringBuffer) null, (String) null);
    }

    /**
     * Executes a command in a new process through Runtime Exec
     * 
     * @param cmd the command and arguments to execute
     * @param out the destination to capture the standard output as a string
     * @return process exit status
     */
    public int execute(final String[] cmd, final StringBuilder out) {
        return execute(cmd, out, (StringBuilder) null, (String) null);
    }

    /**
     * Executes a command in a new process through Runtime Exec
     * 
     * @param cmd the command and arguments to execute
     * @param out the destination to capture the standard output as a string
     * @param charset character set of the output
     * @return process exit status
     */
    public int execute(final String[] cmd, final StringBuffer out, final String charset) {
        return execute(cmd, out, (StringBuffer) null, charset);
    }

    /**
     * Executes a command in a new process through Runtime Exec
     * 
     * @param cmd the command and arguments to execute
     * @param out the destination to capture the standard output as a string
     * @param err the destination to capture the standard error
     * @return process exit status
     */
    public int execute(final String[] cmd, final StringBuffer out, final StringBuffer err) {
        return execute(cmd, out, err, (String) null);
    }

    /**
     * Executes a command in a new process through Runtime Exec
     * 
     * @param cmd the command and arguments to execute
     * @param out the destination to capture the standard output as a string
     * @param err the destination to capture the standard error
     * @return process exit status
     */
    public int execute(final String[] cmd, final StringBuilder out, final StringBuilder err) {
        return execute(cmd, out, err, (String) null);
    }

    /**
     * Executes a command in a new process through Runtime Exec
     * 
     * @param cmd the command and arguments to execute
     * @param out the destination to capture the standard output as a string
     * @param err the destination to capture the standard error
     * @param charset character set of the output
     * @return process exit status
     */
    public int execute(final String[] cmd, @Nullable final StringBuffer out, @Nullable final StringBuffer err, @Nullable final String charset) {
        final StringBuilder bout = (out != null) ? new StringBuilder() : null;
        final StringBuilder berr = (err != null) ? new StringBuilder() : null;

        final int status = execute(cmd, bout, berr, charset);

        if ((out != null) && (bout != null)) {
            out.append(bout.toString());
        }
        if ((err != null) && (berr != null)) {
            err.append(berr.toString());
        }
        return status;
    }

    /**
     * Executes a command in a new process through Runtime Exec
     * 
     * @param cmd the command and arguments to execute
     * @param out the destination to capture the standard output as a string
     * @param err the destination to capture the standard error
     * @return process exit status
     */
    public int execute(final String[] cmd, @Nullable final StringBuilder out, @Nullable final StringBuilder err, @Nullable final String charset) {
        return execute(cmd, out, err, charset, null);
    }

    /**
     * Executes a command in a new process through Runtime Exec
     * 
     * @param cmd the command and arguments to execute
     * @param out the destination to capture the standard output as a string
     * @param err the destination to capture the standard error
     * @param env environment variables for the new process supplied in name=value format.
     * @return process exit status
     */
    public int execute(final String[] cmd, final StringBuilder out, final StringBuilder err, final String charset,
            @Nullable final Map<String, String> env) {
        return execute(cmd, null, out, err, charset, env);
    }

    /**
     * Executes a command in a new process through Runtime Exec
     *
     * @param cmd the command and arguments to execute
     * @param data the input data to the command
     * @return process exit status
     */
    public int execute(final String[] cmd, final byte[] data) {
        return execute(cmd, data, null);
    }

    /**
     * Executes a command in a new process through Runtime Exec
     *
     * @param cmd the command and arguments to execute
     * @param data the input data to the command
     * @param out the destination to capture the standard output as a string
     * @return process exit status
     */
    public int execute(final String[] cmd, final byte[] data, @Nullable final StringBuilder out) {
        return execute(cmd, data, out, null);
    }

    /**
     * Executes a command in a new process through Runtime Exec
     *
     * @param cmd the command and arguments to execute
     * @param data the input data to the command
     * @param out the destination to capture the standard output as a string
     * @param err the destination to capture the standard error
     * @return process exit status
     */
    public int execute(final String[] cmd, final byte[] data, final StringBuilder out, @Nullable final StringBuilder err) {
        return execute(cmd, data, out, err, null);
    }

    /**
     * Executes a command in a new process through Runtime Exec
     *
     * @param cmd the command and arguments to execute
     * @param data the input data to the command
     * @param out the destination to capture the standard output as a string
     * @param err the destination to capture the standard error
     * @param charset character set of the output
     * @return process exit status
     */
    public int execute(final String[] cmd, final byte[] data, final StringBuilder out, final StringBuilder err, @Nullable final String charset) {
        return execute(cmd, data, out, err, charset, null);
    }

    /**
     * Executes a command in a new process through Runtime Exec
     *
     * @param cmd the command and arguments to execute
     * @param data the input data to the command
     * @param out the destination to capture the standard output as a string
     * @param err the destination to capture the standard error
     * @param charset character set of the output
     * @param env environment variables for the new process supplied in name=value format.
     * @return process exit status
     */
    public int execute(final String[] cmd, @Nullable final byte[] data, @Nullable final StringBuilder out, @Nullable final StringBuilder err,
            @Nullable final String charset, @Nullable final Map<String, String> env) {
        return execute(new ExecuteConfig.SbExecuteConfig(cmd, data, out, err, charset, env));
    }

    /**
     * Executes a command in a new process through Runtime Exec and captures binary output
     *
     * @param cmd the command and arguments to execute
     * @param data the input data to the command
     * @param out the destination to capture the standard output as bytes
     * @param err the destination to capture the standard error
     * @return process exit status
     */
    public int execute(final String[] cmd, @Nullable final byte[] data, @Nullable final ByteArrayOutputStream out,
            @Nullable final StringBuilder err) {
        return execute(cmd, data, out, err, null, null);
    }

    /**
     * Executes a command in a new process through Runtime Exec and captures binary output
     *
     * @param cmd the command and arguments to execute
     * @param data the input data to the command
     * @param out the destination to capture the standard output as bytes
     * @param err the destination to capture the standard error
     * @param charset character set of the output
     * @param env environment variables for the new process supplied in name=value format.
     * @return process exit status
     */
    public int execute(final String[] cmd, @Nullable final byte[] data, @Nullable final ByteArrayOutputStream out, @Nullable final StringBuilder err,
            @Nullable final String charset, @Nullable final Map<String, String> env) {
        return execute(new ExecuteConfig.BaosExecuteConfig(cmd, data, out, err, charset, env));
    }

    private int execute(ExecuteConfig eConfig) {
        int exitValue = -1;
        ExecuteWatchdog dog = null;
        try {
            final ProcessBuilder pb = eConfig.getProcessBuilder();
            final Process p = pb.start();
            final ProcessReader stdOutThread = eConfig.getStdOutProcessReader(p);
            final ProcessReader stdErrThread = eConfig.getStdErrProcessReader(p);

            // pass context to child threads so the info is available for any messages logged on those threads
            stdOutThread.setContextMap(MDC.getCopyOfContextMap());
            stdErrThread.setContextMap(MDC.getCopyOfContextMap());

            stdOutThread.start();
            stdErrThread.start();
            streamData(p, eConfig.getData());

            // kill process if it's not done after 5 minutes - would prefer to
            // pass in a timeout value
            if (this.processMaxMillis >= 1) {
                dog = new ExecuteWatchdog(this.processMaxMillis);
                dog.start(p);
            }
            p.waitFor();
            stdOutThread.join();
            stdErrThread.join();
            stdOutThread.finish();
            stdErrThread.finish();
            exitValue = p.exitValue();
        } catch (IOException e) {
            logger.warn("Failure during execution: {}, external command={}", e, Arrays.asList(eConfig.getCmd()));
        } catch (InterruptedException e) {
            logger.warn("Interrupted during execution: {}, external command={}", e, Arrays.asList(eConfig.getCmd()));
            Thread.currentThread().interrupt();
        } finally {
            if (dog != null) {
                dog.stop();
                dog = null;
            }
        }
        return exitValue;
    }


    private static void streamData(Process p, byte[] data) throws IOException {
        try (OutputStream os = new BufferedOutputStream(new DataOutputStream(p.getOutputStream()))) {
            if (ArrayUtils.isNotEmpty(data)) {
                os.write(data);
                os.flush();
            }
        }
    }

    /**
     * Write data out for processing into a new subdir under our configured temp area
     * 
     * @param data the bytes to write
     * @return the tempNames structure that was created
     * @deprecated see {@link #writeInputDataToNewTempDir(byte[])}
     */
    @Deprecated
    public String[] writeDataToNewTempDir(final byte[] data) {
        return writeDataToNewTempDir(data, 0, data.length);
    }

    /**
     * Write data out for processing into a new subdir under our configured temp area
     * 
     * @param data the bytes to write
     * @param start offset in array to start writing
     * @param len length of data to write
     * @return the tempNames structure that was created
     * @deprecated see {@link #writeInputDataToNewTempDir(byte[], int, int)}
     */
    @Deprecated
    public String[] writeDataToNewTempDir(final byte[] data, final int start, final int len) {
        final String[] tnames = makeTempFilenames();
        writeDataToFile(data, start, len, tnames[INPATH], false);
        return tnames;
    }

    /**
     * Write data out for processing into a new subdir under our configured temp area
     *
     * @param data the bytes to write
     * @param dirn the string name of a new tmp directory to use
     * @return the file that was created
     */
    @Nullable
    public File writeDataToNewTempDir(final byte[] data, final String dirn) {
        final File dir = new File(dirn);
        if (!dir.mkdirs()) {
            logger.warn("Unable to create directory path for file {}", dirn);
            return null;
        }

        // Make tmp file in new tmp dir
        final String inputFileName = FileManipulator.mkTempFile(dirn) + getInFileEnding();

        // Write it out
        writeDataToFile(data, 0, data.length, inputFileName, false);

        return new File(inputFileName);
    }

    /**
     * Write data out for processing into a new subdir under our configured temp area
     *
     * @param dirn the string name of a new tmp directory to use
     * @param data the bytes to write
     * @return the file that was created
     * @deprecated use {@link #writeDataToNewTempDir(byte[], String)}
     */
    @Nullable
    @Deprecated
    @SuppressWarnings("InconsistentOverloads")
    public File writeDataToNewTempDir(final String dirn, final byte[] data) {
        return writeDataToNewTempDir(data, dirn);
    }


    /**
     * Write data out for processing into a new subdir under our configured temp area
     *
     * @param data the bytes to write
     * @return the tempNames structure that was created
     */
    public TempFileNames writeInputDataToNewTempDir(final byte[] data) {
        return writeInputDataToNewTempDir(data, 0, data.length);
    }

    /**
     * Write data out for processing into a new subdir under our configured temp area
     *
     * @param data the bytes to write
     * @param start offset in array to start writing
     * @param len length of data to write
     * @return the tempNames structure that was created
     */
    public TempFileNames writeInputDataToNewTempDir(final byte[] data, final int start, final int len) {
        final TempFileNames tnames = createTempFilenames();
        writeDataToFile(data, start, len, tnames.getInputFilename(), false);
        return tnames;
    }

    /**
     * Gets the value of command that this instance will execute adding configured limits and configured paths to the
     * configuration value
     * 
     * @return the value of command
     */
    public String getCommand() {
        return this.command;
    }

    /**
     * Gets the value of command that this instance will execute adding configured limits and supplied paths to the
     * configuration value
     * 
     * @param tmpNames set of input/output directory names
     * @return the value of command
     */
    public String[] getCommand(final String[] tmpNames) {
        return getCommand(tmpNames, getCommand(), this.cpuTimeLimit, this.vmSizeLimit);
    }

    /**
     * Gets the value of a command that can be executed adding configured limits and supplied paths to the configuration
     * value
     *
     * @param tmpNames set of input/output directory names
     * @param commandArg a command string to work with
     * @return the value of command
     */
    public String[] getCommand(final String[] tmpNames, final String commandArg) {
        return getCommand(tmpNames, commandArg, this.cpuTimeLimit, this.vmSizeLimit);
    }

    /**
     * Gets the value of a command that can be executed adding supplied limits and supplied paths to the configuration value
     * The values in the command string that can be replaced are &lt;INPUT_PATH&gt;, &lt;OUTPUT_PATH&gt;,
     * &lt;INPUT_NAME&gt;, and &lt;OUTPUT_NAME&gt;. On unix systems it is wrapped like
     * <code>/bin/sh -c ulimit -c 0; ulimit -v val; your command</code>
     * 
     * @param tmpNames set of input/output directory names
     * @param commandArg a command string to work with
     * @param cpuLimit the cpu limit for the ulimit command
     * @param vmSzLimit for the ulimit command
     * @return the value of command
     */
    public String[] getCommand(final String[] tmpNames, final String commandArg, final int cpuLimit, final int vmSzLimit) {
        String c = commandArg;
        c = c.replaceAll("<INPUT_PATH>", tmpNames[INPATH]);
        c = c.replaceAll("<OUTPUT_PATH>", tmpNames[OUTPATH]);
        c = c.replaceAll("<INPUT_NAME>", tmpNames[IN]);
        c = c.replaceAll("<OUTPUT_NAME>", tmpNames[OUT]);

        // Run the command in shell limiting the core file size to 0 and the specified vm size
        String ulimitv = "";
        if (!SystemUtils.IS_OS_MAC) {
            ulimitv = "ulimit -v " + vmSzLimit + "; ";
        }
        return new String[] {"/bin/sh", "-c", "ulimit -c 0; " + ulimitv + "cd " + tmpNames[DIR] + "; " + c};
    }

    /**
     * Gets the value of command that this instance will execute adding configured limits and supplied paths to the
     * configuration value
     *
     * @param tmpNames set of input/output directory names
     * @return the value of command
     */
    public String[] getCommand(TempFileNames tmpNames) {
        return getCommand(tmpNames, getCommand(), this.cpuTimeLimit, this.vmSizeLimit);
    }

    /**
     * Gets the value of a command that can be executed adding configured limits and supplied paths to the configuration
     * value
     *
     * @param tmpNames set of input/output directory names
     * @param commandArg a command string to work with
     * @return the value of command
     */
    public String[] getCommand(final TempFileNames tmpNames, final String commandArg) {
        return getCommand(tmpNames, commandArg, this.cpuTimeLimit, this.vmSizeLimit);
    }

    /**
     * Gets the value of a command that can be executed adding supplied limits and supplied paths to the configuration value
     * The values in the command string that can be replaced are &lt;INPUT_PATH&gt;, &lt;OUTPUT_PATH&gt;,
     * &lt;INPUT_NAME&gt;, and &lt;OUTPUT_NAME&gt;. On unix systems it is wrapped like
     * <code>/bin/sh -c ulimit -c 0; ulimit -v val; your command</code>
     *
     * @param tmpNames set of input/output directory names
     * @param commandArg a command string to work with
     * @param cpuLimit the cpu limit for the ulimit command
     * @param vmSzLimit for the ulimit command
     * @return the value of command
     */
    public String[] getCommand(final TempFileNames tmpNames, final String commandArg, final int cpuLimit, final int vmSzLimit) {
        String c = commandArg;
        c = c.replaceAll("<INPUT_PATH>", tmpNames.getInputFilename());
        c = c.replaceAll("<OUTPUT_PATH>", tmpNames.getOutputFilename());
        c = c.replaceAll("<INPUT_NAME>", tmpNames.getIn());
        c = c.replaceAll("<OUTPUT_NAME>", tmpNames.getOut());

        // Run the command in shell limiting the core file size to 0 and the specified vm size
        String ulimitv = "";
        if (!SystemUtils.IS_OS_MAC) {
            ulimitv = "ulimit -v " + vmSzLimit + "; ";
        }
        return new String[] {"/bin/sh", "-c", "ulimit -c 0; " + ulimitv + "cd " + tmpNames.getTempDir() + "; " + c};
    }


    /**
     * Gets the value of a command that can be executed adding configured limits and supplied paths to the configuration
     * value
     *
     * @param commandArg a command string to work with
     * @param tmpNames set of input/output directory names
     * @return the value of command
     * @deprecated use {@link #getCommand(String[], String)}
     */
    @Deprecated
    @SuppressWarnings("InconsistentOverloads")
    public String[] getCommand(final String commandArg, final String[] tmpNames) {
        return getCommand(tmpNames, commandArg);
    }

    /**
     * Gets the value of a command that can be executed adding supplied limits and supplied paths to the configuration value
     * The values in the command string that can be replaced are &lt;INPUT_PATH&gt;, &lt;OUTPUT_PATH&gt;,
     * &lt;INPUT_NAME&gt;, and &lt;OUTPUT_NAME&gt;. On unix systems it is wrapped like
     * <code>/bin/sh -c ulimit -c 0; ulimit -v val; your command</code>
     *
     * @param commandArg a command string to work with
     * @param tmpNames set of input/output directory names
     * @param cpuLimit the cpu limit for the ulimit command
     * @param vmSzLimit for the ulimit command
     * @return the value of command
     * @deprecated use {@link #getCommand(String[], String, int, int)}
     */
    @Deprecated
    @SuppressWarnings("InconsistentOverloads")
    public String[] getCommand(final String commandArg, final String[] tmpNames, final int cpuLimit, final int vmSzLimit) {
        return getCommand(tmpNames, commandArg, cpuLimit, vmSzLimit);
    }

    /**
     * Sets the value of command that this instance will execute
     * 
     * @param argCommand Value to assign to this.command
     */
    public void setCommand(final String argCommand) {
        this.command = argCommand;
    }

    /**
     * Gets the value of inFileEnding
     * 
     * @return the value of inFileEnding
     */
    public String getInFileEnding() {
        return this.inFileEnding;
    }

    /**
     * Sets the value of inFileEnding
     * 
     * @param argInFileEnding Value to assign to this.inFileEnding
     */
    public void setInFileEnding(final String argInFileEnding) {
        this.inFileEnding = argInFileEnding;
    }

    /**
     * Gets the value of outFileEnding
     * 
     * @return the value of outFileEnding
     */
    public String getOutFileEnding() {
        return this.outFileEnding;
    }

    /**
     * Sets the value of outFileEnding
     * 
     * @param argOutFileEnding Value to assign to this.outFileEnding
     */
    public void setOutFileEnding(final String argOutFileEnding) {
        this.outFileEnding = argOutFileEnding;
    }

    /**
     * Gets the value of output type (STD or FILE)
     * 
     * @return the value of output
     */
    public String getOutput() {
        return this.output;
    }

    /**
     * Sets the value of output type (STD or FILE)
     * 
     * @param argOutput Value to assign to this.output
     */
    public void setOutput(final String argOutput) {
        if ("FILE".equals(argOutput) || "STD".equals(argOutput)) {
            this.output = argOutput;
        } else {
            throw new IllegalArgumentException("Output type must be FILE or STD");
        }
    }

    /**
     * Set the output type to STD
     */
    public void setOutputStd() {
        this.output = "STD";
    }

    /**
     * Set the output type to FILE
     */
    public void setOutputFile() {
        this.output = "FILE";
    }

    /**
     * Gets the value of order of arguments method
     * 
     * @return the value of order
     */
    public String getOrder() {
        return this.order;
    }

    /**
     * Sets the value of order, NORMAL or REVERSE
     * 
     * @param argOrder Value to assign to this.order
     */
    public void setOrder(final String argOrder) {
        this.order = argOrder;
    }

    /**
     * Gets the value of numArgs
     * 
     * @return the value of numArgs
     */
    public String getNumArgs() {
        return this.numArgs;
    }

    /**
     * Sets the value of numArgs
     * 
     * @param argNumArgs Value to assign to this.numArgs
     */
    public void setNumArgs(final String argNumArgs) {
        this.numArgs = argNumArgs;
    }

    /**
     * Gets the value of tmpDir
     * 
     * @return the value of tmpDir
     */
    public String getTmpDir() {
        return this.tmpDir;
    }

    /**
     * Sets the value of tmpDir
     * 
     * @param argTmpDir Value to assign to this.tempDir
     */
    public void setTmpDir(final String argTmpDir) {
        this.tmpDir = argTmpDir;
    }

    /**
     * Gets the value of tmpDirFile
     * 
     * @return the value of tmpDirFile
     */
    public File getTmpDirFile() {
        return this.tmpDirFile;
    }

    /**
     * Sets the value of tmpDirFile
     * 
     * @param argTmpDirFile Value to assign to this.tmpDirFile
     */
    public void setTmpDirFile(final File argTmpDirFile) {
        this.tmpDirFile = argTmpDirFile;
    }

    /**
     * Gets the value of minimumDataSize
     * 
     * @return the value of minimumDataSize
     */
    public int getMinimumDataSize() {
        return this.minimumDataSize;
    }

    /**
     * Sets the value of minimumDataSize
     * 
     * @param argMinimumDataSize Value to assign to this.minimumDataSize
     */
    public void setMinimumDataSize(final int argMinimumDataSize) {
        this.minimumDataSize = argMinimumDataSize;
    }

    /**
     * Gets the value of maximumDataSize
     * 
     * @return the value of maximumDataSize
     */
    public int getMaximumDataSize() {
        return this.maximumDataSize;
    }

    /**
     * Sets the value of maximumDataSize
     * 
     * @param argMaximumDataSize Value to assign to this.maximumDataSize
     */
    public void setMaximumDataSize(final int argMaximumDataSize) {
        this.maximumDataSize = argMaximumDataSize;
    }

    /**
     * Recursively remove up all files in a directory and then remove the directory itself.
     * 
     * @param dir the directory to remove
     * @return true if it works, false otherwise
     */
    public static boolean cleanupDirectory(final String dir) {
        return cleanupDirectory(new File(dir));
    }

    /**
     * Recursively remove up all files in a directory and then remove the directory itself. If the passed directory does not
     * exist then it will return true. If the passed directory is actually a file it will try and delete that. If an IO
     * problem happens listing the files then it will return false.
     * 
     * @param dir the directory to remove
     * @return true if it works, false otherwise
     */
    @SuppressWarnings("CatchingUnchecked")
    public static boolean cleanupDirectory(final File dir) {
        if (!dir.exists()) {
            return true;
        } else if (dir.isFile()) {
            boolean deleted = dir.delete();
            if (!deleted && dir.exists()) {
                deleted = dir.delete();
            }
            if (!deleted && dir.exists()) {
                logger.warn("Cannot delete {}", dir.getAbsolutePath());
                return false;
            }
            return true;
        } else {
            final File[] files = dir.listFiles();
            if (files == null) {
                // null is returned if it is not a dir or an IOException occurs. As the logic prevents a non dir path
                // getting here an IOException happened.
                return false;
            }
            for (final File f : files) {
                if (f.isDirectory()) {
                    cleanupDirectory(f);
                } else {
                    logger.debug("Deleting {}", f.getAbsolutePath());
                    boolean deleted = f.delete();
                    if (!deleted && f.exists()) {
                        deleted = f.delete();
                    }
                    if (!deleted && f.exists()) {
                        logger.warn("Cannot delete {}", f.getAbsolutePath());
                    }
                }
            }
            logger.debug("Deleting {}", dir.getAbsolutePath());

            try {

                // Try 1
                boolean deleted = dir.delete();

                // Try 2
                if (!deleted && dir.exists()) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ignore) {
                        Thread.currentThread().interrupt();
                    }
                    if (dir.exists()) {
                        deleted = dir.delete();
                    }
                }

                // Try 3
                if (!deleted && dir.exists()) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ignore) {
                        Thread.currentThread().interrupt();
                    }
                    if (dir.exists()) {
                        logger.debug("Temporary directory is still there. doing rm-rf {}", dir.getAbsolutePath());
                        new Executrix().execute(new String[] {"rm", "-rf", dir.getAbsolutePath()});
                    }
                }
            } catch (Exception ex) {
                logger.debug("Unable to remove directory {}", dir.getAbsolutePath(), ex);
            }

            return !dir.exists();
        }
    }

    public void setProcessMaxMillis(final long millis) {
        this.processMaxMillis = millis;
    }

    public long getProcessMaxMillis() {
        return this.processMaxMillis;
    }

    private abstract static class ExecuteConfig {

        final String[] cmd;
        final byte[] data;
        final StringBuilder err;
        final String charset;
        final Map<String, String> env;

        public ExecuteConfig(String[] cmd, byte[] data, StringBuilder err, String charset, Map<String, String> env) {
            this.cmd = cmd;
            this.data = data;
            this.err = err;
            this.charset = charset;
            this.env = env;
        }

        public String[] getCmd() {
            return cmd;
        }

        public byte[] getData() {
            return data;
        }

        public ProcessBuilder getProcessBuilder() {
            logger.debug("Executing command: {}", Arrays.asList(cmd));
            final ProcessBuilder pb = new ProcessBuilder(cmd);
            if (env != null) {
                pb.environment().putAll(env);
            }
            return pb;

        }

        @SuppressWarnings("MethodCanBeStatic")
        public ProcessReader getStdOutProcessReader(Process p) {
            return new ReadOutputLogger("stdOut", p.getInputStream());
        }

        public ProcessReader getStdErrProcessReader(Process p) {
            return err == null ? new ReadOutputLogger("stdErr", p.getErrorStream()) : new ReadOutputBuffer(p.getErrorStream(), err, charset);
        }

        private static class SbExecuteConfig extends ExecuteConfig {
            final StringBuilder out;

            public SbExecuteConfig(String[] cmd, byte[] data, StringBuilder out, StringBuilder err, String charset, Map<String, String> env) {
                super(cmd, data, err, charset, env);
                this.out = out;
            }

            @Override
            public ProcessReader getStdOutProcessReader(Process p) {
                return out == null ? super.getStdOutProcessReader(p) : new ReadOutputBuffer(p.getInputStream(), out);
            }

        }

        private static class BaosExecuteConfig extends ExecuteConfig {
            final ByteArrayOutputStream out;

            public BaosExecuteConfig(String[] cmd, byte[] data, ByteArrayOutputStream out, StringBuilder err, String charset,
                    Map<String, String> env) {
                super(cmd, data, err, charset, env);
                this.out = out;
            }

            @Override
            public ProcessReader getStdOutProcessReader(Process p) {
                return out == null ? super.getStdOutProcessReader(p) : new ReadBinaryOutputBuffer(p.getInputStream(), out);
            }
        }

    }


}
