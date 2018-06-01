package emissary.parser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataByteBufferSlicer {
    private static final Logger logger = LoggerFactory.getLogger(DataByteBufferSlicer.class);

    /**
     * Slice data from a buffer based on a single position record
     * 
     * @param data the data to pull from
     * @param r the position record indicating absolute offsets
     */
    public static byte[] makeDataSlice(ByteBuffer data, PositionRecord r) {
        if (r.getLength() > Integer.MAX_VALUE) {
            throw new IllegalStateException("Implementation currently only handles up to Intger.MAX_VALUE lengths");
        }
        int len = (int) r.getLength();
        byte[] n = new byte[len];
        try {
            data.position((int) r.getPosition());
            data.get(n);
        } catch (BufferUnderflowException ex) {
            logger.warn("Underflow getting " + n.length + " bytes at " + r.getPosition());
        }
        return n;
    }

    /**
     * Slice a ByteBuffer based on a list of position record
     * 
     * @param data the data to pull from
     * @param list the list of position records indicating absolute offsets
     */
    public static byte[] makeDataSlice(ByteBuffer data, List<PositionRecord> list) {
        // Nothing to do
        if (list == null || list.isEmpty()) {
            return null;
        }

        // Use higher performing impl when only one record
        if (list.size() == 1) {
            return makeDataSlice(data, list.get(0));
        }

        // Aggregate all the pieces using the baos
        byte[] ret = null;
        ByteArrayOutputStream out = null;
        try {
            out = new ByteArrayOutputStream();

            for (PositionRecord r : list) {
                int start = (int) r.getPosition();
                int len = (int) r.getLength();

                if (len <= 0) {
                    continue;
                }
                if (start < 0) {
                    continue;
                }

                data.position(start);
                try {
                    byte[] n = new byte[len];
                    data.get(n);
                    out.write(n);
                } catch (BufferUnderflowException ex) {
                    logger.error("Underflow getting " + len + " bytes at " + start);
                }
            }
            ret = out.toByteArray();
        } catch (IOException iox) {
            logger.warn("io error on bytearray stream cant happen", iox);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignore) {
                    // empty catch block
                }
            }
        }

        return ret;
    }

    /** This class is not meant to be instantiated. */
    private DataByteBufferSlicer() {}
}
