package emissary.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import emissary.util.magic.MagicNumber;
import emissary.util.magic.MagicNumberFactory;
import emissary.util.shell.Executrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Magic entry rules when using the Java utility, MagicNumberUtil
 *
 * A. Examples:
 *
 * Java ByteCode From Larry Schwimmer (schwim@cs.stanford.edu) 0 belong 0xcafebabe compiled Java class data, &gt;6
 * beshort x version %d. &gt;4 beshort x \b%d
 *
 * The entries must have 4 columns where the first three are delimited by white space as tabs, or spaces, or both and
 * the remaining columns will be stored as the description. Since spaces are also delimiters - if the value column
 * (third column) requires a space then it should be escaped.
 *
 * B. Offset Column 1. A decimal, hex, or octal value preceded or not preceded by '&gt;' 2. Decimal: n* - if the
 * occurrences is &gt; 1, then not preceded by '0' 3. Hex: 0xn* 4. Octal: 0n* 5. Offset values in the format '(n.s+32)'
 * are ignored. These only occurred in the continuations
 *
 * C. Data Type Column 1. BYTE, SHORT, LONG, STRING, BESHORT, BELONG, LESHORT, LELONG 2. LEDATE, BEDATE, and DATE are
 * not supported 3. Masking: If the data type is followed by a mask value in decimal, octal or hex and delimited by
 * '&amp;' then the value column will be stored as the product of the masking. The mask value cannot exceed the data
 * type length.
 *
 * D. Value columns 1. String, Byte, Short, or Long - 1, 2, or 4 byte values - or any length value for the string type.
 * 2. String values can be escaped. a. Example "\0\40\320\3200\4text\ \7\x40\r\t\" parsed as: "0-32-208-208-0-4-text-
 * -7-64- " with the dashes removed.
 *
 * 1. Escaping number values Numbers can be of length up to three octal or two hex and can also be terminated by
 * non-digits and finally must be less then 256. These numeric values are substituted into their respective byte
 * positions.
 *
 * - 3200 will be evaluated as '320' octal and '0' string. - 0 as 0 octal - 40 as 40 octal - x40 as 40 hex which is 64 -
 * 4text as 4 and succeeded by the characters 't' 'e' 'x' 't'
 *
 * 2. Escaping characters including spaces - Spaces must be escaped - otherwise they'll be tokenized into the next
 * column - 4\ 4\4 results in [char 4, char space, char 4, integer 4] - Ascii values 8-15 can be escaped as: \b\t\n\r
 * etc... see man page for ascii - trailing slashes will result in the placement of a trailing space
 *
 * 3. Substitution Example &gt;4 beshort x \bversion %d.%c Substitution is allowed for continuations only. In this case,
 * the short byte array will be sampled from the document at offset 4 and length 2. This stored value can be substituted
 * in the description field where %c or %s will substitute convert the number into a unicode character and %d %ld and
 * other numeric data types will instead substitute the numeric value.
 *
 * In the above, if value 'x' at offset 4 equals 0101 octal, then the substitutions will be a decimal value of 64 and
 * the character value of 'A' resulting in: "\bversion 64.A
 *
 * E. Description The description is comprised of all remaining columns once the first three have been discovered. They
 * can be blank in continuations since continuations may depend upon the successful testing of preceding continuations.
 * In other words:
 *
 * 0 long 0xcafebabe java binary &gt;4 byte x version %d
 *
 * can be re-written as
 *
 * 0 short 0xcafe java &gt;4 byte 0xba &gt;&gt;6 byte 0xbe \b\bbinary &gt;&gt;&gt;4 byte x version %d
 *
 * where the continuations will only occur upon the completion of '&gt;4 byte 0xba'
 *
 * 1. The descriptions can be escaped with a '\b' 2. Each continuation is prefixed with a space when added to a
 * description. To avoid this or remove spaces use the '\b' backspace and it will perform a backspace function or trim
 * previous character. 3. See Value column for substitution rules
 */

public final class MagicNumberUtil {

    private static final Logger log = LoggerFactory.getLogger(MagicNumberUtil.class);

    /** The magic number instances */
    private final List<MagicNumber> magicNumbers = new ArrayList<MagicNumber>();

    /**
     * Log flag for storing parse errors - they will just be discarded. Switching this on will allow erroneous entries to be
     * logged and can be retrieved using the method getErrorLog to find out which entries had parsing errors. Using the
     * magic file shipped with version unix file 3.39 only three/four primary entries were unsupported - these had to do
     * with signed data types such as ubelong. Or the value was larger then the specified data type which occurred once.
     * Otherwise, remaining errors were in continuations - mainly when the offset value was in the form of n.s+32 where 'n'
     * is a decimal value and 's' could not be determined.
     */
    private boolean logErrors = false;

    /**
     * Log data structure for continuations. Maps entries with depth 0 with a List of continuation entries containing the
     * errors
     */
    private final Map<String, List<String>> extErrorMap = new TreeMap<String, List<String>>();

    /**
     * Log data structure for entries with a depth of '0' - these are the important entries. Just maintains a simple list of
     * these entries
     */
    private final List<String> errorList = new ArrayList<String>();

    /**
     * Private Constructor
     */
    public MagicNumberUtil() {}

    /**
     * Main testing - plug in the magic file as the first arg and the target file to be examined as the second.
     *
     * Usage: java xxx.MagicNumberUtil [magic config file absolute path] [target file to be id'd]
     */
    public static void main(final String[] args) {
        if (args.length < 2) {
            log.info("Usage: java xxx.MagicNumberUtil [magic config file absolute path] [target file to be id'd]");
        }

        final MagicNumberUtil util = new MagicNumberUtil();
        try {
            // make sure the magic file exists
            final File magicFile = new File(args[0]);
            if (!magicFile.exists()) {
                log.info("Could not find the magic config file at: " + magicFile.getAbsolutePath());
                System.exit(0);
            }
            // make sure the target file can be found as well
            final File target = new File(args[1]);
            if (!target.exists()) {
                log.info("Could not find the target file at: " + target.getAbsolutePath());
            }

            // load the magic numbers
            log.info("LOAD MAGIC NUMBER LIST AT: " + args[0]);
            util.load(new File(args[0]));
            log.info("FINISHED LOADING MAGIC NUMBER LIST");

            // perform the id process
            log.info("PERFORMING ID PROCESS");
            log.info(util.describe(new File(args[1])));
        } catch (Exception e) {
            log.error("Error in main", e);
        }
        // if error logging is enabled on the 'logErrors' flag then this will print out the entry and continuation
        // parsing errors
        if (util.logErrors) {
            log.error(util.getErrorLog());
        }
    }

    public void setErrorLogging(final boolean logErr) {
        this.logErrors = logErr;
    }

    /**
     * Input a byte array sample and it will be compared against the global magic number list. Descriptions for matching
     * entries inclusive of continuations.
     *
     * @param data a byte[]
     * @return {@link String} representing matching description plus matching continuation descriptions or null.
     * @throws RuntimeException If the magic file has not been loaded globally using the load methods.
     * @see #load(java.io.File)
     * @see #load(byte[])
     */
    public String describe(final byte[] data) {
        log.debug("Checking against " + this.magicNumbers.size() + " magic items");
        String description = null;
        for (final MagicNumber item : this.magicNumbers) {
            log.debug("Checking magic item " + item);
            description = item.describe(data);
            if (description != null && !description.isEmpty()) {
                break;
            }
        }
        return description;
    }

    /**
     * Input a byte array sample and it will be compared against the global magic number list. Descriptions for matching
     * entries inclusive of continuations provided.
     *
     * @param target data a java.io.File
     * @return A string representing matching description plus matching continuation descriptions or null.
     * @throws RuntimeException If the magic file has not been loaded globally using the load methods.
     * @throws IOException If a read error occurs loading the target file.
     * @see #load(java.io.File)
     * @see #load(byte[])
     */
    public String describe(final File target) throws IOException {
        try {
            if (!target.exists()) {
                throw new IOException("Target file not found at: " + target.getAbsolutePath());
            }
        } catch (SecurityException se) {
            throw new IOException("Security Exception reading file: " + se.getMessage());
        }
        return describe(Executrix.readDataFromFile(target.getAbsolutePath()));
    }

    /**
     * Do not load magic file globally and do not compare against the global magic number list and instead compare target
     * against the specified magic file. The magic file will be read/parsed each time as the comparative file. Useful for
     * debugging or if certain files can be narrowed down to a smaller magic file list improving id performance.
     *
     * @param target a java.io.File specifying the file to be id'd
     * @param magicConfig the magic file containing the magic number entries to use
     * @return {@link String} representing the id description or null
     * @throws IOException If an IO error occurs while reading either file.
     */
    public static String describe(final File target, final File magicConfig) throws IOException {
        try {
            if (!target.exists()) {
                throw new IOException("Target file not found at: " + target.getAbsolutePath());
            } else if (!magicConfig.exists()) {
                throw new IOException("Magic config file not found at: " + magicConfig.getAbsolutePath());
            }
        } catch (SecurityException se) {
            throw new IOException("Security Exception reading file: " + se.getMessage());
        }

        return describe(Executrix.readDataFromFile(target.getAbsolutePath()), magicConfig);
    }

    /**
     * Do not load magic file globally and do not compare against the global magic number list and compare target against
     * the specified magic file instead. The magic file will be read/parsed each time as the comparative file. Useful for
     * debugging or if certain files can be narrowed down to a smaller magic file list improving id performance.
     *
     * @param sample a byte[] containing the data to be id'd
     * @param magicConfig the magic file containing the magic number entries to use
     * @return {@link String} representing the id description or null
     * @throws IOException If an IO error occurs while reading either file.
     */
    public static String describe(final byte[] sample, final File magicConfig) throws IOException {
        try {
            if (!magicConfig.exists()) {
                throw new IOException("Magic config file not found at: " + magicConfig.getAbsolutePath());
            }
        } catch (SecurityException se) {
            throw new IOException("Security Exception reading file: " + se.getMessage());
        }

        final List<MagicNumber> magicNumberList =
                MagicNumberFactory.buildMagicNumberList(Executrix.readDataFromFile(magicConfig.getAbsolutePath()), null, null);

        String description = null;
        for (final MagicNumber item : magicNumberList) {
            description = item.describe(sample);
            if (description != null) {
                break;
            }
        }
        return description;
    }

    /**
     * Load the magic number list globally.
     *
     * @param config the java.io.File pointing to the magic file
     * @exception IOException if one occurs while reading the config file or if a security access error occurs
     */
    public void load(final File config) throws IOException {
        load(config, false);
    }

    /**
     * Load the magic number list globally.
     *
     * @param config the java.io.File pointing to the magic file
     * @param swallowParseException should we swallow Ignorable ParseException or bubble them up
     * @exception IOException if one occurs while reading the config file or if a security access error occurs
     */
    public void load(final File config, final boolean swallowParseException) throws IOException {
        try {
            if (!config.exists()) {
                throw new IOException("File not found");
            }
        } catch (SecurityException se) {
            throw new IOException("Security Exception: " + se.getMessage());
        }

        List<String> mErrorList = null;
        Map<String, List<String>> mExtErrorMap = null;
        if (this.logErrors) {
            mErrorList = this.errorList;
            mExtErrorMap = this.extErrorMap;
        }
        this.magicNumbers.addAll(MagicNumberFactory.buildMagicNumberList(Executrix.readDataFromFile(config.getAbsolutePath()), mErrorList,
                mExtErrorMap, swallowParseException));
    }

    /**
     * Load the magic number list globally.
     *
     * @param configData the byte[] containing the the magic number entry data
     */
    public void load(final byte[] configData) {
        List<String> mErrorList = null;
        Map<String, List<String>> mExtErrorMap = null;
        if (this.logErrors) {
            mErrorList = this.errorList;
            mExtErrorMap = this.extErrorMap;
        }
        this.magicNumbers.addAll(MagicNumberFactory.buildMagicNumberList(configData, mErrorList, mExtErrorMap));
    }

    public int size() {
        return this.magicNumbers.size();
    }

    public String getErrorLog() {
        if (!this.logErrors) {
            return "";
        }
        return getErrorLog(this.magicNumbers, this.errorList, this.extErrorMap);
    }

    /**
     * Summarizes
     */
    public String getErrorLog(final List<MagicNumber> magicNumberList, final List<String> zeroDepthErrorList,
            final Map<String, List<String>> continuationErrorMap) {
        final StringBuffer sb = new StringBuffer();
        final String lineBreak = "\n###########################################################";
        sb.append(lineBreak);
        sb.append("\nSUMMARY");
        sb.append(lineBreak);
        sb.append("\nSUCCESSFUL ENTRIES.................................................. ");
        sb.append(magicNumberList.size() - continuationErrorMap.size());
        sb.append("\nFAILED ENTRIES...................................................... ");
        sb.append(zeroDepthErrorList.size());
        sb.append("\nPARTIALLY SUCCESSFUL ENTRIES (failed on some child continuations)... ");
        sb.append(continuationErrorMap.size());
        sb.append('\n');
        sb.append(lineBreak);
        sb.append("\nFAILED ENTRIES (failed on some continuations)\n\n");

        for (final String err : zeroDepthErrorList) {
            sb.append('\n');
            sb.append("ENTRY (STATUS:FAILED): ");
            sb.append(err);
        }

        sb.append('\n');
        sb.append(lineBreak);
        sb.append("\nPARTIALLY SUCCESSFUL ENTRIES (failed on some extensions)\n\n");

        for (final String entry : continuationErrorMap.keySet()) {
            sb.append('\n');
            sb.append("MAIN ENTRY (STATUS:SUCCESSFUL): ");
            sb.append(entry);

            for (final String extValue : continuationErrorMap.get(entry)) {
                sb.append("\n\tCONTINUATION (STATUS:FAILED): ");
                sb.append(extValue);
            }
        }

        return sb.toString();
    }
}
