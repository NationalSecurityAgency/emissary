package emissary.util;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Pretend to be Emacs hexl mode
 */
public class Hexl {

    static final int CHARS_PER_LINE = 16;
    static final int COUNTER_SIZE = 4;
    static final String printable = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890!@#$%^&*()_+|~-=\\`{}[]:;<>?,./'\"";

    /**
     * Unformatted hex string
     */
    public static String toUnformattedHexString(byte[] data) {
        return Hex.encodeHexString(data);
    }


    /**
     * Print out the whole byte array as hex
     */
    public static String toHexString(byte[] data) {
        return toHexString(data, data.length);
    }

    /**
     * Print out a portion of the data as a hexdump for debugging
     * 
     * @param limit how many bytes of data to print starting from 0
     */
    @SuppressWarnings("PMD.UselessParentheses")
    public static String toHexString(byte[] data, int limit) {

        StringBuilder output = new StringBuilder(2048);

        int stop = data.length;

        if (limit < stop) {
            stop = limit;
        }

        int lineCount = 0;

        byte[] rhs = new byte[CHARS_PER_LINE];

        int j = 0;

        for (int i = 0; i < stop; i++, j++) {


            // end a line
            if (j >= CHARS_PER_LINE) {
                output.append("  ").append(new String(rhs));
                output.append(System.getProperty("line.separator", "\n"));
                j = 0;
                lineCount++;
            }

            // start a new line
            if (j == 0) {
                String s = Integer.toHexString(lineCount);
                int pad = COUNTER_SIZE - s.length();
                while (pad-- > 0) {
                    output.append("0");
                }
                output.append(s);
                output.append(": ");
            }

            String s = Integer.toHexString(data[i] & 0xff);
            if (s.length() < 2) {
                output.append("0").append(s);
            } else {
                output.append(s);
            }

            // Group hex entries
            if (j % 2 != 0) {
                output.append(" ");
            }

            // Build the rhs
            if (printable.indexOf(data[i]) > -1) {
                rhs[j] = data[i];
            } else {
                rhs[j] = (byte) '.';
            }
        }

        int pad = ((CHARS_PER_LINE * 2) + (CHARS_PER_LINE / 2) + 1) - ((j * 2) + (j / 2) - 1);
        while (pad-- > 0) {
            output.append(" ");
        }
        while (j < CHARS_PER_LINE) {
            rhs[j++] = (byte) ' ';
        }
        output.append(new String(rhs));

        return output.toString();
    }

    /** This class is not meant to be instantiated. */
    private Hexl() {}

    @SuppressWarnings("SystemOut")
    public static void main(String[] argv) {

        if (argv.length < 1) {
            System.err.println("usage: java emissary.util.Hexl datafile1 datafile2 ...");
            return;
        }

        byte[] theContent;

        for (int i = 0; i < argv.length; i++) {

            try (InputStream theFile = Files.newInputStream(Paths.get(argv[i]));
                    DataInputStream theStream = new DataInputStream(theFile)) {
                theContent = IOUtils.toByteArray(theStream);
            } catch (IOException e) {
                System.err.println("Error reading from " + argv[i]);
                continue;
            }

            System.out.println("File " + argv[i]);
            System.out.println(toHexString(theContent));
        }
    }
}
