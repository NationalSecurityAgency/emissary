package emissary.transform.decode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import emissary.util.shell.Executrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonEscape {
    /* our logger */
    private final static Logger logger = LoggerFactory.getLogger(JsonEscape.class);

    private final static String ESCAPES = "ntr\"'/";

    /**
     * Unescape a bunch of JSON bytes that might have \\uxxxx character values. Should already be UTF-8 since JSON is
     * specified as UTF-8 by RFC 4627
     */
    public static byte[] unescape(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < data.length; i++) {
            if (data[i] == '\\' && (i + 5) < data.length && (data[i + 1] == 'u' || data[i + 1] == 'U')) {
                // process unicode escape
                try {
                    String s = new String(data, i + 2, 4);
                    char[] c = HtmlEscape.unescapeHtmlChar(s, true);
                    if (c != null && c.length > 0) {
                        out.write(new String(c).getBytes());
                        logger.debug("Unicode '" + s + "' ==> '" + new String(c) + "'");
                        i += 5;
                    } else {
                        out.write(data[i]);
                    }
                } catch (IOException iox) {
                    out.write(data[i]);
                }
            } else if (data[i] == '\\' && (i + 1) < data.length && isOctalDigit(data[i + 1])) {
                // Process octal escape
                int end = i + 1;
                if ((i + 2) < data.length && isOctalDigit(data[i + 2]))
                    end++;
                if ((i + 3) < data.length && isOctalDigit(data[i + 3]))
                    end++;
                String s = new String(data, i + 1, (end - i));
                try {
                    int num = Integer.parseInt(s, 8);
                    char[] ch = Character.toChars(num);
                    out.write(new String(ch).getBytes());
                    logger.debug("Octal '" + s + "' ==> '" + new String(ch) + "'");
                    i += s.length();
                } catch (Exception ex) {
                    out.write(data[i]);
                }
            } else if (data[i] == '\\' && (i + 1) < data.length && ESCAPES.indexOf(data[i + 1]) != -1) {
                byte b = data[i + 1];
                if (b == 'n')
                    out.write('\n');
                else if (b == 't')
                    out.write('\t');
                else if (b == 'r')
                    out.write('\r');
                else
                    out.write(b);
                i++;
            } else {
                out.write(data[i]);
            }
        }
        return out.toByteArray();
    }

    protected static boolean isOctalDigit(byte b) {
        return (b >= '0' && b <= '7');
    }

    /** This class is not meant to be instantiated. */
    private JsonEscape() {}

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < args.length; i++) {
            byte[] content = Executrix.readDataFromFile(args[i]);
            if (content == null) {
                System.out.println(args[i] + ": Unreadable");
                continue;
            }

            System.out.println(args[i]);
            byte[] escaped = JsonEscape.unescape(content);
            System.out.write(escaped, 0, escaped.length);
            System.out.println();
        }
    }
}
