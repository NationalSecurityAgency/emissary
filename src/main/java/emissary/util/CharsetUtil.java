package emissary.util;

import java.io.UnsupportedEncodingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A collection of utilities for dealing with different character sets in Java. Mainly with the aim of getting to UTF-8.
 * The j* routines generally take Java CharSet names while the non j* routines take derived charset names.
 *
 * This class contains an interpretation in Java of the GPL method isUTF8, available in C from
 * http://billposer.org/Software/unidesc.html and the copied routine is called LegalUTF8P in Get_UTF32_From_UTF8i.c
 *
 * Copyright (C) 2003-2006 William J. Poser (billposer@alum.mit.edu)
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free
 * Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA or go to the web page:
 * http://www.gnu.org/licenses/gpl.txt.
 *
 * =-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
 *
 * This class contains the Apache Licensed isUnicodeString which is from Jakarta POI http://jakarta.apache.org/poi
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
public class CharsetUtil {
    private static final int[] TrailingBytesForUTF8 = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5};

    @SuppressWarnings("unused")
    private static final long[] OffsetsFromUTF8 = {0x00000000L, 0x00003080L, 0x000e2080L, 0x03c82080L, 0xfa082080L, 0x82082080L};

    // Our logger
    private static final Logger logger = LoggerFactory.getLogger(CharsetUtil.class);


    /**
     * Get an array of UTF-8 characters from the input bytes
     *
     * @param byteArray the input bytes
     * @param charSet <em>derived</em> charSet of the input array
     * @param start index into input array to start copying
     * @param end index into input array to stop copying
     * @return array of UTF8 char
     */
    public static char[] getUTFCharArray(final byte[] byteArray, final String charSet, final int start, final int end) {
        String actualCharSet = charSet;
        if (actualCharSet != null) {
            final String jcs = JavaCharSet.get(actualCharSet);
            if (jcs != null) {
                actualCharSet = jcs;
            }
        }
        return jGetUTFCharArray(byteArray, actualCharSet, start, end);
    }

    /**
     * Get an array of UTF-8 characters from the input bytes
     *
     * @param byteArray the input bytes
     * @param charSet <em>JAVA</em> charSet of the input array
     * @param start byte index into input array to start copying
     * @param end byte index into input array to stop copying
     * @return array of UTF8 char
     */
    public static char[] jGetUTFCharArray(final byte[] byteArray, final String charSet, final int start, final int end) {
        char[] cbuffer = null;

        if (byteArray != null) {

            // Check start value
            if (start > byteArray.length || start < 0) {
                throw new ArrayIndexOutOfBoundsException("start : " + start + ", actual length " + byteArray.length);
            }

            // Check end value
            final int actualEnd;
            if ((end == -1) || (end > byteArray.length)) {
                actualEnd = byteArray.length;
            } else {
                actualEnd = end;
            }

            if (actualEnd <= start) {
                throw new ArrayIndexOutOfBoundsException("start : " + start + ", end : " + actualEnd + ", actual length " + byteArray.length);
            }

            // Convert byteArray to UTF-8
            if (charSet != null) {
                try {
                    final String converted = new String(byteArray, start, actualEnd - start, charSet);
                    cbuffer = converted.toCharArray();
                } catch (UnsupportedEncodingException uee) {
                    logger.warn("Unable to convert to " + charSet);
                    // Convert from Byte Array to Char Array...
                    cbuffer = byteToCharArray(byteArray, start, actualEnd);
                }
            } else {
                // Convert from Byte Array to Char Array...
                cbuffer = byteToCharArray(byteArray, start, actualEnd);
            }
        }

        return cbuffer;
    }

    /**
     * Get a string in the specified encoding from the input String
     */
    public static String getUTFString(final String s, final String charSet) {
        try {
            return new String(s.getBytes("ISO8859_1"), charSet);
        } catch (UnsupportedEncodingException uue) {
            logger.warn("Unable to convert to " + charSet);
        }
        return s;
    }

    /**
     * Get a string in the specified encoding
     *
     * @param data input bytes
     * @param charSet the JAVA charset
     * @return JUCS2 string or null if error
     */
    public static String getUTFString(final byte[] data, final String charSet) {
        try {
            return new String(data, charSet);
        } catch (UnsupportedEncodingException uue) {
            logger.warn("Unable to convert to " + charSet);
        }
        return null;
    }

    /**
     * Convert bytes to chars using platform default encoding
     *
     * @param bArray the input data
     */
    public static char[] byteToCharArray(final byte[] bArray) {
        final String theContent = new String(bArray);
        final char[] cArray = theContent.toCharArray();
        return cArray;
    }

    /**
     * Convert bytes to chars using platform default encoding with begin and end points
     *
     * @param bArray the input data
     * @param start byte index into input to start copy
     * @param end byte index into input to end copy or -1 for end
     */
    private static char[] byteToCharArray(final byte[] bArray, final int start, final int end) {
        final int len = (end == -1 ? bArray.length : (bArray.length < end) ? bArray.length : end) - start;
        final char[] cArray = new char[len];

        for (int j = start; j < len; j++) {
            cArray[j] = (char) (0xFF & bArray[j]);
        }
        return cArray;
    }

    /**
     * test for ascii ness
     *
     * @param s string to test
     * @return true if string is ascii
     */
    public static boolean isAscii(final String s) {
        try {
            final int len = s.length();
            for (int i = 0; i < len; i++) {
                final char c = s.charAt(i);
                if (c <= 0 || c > 127) {
                    return false;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * Do the bytes behind this string represent valid utf8?
     *
     * @param s string to test
     * @return true if string is utf8
     */
    public static boolean isUTF8(final String s) {
        try {
            final byte[] b = s.getBytes("UTF-8");
            return isUTF8(b);
        } catch (UnsupportedEncodingException uue) {
            // World must end.
            logger.warn("JVM must support UTF-8 but doesn't?");
            return false;
        }
    }

    /**
     * do these bytes represent a valid utf8 string?
     *
     * @param data the bytes to check
     * @return true if valid utf8
     */

    public static boolean isUTF8(final byte[] data) {
        return isUTF8(data, 0, data.length);
    }

    /**
     * Check for valid utf8 data. Borrowed from the unidesc package (GPL) by Bill Poser, converted from C to Java. The check
     * runs from offs to dlen-1
     *
     * @param data the bytes to check for validity
     * @param offs beginning offset to check
     * @param dlen ending offset of the range
     * @return true if valid utf8
     */
    @SuppressWarnings("fallthrough")
    public static boolean isUTF8(final byte[] data, final int offs, final int dlen) {
        int pos = offs;
        int a;

        while (pos < dlen) {
            try {
                final int val = data[pos] & 0xff;
                final int len = TrailingBytesForUTF8[val] + 1;
                int srcptr = pos + len;

                switch (len) {
                    default:
                        return false;
                    // everything else falls through when true
                    case 4:
                        a = (data[--srcptr] & 0xff);
                        if (a < 0x80 || a > 0xbf) {
                            return false;
                        }
                    case 3:
                        a = (data[--srcptr] & 0xff);
                        if (a < 0x80 || a > 0xbf) {
                            return false;
                        }
                    case 2:
                        a = (data[--srcptr] & 0xff);
                        if (a > 0xbf) {
                            return false;
                        }

                        switch (val) {
                            // no fall through in this one
                            case 0xe0:
                                if (a < 0xa0) {
                                    return false;
                                }
                                break;
                            case 0xf0:
                                if (a < 0x90) {
                                    return false;
                                }
                                break;
                            case 0xf4:
                                if (a > 0x8f) {
                                    return false;
                                }
                                break;
                            default:
                                if (a < 0x80) {
                                    return false;
                                }
                        }
                    case 1:
                        if (val >= 0x80 && val < 0xc0) {
                            return false;
                        }
                        if (val >= 0xf0) {
                            return false;
                        }
                }
                pos += len;
            } catch (ArrayIndexOutOfBoundsException x) {
                logger.warn("ooops", x);
                return false;
            }
        }
        return true;
    }

    /**
     * See if string has multibyte chars (No longer based on org.apache.poi.util.StringUtil) It would be a bad idea to call
     * this with a very large string
     *
     * @param value string to test
     * @return true if string has at least one multibyte char
     */
    public static boolean hasMultibyte(final String value) {
        if (value == null) {
            return false;
        }
        final int cpc = value.codePointCount(0, value.length());
        final int bc = value.getBytes().length;
        return cpc != bc;
    }

    /** This class is not meant to be instantiated. */
    private CharsetUtil() {}
}
