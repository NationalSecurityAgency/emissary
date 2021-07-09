package emissary.util;

import java.util.Objects;

public class IntegerUtil {
    private IntegerUtil() {}

    // Copied from JDK11 version of java.lang.Integer.parseInt()
    public static int parseInt(CharSequence s, int beginIndex, int endIndex, int radix)
            throws NumberFormatException {
        s = Objects.requireNonNull(s);

        if (beginIndex < 0 || beginIndex > endIndex || endIndex > s.length()) {
            throw new IndexOutOfBoundsException();
        }
        if (radix < Character.MIN_RADIX) {
            throw new NumberFormatException("radix " + radix +
                    " less than Character.MIN_RADIX");
        }
        if (radix > Character.MAX_RADIX) {
            throw new NumberFormatException("radix " + radix +
                    " greater than Character.MAX_RADIX");
        }

        boolean negative = false;
        int i = beginIndex;
        int limit = -Integer.MAX_VALUE;

        if (i < endIndex) {
            char firstChar = s.charAt(i);
            if (firstChar < '0') { // Possible leading "+" or "-"
                if (firstChar == '-') {
                    negative = true;
                    limit = Integer.MIN_VALUE;
                } else if (firstChar != '+') {
                    throw new NumberFormatException(s.subSequence(beginIndex,
                            endIndex).toString());
                }
                i++;
                if (i == endIndex) { // Cannot have lone "+" or "-"
                    throw new NumberFormatException(s.subSequence(beginIndex,
                            endIndex).toString());
                }
            }
            int multmin = limit / radix;
            int result = 0;
            while (i < endIndex) {
                // Accumulating negatively avoids surprises near MAX_VALUE
                int digit = Character.digit(s.charAt(i), radix);
                if (digit < 0 || result < multmin) {
                    throw new NumberFormatException(s.subSequence(beginIndex,
                            endIndex).toString());
                }
                result *= radix;
                if (result < limit + digit) {
                    throw new NumberFormatException(s.subSequence(beginIndex,
                            endIndex).toString());
                }
                i++;
                result -= digit;
            }
            return negative ? result : -result;
        } else {
            throw new NumberFormatException("");
        }
    }
}
