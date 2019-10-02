package emissary.parser;

/**
 * Record offset and length of something in an array
 */
public class PositionRecord {

    private long position;
    private long length;

    /**
     * position and length of something important in a data structure
     */
    public PositionRecord(long position, long length) {
        this.position = position;
        this.length = length;
    }

    /**
     * Take an array of two ints for start and length
     * 
     * @param posAndLen Position at 0 and Length at 1
     */
    public PositionRecord(long[] posAndLen) {
        if (posAndLen != null && posAndLen.length == 2) {
            this.position = posAndLen[0];
            this.length = posAndLen[1];
        }
    }


    /**
     * Starting position of data
     * 
     * @return a long with the position
     */
    public long getPosition() {
        return position;
    }

    /**
     * Length of data
     * 
     * @return a long with the length
     */
    public long getLength() {
        return length;
    }

    /**
     * Pseudo property
     * 
     * @return a long that is position + length
     */
    public long getEnd() {
        return position + length;
    }

    @Override
    public String toString() {
        return "{" + position + "/" + length + "}";
    }
}
