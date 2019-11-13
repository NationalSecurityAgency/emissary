/*
  $Id$
 */


package emissary.util.search;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The byte tokenizer class allows an application to break a byte buffer into tokens. This was modified from the
 * java.util.StringTokenizer implementation. Note that all characters in the deliminter set are considered to be
 * characters in the range 0 - 255. In other words the ISO8859-1 encoding is used to match the delimiters to the byte
 * array.
 */
public class ByteTokenizer implements Enumeration<String> {
    private int currentPosition;
    private int newPosition;
    private int maxPosition;
    private byte[] data;
    private String delimiters;
    private boolean retDelims;
    private boolean delimsChanged;
    private String encoding;

    private static final Logger logger = LoggerFactory.getLogger(ByteTokenizer.class);

    /**
     * maxDelimChar stores the value of the delimiter character with the highest value. It is used to optimize the detection
     * of delimiter characters.
     */
    private char maxDelimChar;

    /**
     * Set maxDelimChar to the highest char in the delimiter set.
     */
    private void setMaxDelimChar() {
        if (delimiters == null) {
            maxDelimChar = 0;
            return;
        }

        char m = 0;
        for (int i = 0; i < delimiters.length(); i++) {
            char c = delimiters.charAt(i);
            if (m < c) {
                m = c;
            }
        }
        maxDelimChar = m;
    }

    /**
     * Constructs a byte tokenizer for the specified byte array. All characters in the <code>delim</code> argument are the
     * delimiters for separating tokens. Characters must be in the range of 0 - 255.
     * <p>
     * If the <code>returnDelims</code> flag is <code>true</code>, then the delimiter characters are also returned as
     * tokens. Each delimiter is returned as a byte[] or String of length one. If the flag is <code>false</code>, the
     * delimiter characters are skipped and only serve as separators between tokens.
     *
     * @param bytes a byte array to be parsed.
     * @param start the first byte in the array
     * @param len the number of bytes to parse
     * @param delim the delimiters.
     * @param returnDelims flag indicating whether to return the delimiters as tokens.
     */
    public ByteTokenizer(byte[] bytes, int start, int len, String delim, boolean returnDelims) {
        currentPosition = start;
        newPosition = -1;
        delimsChanged = false;
        data = bytes;
        maxPosition = start + len;
        delimiters = delim;
        retDelims = returnDelims;
        setMaxDelimChar();
    }

    /**
     * Constructs a byte tokenizer for the specified byte array using the specified encoding.
     *
     * @param bytes a byte array to be parsed.
     * @param start the first byte in the array
     * @param len the number of bytes to parse
     * @param delim the delimiters.
     * @param returnDelims flag indicating whether to return the delimiters as tokens.
     * @param encoding the encoding for which to return the bytes.
     * @exception UnsupportedEncodingException thrown if the supplied encoding is unsupported.
     */
    public ByteTokenizer(byte[] bytes, int start, int len, String delim, boolean returnDelims, String encoding) throws UnsupportedEncodingException {
        this(bytes, start, len, delim, returnDelims);
        // call this to ensure the encoding is supported
        // CharToByteConverter.getConverter(encoding);
        try {
            Charset c = Charset.forName(encoding);
            logger.debug("Loaded charset " + c);
        } catch (Exception ex) {
            throw new UnsupportedEncodingException("No support for " + encoding);
        }
        this.encoding = encoding;
    }

    /**
     * Constructs a byte tokenizer for the specified byte array. The characters in the <code>delim</code> argument are the
     * delimiters for separating tokens. Delimiter characters themselves will not be treated as tokens.
     *
     * @param bytes a byte array to be parsed.
     * @param start the first byte in the array
     * @param len the number of bytes to parse
     * @param delim the delimiters.
     */
    public ByteTokenizer(byte[] bytes, int start, int len, String delim) {
        this(bytes, start, len, delim, false);
    }

    /**
     * Constructs a byte tokenizer for the specified byte array using the specified encoding.
     *
     * @param bytes a byte array to be parsed.
     * @param start the first byte in the array
     * @param len the number of bytes to parse
     * @param delim the delimiters.
     * @param encoding the encoding for which to return the bytes.
     * @exception UnsupportedEncodingException thrown if the supplied encoding is unsupported.
     */
    public ByteTokenizer(byte[] bytes, int start, int len, String delim, String encoding) throws UnsupportedEncodingException {
        this(bytes, start, len, delim, false, encoding);
    }

    /**
     * Constructs a byte tokenizer for the specified byte array. The tokenizer uses the default delimiter set, which is
     * <code>"&nbsp;&#92;t&#92;n&#92;r&#92;f"</code>: the space character, the tab character, the newline character, the
     * carriage-return character, and the form-feed character. Delimiter characters themselves will not be treated as
     * tokens.
     *
     * @param bytes a byte array to be parsed.
     * @param start the first byte in the array
     * @param len the number of bytes to parse
     */
    public ByteTokenizer(byte[] bytes, int start, int len) {
        this(bytes, start, len, " \t\n\r\f", false);
    }

    /**
     * Constructs a byte tokenizer for the specified byte array. All characters in the <code>delim</code> argument are the
     * delimiters for separating tokens. Characters must be in the range of 0 - 255.
     * <p>
     * If the <code>returnDelims</code> flag is <code>true</code>, then the delimiter characters are also returned as
     * tokens. Each delimiter is returned as a byte[] or String of length one. If the flag is <code>false</code>, the
     * delimiter characters are skipped and only serve as separators between tokens.
     *
     * @param bytes a byte array to be parsed.
     * @param delim the delimiters.
     * @param returnDelims flag indicating whether to return the delimiters as tokens.
     */
    public ByteTokenizer(byte[] bytes, String delim, boolean returnDelims) {
        this(bytes, 0, bytes.length, delim, returnDelims);
    }

    /**
     * Constructs a byte tokenizer for the specified byte array using the specified encoding.
     *
     * @param bytes a byte array to be parsed.
     * @param delim the delimiters.
     * @param returnDelims flag indicating whether to return the delimiters as tokens.
     * @param encoding the encoding for which to return the bytes.
     * @exception UnsupportedEncodingException thrown if the supplied encoding is unsupported.
     */
    public ByteTokenizer(byte[] bytes, String delim, boolean returnDelims, String encoding) throws UnsupportedEncodingException {
        this(bytes, 0, bytes.length, delim, returnDelims, encoding);
    }

    /**
     * Constructs a byte tokenizer for the specified byte array. The characters in the <code>delim</code> argument are the
     * delimiters for separating tokens. Delimiter characters themselves will not be treated as tokens.
     *
     * @param bytes a byte array to be parsed.
     * @param delim the delimiters.
     */
    public ByteTokenizer(byte[] bytes, String delim) {
        this(bytes, 0, bytes.length, delim);
    }

    /**
     * Constructs a byte tokenizer for the specified byte array using the specified encoding.
     *
     * @param bytes a byte array to be parsed.
     * @param delim the delimiters.
     * @param encoding the encoding for which to return the bytes.
     * @exception UnsupportedEncodingException thrown if the supplied encoding is unsupported.
     */
    public ByteTokenizer(byte[] bytes, String delim, String encoding) throws UnsupportedEncodingException {
        this(bytes, 0, bytes.length, delim, encoding);
    }

    /**
     * Constructs a byte tokenizer for the specified byte array. The tokenizer uses the default delimiter set, which is
     * <code>"&nbsp;&#92;t&#92;n&#92;r&#92;f"</code>: the space character, the tab character, the newline character, the
     * carriage-return character, and the form-feed character. Delimiter characters themselves will not be treated as
     * tokens.
     *
     * @param bytes a byte array to be parsed.
     */
    public ByteTokenizer(byte[] bytes) {
        this(bytes, 0, bytes.length);
    }

    /**
     * Skips delimiters starting from the specified position. If retDelims is false, returns the index of the first
     * non-delimiter character at or after startPos. If retDelims is true, startPos is returned.
     */
    private int skipDelimiters(int startPos) {
        if (delimiters == null) {
            throw new NullPointerException();
        }

        int position = startPos;
        while (!retDelims && position < maxPosition) {
            char c = (char) (0xFF & (int) (data[position]));
            if ((c > maxDelimChar) || (delimiters.indexOf(c) < 0)) {
                break;
            }
            position++;
        }
        return position;
    }

    /**
     * Skips ahead from startPos and returns the index of the next delimiter character encountered, or maxPosition if no
     * such delimiter is found.
     */
    private int scanToken(int startPos) {
        int position = startPos;
        while (position < maxPosition) {
            char c = (char) (0xFF & (int) (data[position]));
            if ((c <= maxDelimChar) && (delimiters.indexOf(c) >= 0)) {
                break;
            }
            position++;
        }
        if (retDelims && (startPos == position)) {
            char c = (char) (0xFF & (int) (data[position]));
            if ((c <= maxDelimChar) && (delimiters.indexOf(c) >= 0)) {
                position++;
            }
        }
        return position;
    }

    /**
     * Tests if there are more tokens available from this tokenizer's string. If this method returns <code>true</code>, then
     * a subsequent call to <code>nextToken</code> with no argument will successfully return a token.
     *
     * @return <code>true</code> if and only if there is at least one token in the string after the current position;
     *         <code>false</code> otherwise.
     */
    public boolean hasMoreTokens() {
        /*
         * Temporary store this position and use it in the following nextToken() method only if the delimiters haven't been
         * changed in that nextToken() invocation.
         */
        newPosition = skipDelimiters(currentPosition);
        return (newPosition < maxPosition);
    }

    /**
     * Returns the next token from this string tokenizer.
     *
     * @return the next token from this string tokenizer.
     * @exception NoSuchElementException if there are no more tokens in this tokenizer's string.
     */
    public String nextToken() {
        /*
         * If next position already computed in hasMoreElements() and delimiters have changed between the computation and this
         * invocation, then use the computed value.
         */

        currentPosition = (newPosition >= 0 && !delimsChanged) ? newPosition : skipDelimiters(currentPosition);

        /* Reset these anyway */
        delimsChanged = false;
        newPosition = -1;

        if (currentPosition >= maxPosition) {
            throw new NoSuchElementException();
        }
        int start = currentPosition;
        currentPosition = scanToken(currentPosition);

        String token = null;
        try {
            if (encoding != null) {
                token = new String(data, start, currentPosition - start, encoding);
            } else {
                token = new String(data, start, currentPosition - start);
            }
        } catch (UnsupportedEncodingException uee) {
            // cannot happen...we already verified in constructer
        }
        return token;
    }

    /**
     * Returns the next token in this string tokenizer's string. First, the set of characters considered to be delimiters by
     * this <code>ByteTokenizer</code> object is changed to be the characters in the string <code>delim</code>. Then the
     * next token in the string after the current position is returned. The current position is advanced beyond the
     * recognized token. The new delimiter set remains the default after this call.
     *
     * @param delim the new delimiters.
     * @return the next token, after switching to the new delimiter set.
     * @exception NoSuchElementException if there are no more tokens in this tokenizer's string.
     */
    public String nextToken(String delim) {
        delimiters = delim;

        /* delimiter string specified, so set the appropriate flag. */
        delimsChanged = true;

        setMaxDelimChar();
        return nextToken();
    }

    /**
     * Returns the same value as the <code>hasMoreTokens</code> method. It exists so that this class can implement the
     * <code>Enumeration</code> interface.
     *
     * @return <code>true</code> if there are more tokens; <code>false</code> otherwise.
     * @see java.util.Enumeration
     * @see ByteTokenizer#hasMoreTokens()
     */
    @Override
    public boolean hasMoreElements() {
        return hasMoreTokens();
    }

    /**
     * Returns the same value as the <code>nextToken</code> method, except that its declared return value is
     * <code>Object</code> rather than <code>String</code>. It exists so that this class can implement the
     * <code>Enumeration</code> interface.
     *
     * @return the next token in the string.
     * @exception NoSuchElementException if there are no more tokens in this tokenizer's string.
     * @see java.util.Enumeration
     * @see ByteTokenizer#nextToken()
     */
    @Override
    public String nextElement() {
        return nextToken();
    }

    /**
     * Calculates the number of times that this tokenizer's <code>nextToken</code> method can be called before it generates
     * an exception. The current position is not advanced.
     *
     * @return the number of tokens remaining in the string using the current delimiter set.
     * @see ByteTokenizer#nextToken()
     */
    public int countTokens() {
        int count = 0;
        int currpos = currentPosition;
        while (currpos < maxPosition) {
            currpos = skipDelimiters(currpos);
            if (currpos >= maxPosition) {
                break;
            }
            currpos = scanToken(currpos);
            count++;
        }
        return count;
    }
}
