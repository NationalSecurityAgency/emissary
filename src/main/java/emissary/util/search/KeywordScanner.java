package emissary.util.search;

import java.nio.charset.Charset;

/**
 * Provides the ability to find specified {@code byte[]} patterns inside a larger {@code byte[]}.
 */
public class KeywordScanner {
    private int[] skip = new int[256];
    private int dataLength = -1;
    private byte[] data;
    private byte[] pattern;
    private int patternLength = -1;
    private int lastByte = 0;
    private int lastPosition = 0;
    private boolean caseSensitive = true;

    public KeywordScanner() {
        this(new byte[0]);
    }

    /**
     * Initializes a new {@code KeywordScanner} object with the provided data bytes.
     * 
     * @param data the data to be scanned
     */
    public KeywordScanner(final byte[] data) {
        resetData(data);
    }

    public void resetData(String data) {
        resetData(data, Charset.defaultCharset());
    }

    public void resetData(String data, String charsetName) {
        resetData(data, Charset.forName(charsetName));
    }

    public void resetData(String data, Charset charset) {
        resetData(data.getBytes(charset));
    }

    /**
     * Reset the byte array. Use of this method avoids having to instantiate a new KeywordScanner.
     * 
     * @param data - bytes to match against
     */
    public void resetData(byte[] data) {
        this.data = data;
        if (data != null) {
            this.dataLength = data.length;
        } else {
            this.dataLength = -1;
        }
    }

    /**
     * Returns the first occurrence of the provided pattern in the data.
     * 
     * @param patternArg the byte pattern to scan for, null returns -1
     * @return the index in the data where the pattern begins, -1 if not found
     */
    public int indexOf(final byte[] patternArg) {
        return indexOf(patternArg, 0, this.dataLength);
    }

    /**
     * Returns the first occurrence of the provided pattern in the data, starting from the specified index.
     * <p>
     * There is no restriction on the value of {@code start}. If it is negative, it has the same effect as if it were zero:
     * the entire data will be scanned. If it is greater than the length of the data, it has the same effect as if it were
     * equal to the length of the data: -1 is returned.
     * 
     * @param patternArg the byte pattern to scan for, null returns -1
     * @param start the index to start searching from, negative values treated as 0
     * @return the index in the data where the pattern begins, -1 if not found
     */
    public int indexOf(final byte[] patternArg, final int start) {
        return indexOf(patternArg, start, this.dataLength);
    }

    /**
     * Returns the first occurrence of the provided pattern in the data, starting from the specified index and stopping at
     * the specified index.
     * <p>
     * There is no restriction on the value of {@code start}. If it is negative, it has the same effect as if it were zero:
     * the entire data may be scanned. If it is greater than the length of the data, it has the same effect as if it were
     * equal to the length of the data: -1 is returned.
     * <p>
     * If the value of {@code stop} is negative, greater than the data length, or less than or equal to the start value, -1
     * is returned.
     * 
     * @param patternArg the byte pattern to scan for, null returns -1
     * @param start the index to start searching from, negative values treated as 0
     * @param stop the index to stop searching at, exclusive, negative value returns -1
     * @return the index in the data where the pattern begins, -1 if not found
     */
    public int indexOf(final byte[] patternArg, final int start, final int stop) {
        if ((start >= this.dataLength) || (stop > this.dataLength) || (patternArg == null)) {
            return -1;
        }
        // Adjust the actual start index to 0 if a negative value is provided.
        final int actualStart = Math.max(start, 0);
        this.pattern = patternArg;
        this.patternLength = patternArg.length;
        analyze();
        final int position = match(actualStart, stop);
        this.lastPosition = position;
        return position;
    }

    /**
     * Find the next occurrence of the set pattern, stopping at the specified index.
     * <p>
     * If the value of {@code stop} is negative, greater than or equal to the data length, or less than the previously found
     * index: -1 is returned.
     * <p>
     * This method should follow a call to one of the {@code indexOf} methods. These methods will set the pattern and return
     * the index of the first occurrence of the provided pattern. Calls to this method will then return the index of
     * subsequent occurrences. Without first establishing a pattern in this way, -1 will be returned.
     * 
     * @param stop the index to stop searching at, exclusive, negative value returns -1
     * @return the index, less than the stop index, where the next occurrence of the pattern is found, -1 if not found
     */
    public int findNext(final int stop) {
        if (stop >= this.dataLength) {
            return -1;
        }
        if ((this.lastPosition > stop) || (this.lastPosition < 0)) {
            return -1;
        }
        // if a pattern has not been set, just return -1
        if (this.pattern == null) {
            return -1;
        }
        final int position = match((this.lastPosition + 1), stop);
        this.lastPosition = position;
        return position;
    }

    /**
     * Find the next occurrence of the set pattern.
     * <p>
     * This method should follow a call to one of the {@code indexOf} methods. These methods will set the pattern and return
     * the index of the first occurrence of the provided pattern. Calls to this method will then return the index of
     * subsequent occurrences. Without first establishing a pattern in this way, -1 will be returned.
     * 
     * @return the index where the next occurrence of the pattern is found, -1 if not found
     */
    public int findNext() {
        return findNext(this.dataLength - 1);
    }

    /**
     * Sets the case sensitivity of the scanner.
     * 
     * @param theCase if set to false, the scanner will ignore case. Default is true.
     */
    public void setCaseSensitive(final boolean theCase) {
        this.caseSensitive = theCase;
    }

    /**
     * Returns the case sensitivity set for the scanner.
     * 
     * @return true if the scanner is case sensitive, false otherwise
     */
    public boolean isCaseSensitive() {
        return this.caseSensitive;
    }

    private static int get256Value(final byte b) {
        return ((int) b) & 0xff;
    }

    private void analyze() {
        for (int i = 0; i < 256; i++) {
            this.skip[i] = this.patternLength;
        }
        for (int i = 0; i < this.patternLength - 1; i++) {
            this.skip[get256Value(this.pattern[i])] = this.patternLength - i - 1;
        }
        this.lastByte = get256Value(this.pattern[this.patternLength - 1]);
    }

    private int match(final int start, final int stop) {
        final int compareByte;
        if (this.caseSensitive) {
            compareByte = this.lastByte;
        } else {
            compareByte = lowercase(this.lastByte);
        }

        int matchIndex = -1;
        for (int position = start + this.patternLength - 1; position < stop;) {
            int currentByte = get256Value(this.data[position]);
            if (!this.caseSensitive) {
                currentByte = lowercase(currentByte);
            }
            if (currentByte != compareByte) {
                position += this.skip[currentByte];
            } else {
                if (isSame(position)) {
                    matchIndex = position + 1 - this.patternLength;
                    break;
                }
                position += this.skip[currentByte];
            }
        }

        return matchIndex;
    }

    private int lowercase(final int i) {
        if ((i >= 65) && (i <= 90)) {
            return i + 32;
        } else {
            return i;
        }
    }

    private boolean isSame(final int pos) {
        for (int i = 0; i < this.patternLength; i++) {
            int patternByte = this.pattern[i];
            int dataByte = this.data[pos - this.patternLength + 1 + i];
            if (!this.caseSensitive) {
                patternByte = lowercase(patternByte);
                dataByte = lowercase(dataByte);
            }
            if (patternByte != dataByte) {
                return false;
            }
        }
        return true;
    }
}
