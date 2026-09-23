package emissary.util.magic;

import java.io.Serial;

/**
 * Exception used within the magic package - external clients never encounter this exception
 */

public class ByteArrayPrecisionException extends NumberFormatException {
    @Serial
    private static final long serialVersionUID = -50977746714895277L;

    public ByteArrayPrecisionException(String msg) {
        super(msg);
    }
}
