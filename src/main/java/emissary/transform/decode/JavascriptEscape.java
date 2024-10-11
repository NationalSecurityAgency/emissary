package emissary.transform.decode;

import emissary.util.shell.Executrix;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Deprecated(forRemoval = true)
public class JavascriptEscape {

    /**
     * Unescape javascript unicode characters in the form backslash-u-nnnn. Browser tests show that only lowercase "u" and
     * only four digits work. Javascript also has normal unix escapes like \n and \r.
     */
    public static byte[] unescape(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < data.length; i++) {
            if (data[i] == '\\' && (i + 5) < data.length && data[i + 1] == 'u') {
                // process unicode escape
                try {
                    String s = new String(data, i + 2, 4);
                    char[] c = HtmlEscape.unescapeHtmlChar(s, true);
                    if (c != null && c.length > 0) {
                        out.write(new String(c).getBytes());
                        // logger.debug("Unicode '{}' ==> '{}'", s, new String(c));
                        i += 5;
                    } else {
                        out.write(data[i]);
                    }
                } catch (IOException iox) {
                    out.write(data[i]);
                }
            } else if (data[i] == '\\' && (i + 1) < data.length && (data[i + 1] == 'n' || data[i + 1] == 'r')) {
                out.write('\n');
            } else {
                out.write(data[i]);
            }
        }

        return out.toByteArray();
    }

    /** This class is not meant to be instantiated. */
    private JavascriptEscape() {}

    @SuppressWarnings("SystemOut")
    public static void main(String[] args) {
        int i = 0;

        for (; i < args.length; i++) {
            byte[] content = Executrix.readDataFromFile(args[i]);
            if (content == null) {
                System.out.println(args[i] + ": Unreadable");
                continue;
            }

            System.out.println(args[i]);
            byte[] escaped = JavascriptEscape.unescape(content);
            System.out.write(escaped, 0, escaped.length);
            System.out.println();
        }
    }
}
