package emissary.transform.decode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * <p>
 * This class decodes the four hexadecimal digits following a {@code \\u} escape sequence and writes the resulting UTF-8
 * character bytes to a caller-supplied output stream. Callers remain responsible for recognizing the escape prefix and
 * advancing their input position after a successful decode.
 * </p>
 */
final class UnicodeEscapeUtil {

    /** This class is not meant to be instantiated. */
    private UnicodeEscapeUtil() {}

    /**
     * Decodes a {@code \\uXXXX} escape sequence at the supplied offset.
     *
     * @param data the source bytes containing the escape sequence
     * @param offset the index of the backslash in the source bytes
     * @param output the destination for the decoded UTF-8 character bytes
     * @return {@code true} if the hexadecimal digits form a valid Unicode character; otherwise {@code false} and no decoded
     *         bytes are written
     */
    static boolean unescapeUnicodeEscape(byte[] data, int offset, ByteArrayOutputStream output) {
        try {
            final String encodedCharacter = new String(data, offset + 2, 4, UTF_8);
            final char[] character = HtmlEscape.unescapeHtmlChar(encodedCharacter, true);
            if (character == null || character.length == 0) {
                return false;
            }
            output.write(new String(character).getBytes(UTF_8));
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
