package emissary.util.magic;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

public class MagicMath {

    private static final String EMPTYSTRING = "";
    public static final String HEX_PREFIX = "0x";
    private static final String ZERO = "0";
    private static final String PRE_OCT = "0";

    public static int[] literals = new int[] {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1 // 0
            , -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 // 10
            , -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 // 20
            , -1, -1, 32, 33, -1, -1, -1, -1, 38, -1 // 30
            , -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 // 40
            , -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 // 70
            , 60, 61, 62, -1, -1, -1, -1, -1, -1, -1 // 60
            , -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 // 70
            , -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 // 80
            , -1, -1, 92, -1, 94, -1, -1, 97, 98, -1 // 90
            , -1, -1, 102, -1, -1, -1, -1, -1, -1, -1 // 100
            , 10, -1, -1, -1, 13, -1, 116, -1, 118, -1 // 110
            , -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 // 120
            , -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 // 130
            , -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 // 140
            , -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 // 150
            , -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 // 160
            , -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 // 170
            , -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 // 180
            , -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 // 190
            , -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 // 200
            , -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 // 210
            , -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 // 220
            , -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 // 230
            , -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 // 240
            , -1, -1, -1, -1, -1, -1, -1}; // 250


    public static String byteArrayToHexString(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            if (i == 0)
                sb.append(HEX_PREFIX);
            sb.append(Integer.toString((int) b[i], 16));
        }
        return sb.toString();
    }

    public static byte[] parseEscapedString(String s) {
        List<Number> array = new ArrayList<Number>();
        Stack<Character> chars = new Stack<Character>();
        for (int i = (s.length() - 1); i >= 0; i--)
            chars.push(s.charAt(i));
        while (!chars.empty()) {
            Character c = chars.pop();
            String val = EMPTYSTRING;
            if (c.charValue() == '\\') {
                if (chars.empty()) {
                    array.add(Integer.valueOf(32));
                    break;
                }
                Character next = chars.peek();
                if (literals[next.charValue()] > 0) {
                    array.add(literals[next.charValue()]);
                    chars.pop();
                } else if (Character.isDigit(next.charValue())) {
                    int max = 3;
                    while (!chars.empty() && Character.isDigit(next.charValue()) && max-- > 0) {
                        val += chars.pop().charValue();
                        if (!chars.empty())
                            next = chars.peek();
                    }
                    array.add(new BigInteger(val, 8));
                    val = EMPTYSTRING;
                } else if (next.charValue() == 'x') {
                    chars.pop(); // pop the hex symbol
                    val += (chars.pop()).charValue();
                    val += (chars.pop()).charValue();
                    array.add(new BigInteger(val, 16));
                    val = EMPTYSTRING;
                }
                continue;
            }
            array.add(Integer.valueOf(c.charValue()));
        }
        byte[] bytes = new byte[array.size()];
        Iterator<Number> iter = array.iterator();
        for (int i = 0; i < bytes.length; i++) {
            Number num = iter.next();
            bytes[i] = num.byteValue();
        }
        return bytes;
    }

    public static byte[] stringToByteArray(String s) {
        if (s.startsWith(HEX_PREFIX))
            return hexStringToByteArray(s);
        else if (!s.equals(ZERO) && s.startsWith(PRE_OCT))
            return octalStringToByteArray(s.substring(1));
        else
            return decimalStringToByteArray(s);
    }

    public static byte[] octalStringToByteArray(String s) {
        String sub = s.startsWith(PRE_OCT) ? s.substring(1) : s;
        BigInteger integer = new BigInteger(sub, 8);
        return integer.toByteArray();
    }

    public static final String BYTEARRAY_PRECISION_ERROR_RULE =
            "The new byte array length must fit the existing value. Such that n*2^8 > valueOf (data[]).";

    public static byte[] setLength(byte[] data, int length) {

        int actualSize = data.length;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == 0)
                actualSize--;
            else
                break;
        }
        if (data.length == length)
            return data;
        else if (actualSize > length)
            throw new ByteArrayPrecisionException(BYTEARRAY_PRECISION_ERROR_RULE);

        if (length == 0)
            return new byte[0];

        byte[] newValues = new byte[length];
        int ix = data.length - 1;
        for (int i = (length - 1); i >= 0; i--) {
            if (ix < 0)
                newValues[i] = (byte) 0;
            else
                newValues[i] = data[ix--];
        }
        return newValues;
    }

    public static byte[] mask(byte[] data, byte[] maskValues) {
        byte[] target = new byte[data.length];
        for (int i = 0; i < data.length; i++)
            target[i] = (byte) (data[i] & maskValues[i]);
        return target;
    }

    public static byte[] hexStringToByteArray(String s) {
        String subject = s;
        if (subject.startsWith(HEX_PREFIX))
            subject = subject.substring(2);
        if (subject.length() % 2 != 0)
            subject = ZERO + subject;
        byte[] array = new byte[subject.length() / 2];
        for (int i = 0; i < array.length; i++) {
            int b = Integer.parseInt(subject.substring(i * 2, i * 2 + 2), 16);
            array[i] = (byte) (0xff & b);
        }
        return array;
    }

    public static byte[] decimalStringToByteArray(String s) {
        return new BigInteger(s).toByteArray();
    }

    public static int stringToInt(String s) {
        if (s.startsWith(HEX_PREFIX))
            return new BigInteger(s.substring(2), 16).intValue();
        else if (!s.equals("0") && s.startsWith(PRE_OCT))
            return new BigInteger(s.substring(1), 8).intValue();
        else
            return new BigInteger(s, 10).intValue();
    }


    public static byte[] integerToByteArray(int arraySize, long integerValue) {
        byte[] valueBytes = new byte[arraySize];
        for (int i = 0; i < arraySize; i++) {
            valueBytes[arraySize - i - 1] = (byte) ((integerValue) >>> (i * 8) & 0xff);
        }
        return valueBytes;
    }

    public static long stringToLong(String stringValue) {
        if (stringValue.length() > 2 && "0x".equals(stringValue.substring(0, 2)))
            return Long.parseLong(stringValue.substring(2), 16);
        else if (stringValue.length() > 1 && stringValue.charAt(0) == '0')
            return Long.parseLong(stringValue.substring(1), 8);
        else
            return Long.parseLong(stringValue, 10);
    }

    public static byte[] stringToByteArray(int arraySize, String stringValue) {
        if (stringValue == null || stringValue.length() == 0)
            return null;
        if (stringValue.length() > 2 && "0x".equals(stringValue.substring(0, 2)))
            return hexStringToByteArray(stringValue);
        else
            return integerToByteArray(arraySize, stringToLong(stringValue));
    }

    public static String byteArrayToString(byte[] bytes) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < bytes.length; i++) {
            if (i != 0)
                sb.append(", ");
            sb.append(Byte.toString(bytes[i]));
        }
        return sb.toString();
    }

    public static long byteArrayToLong(byte[] data) {
        int actualSize = data.length;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == 0)
                actualSize--;
            else
                break;
        }
        byte[] adjustedData = setLength(data, actualSize);
        BigInteger value = new BigInteger(adjustedData);
        return value.longValue();
    }

    public static String byteArrayToString(byte[] data, int radix) {
        int actualSize = data.length;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == 0)
                actualSize--;
            else
                break;
        }
        if (actualSize == 0)
            return ZERO;
        byte[] adjustedData = setLength(data, actualSize);
        BigInteger value = new BigInteger(adjustedData);
        return value.toString(radix);
    }

    public static void longEndianSwap(byte[] array, int offset) {

        if (array.length < (offset + 4))
            throw new ArrayIndexOutOfBoundsException(array.length + 1);
        byte t = array[offset];
        array[offset] = array[offset + 3];
        array[offset + 3] = t;
        t = array[offset + 1];
        array[offset + 1] = array[offset + 2];
        array[offset + 2] = t;

    }

    public static void shortEndianSwap(byte[] array, int offset) {
        if (array.length < (offset + 2))
            throw new ArrayIndexOutOfBoundsException(array.length + 1);

        byte t = array[offset];
        array[offset] = array[offset + 1];
        array[offset + 1] = t;
    }

    /** This class is not meant to be instantiated. */
    private MagicMath() {}
}
