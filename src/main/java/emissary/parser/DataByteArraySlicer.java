package emissary.parser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataByteArraySlicer {

    private static final Logger logger = LoggerFactory.getLogger(DataByteArraySlicer.class);

    /**
     * Slice a data byte array based on a single position record
     * 
     * @param data the data to pull from
     * @param r the position record indicating offsets
     */
    public static byte[] makeDataSlice(byte[] data, PositionRecord r) {
        byte[] n = new byte[(int) r.getLength()];
        System.arraycopy(data, (int) r.getPosition(), n, 0, (int) r.getLength());
        return n;
    }

    /**
     * Slice a data byte array based on a list of position record
     * 
     * @param data the data to pull from
     * @param list the list of position records indicating offsets
     */
    public static byte[] makeDataSlice(byte[] data, List<PositionRecord> list) {

        // Nothing to do
        if (list == null || list.size() == 0) {
            return null;
        }

        // Use higher performing impl when only one record
        if (list.size() == 1) {
            return makeDataSlice(data, list.get(0));
        }


        // Aggregate all the pieces using the baos
        byte[] ret = null;

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            for (PositionRecord r : list) {
                int start = (int) r.getPosition();
                int len = (int) r.getLength();

                if (len == 0) {
                    continue;
                }

                if (len > 0 && start > -1 && (len + start) <= data.length) {
                    out.write(data, start, len);
                }
            }

            ret = out.toByteArray();
            out.close();
        } catch (IOException iox) {
            logger.warn("io error on bytearray stream cant happen", iox);
        }

        return ret;
    }

    /** This class is not meant to be instantiated. */
    private DataByteArraySlicer() {}
}
