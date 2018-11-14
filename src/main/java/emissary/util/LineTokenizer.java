package emissary.util;

import java.nio.charset.Charset;

/**
 * Used the default way, this is equivalent to StringTokenizer st = new StringTokenizer(new String(theData),"\n",false);
 * except that a token is returned for blank lines as well. There seems no way to tell the StringTokenizer to do that.
 */
public class LineTokenizer {

    protected int previousIndex = -1;
    protected int index = 0;
    protected byte delim = (byte) '\n';
    protected int tokenCount = 0;
    protected byte[] data;
    protected Charset charset = Charset.forName("8859_1");

    /**
     * Create a line tokenizer to operate on some data
     * 
     * @param theData byte array of data
     */
    public LineTokenizer(byte[] theData) {
        this(theData, (byte) ('\n' & 0xff));
    }

    /**
     * Create a line tokenizer to operate on some data
     * 
     * @param theData byte array of data
     * @param delim the delimiter to mark off lines
     */
    public LineTokenizer(byte[] theData, byte delim) {
        this.delim = delim;
        data = new byte[theData.length];

        // Count delimiter tokens in data
        if (data.length > 0) {
            for (int i = 0; i < theData.length; i++) {
                data[i] = theData[i];
                if (data[i] == delim) {
                    tokenCount++;
                }
            }

            // Trailing portion with no trailing delim
            if (data[data.length - 1] != delim) {
                tokenCount++;
            }
        }
    }

    /**
     * Create a line tokenizer to operate on some data
     * 
     * @param theData byte array of data
     * @param delim the delimiter to mark off lines
     * @param charset the character set to use the outputting tokens as strings
     */
    public LineTokenizer(byte[] theData, byte delim, String charset) {
        this(theData, delim);
        this.charset = (charset == null ? null : Charset.forName(charset));
    }

    /**
     * Create a line tokenizer to operate on some data
     * 
     * @param theData byte array of data
     * @param delim the delimiter to mark off lines
     * @param charset the character set to use the outputting tokens as strings
     */
    public LineTokenizer(byte[] theData, byte delim, Charset charset) {
        this(theData, delim);
        this.charset = charset;
    }

    /**
     * Create a line tokenizer to operate on some data
     * 
     * @param theData byte array of data
     * @param charset the character set to use the outputting tokens as strings
     */
    public LineTokenizer(byte[] theData, String charset) {
        this(theData);
        this.charset = (charset == null ? null : Charset.forName(charset));
    }

    /**
     * Create a line tokenizer to operate on some data
     * 
     * @param theData byte array of data
     * @param charset the character set to use the outputting tokens as strings
     */
    public LineTokenizer(byte[] theData, Charset charset) {
        this(theData);
        this.charset = charset;
    }

    /**
     * Set the character set to use when outputting tokens as strings default is 8859_1
     * 
     * @param charset the java charset value
     */
    public void setCharset(String charset) {
        this.charset = (charset == null ? null : Charset.forName(charset));
    }

    /**
     * Set the character set to use when outputting tokens as strings default is 8859_1
     * 
     * @param charset the java charset value
     */
    public void setCharset(Charset charset) {
        this.charset = charset;
    }

    /**
     * Indicate if there are more lines
     * 
     * @return true if there are more lines
     */
    public boolean hasMoreTokens() {
        return (tokenCount > 0);
    }

    /**
     * Current count of tokens remaining
     * 
     * @return count of tokens remaining
     */
    public int countTokens() {
        return tokenCount;
    }

    /**
     * Current byte offset in the data Caller can use this on their copy of the original data buffer to extract data of
     * interest
     * 
     * @return current byte offset
     */
    public int getCurrentPosition() {
        return index - 1;
    }

    /**
     * Next token as a string The string is created using the charset specified in the constructor or in the
     * setCharset(String s) method
     * 
     * @return the next line as a string
     */
    public String nextToken() {

        byte[] btok = nextTokenBytes();
        String tok = null;

        if (btok != null) {

            // Use the specified charset to create the string
            if (charset != null) {
                tok = new String(btok, charset);
            } else {
                tok = new String(btok);
            }
        }
        return tok;
    }

    /**
     * Next token as an array of bytes
     * 
     * @return the next line as a array of bytes
     */
    public byte[] nextTokenBytes() {

        if (tokenCount == 0) {
            return null;
        }

        int end = index;

        for (; end < data.length && data[end] != delim; end++) {
        }
        ;

        byte[] tok = new byte[end - index];
        System.arraycopy(data, index, tok, 0, end - index);

        tokenCount--;

        if (tokenCount > 0 && data[end] == delim) {
            previousIndex = index;
            index = end + 1;
        }

        return tok;
    }

    /**
     * Push back a single token onto the stack We only take a single push back. This just moves our pointers to the previous
     * index
     */
    public void pushBack() {
        if (previousIndex == -1) {
            return; // already at beginning
        }
        tokenCount++;
        index = previousIndex;
        previousIndex = -1;
    }
}
