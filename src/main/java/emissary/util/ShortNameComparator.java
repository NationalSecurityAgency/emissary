package emissary.util;

import java.io.Serializable;
import java.util.Comparator;

import emissary.core.Family;
import emissary.core.IBaseDataObject;

/**
 * Allow a Collection or Array of IBaseDataObject to be sorted by shortName such that all attachments come in order and
 * all parents are immediately followed by their children and all siblings are in numerical (i.e. not string) order.
 */
public class ShortNameComparator implements Comparator<IBaseDataObject>, Serializable {

    // Serializable
    static final long serialVersionUID = -7621558910791975422L;

    @Override
    public int compare(IBaseDataObject obj1, IBaseDataObject obj2) {
        String s1 = obj1.shortName();
        String s2 = obj2.shortName();
        int index1 = s1.indexOf(Family.SEP, 0);
        int index2 = s2.indexOf(Family.SEP, 0);

        while (index1 >= 0) {
            if (index1 >= 0 && index2 < 0) {
                return 1;
            }

            index1 += Family.SEP.length();
            index2 += Family.SEP.length();

            final int nextIndex1 = s1.indexOf(Family.SEP, index1);
            final int nextIndex2 = s2.indexOf(Family.SEP, index2);
            final int length1 = ((nextIndex1 < 0) ? s1.length() : nextIndex1) - index1;
            final int length2 = ((nextIndex2 < 0) ? s2.length() : nextIndex2) - index2;

            if (length1 == length2 && s1.regionMatches(index1, s2, index2, length1)) {
                index1 = nextIndex1;
                index2 = nextIndex2;

                continue;
            }

            try {
                int int1 = parseInt(s1, 10, index1, length1);
                int int2 = parseInt(s2, 10, index2, length2);

                return int1 - int2;
            } catch (NumberFormatException e) {
                final String substring1 = s1.substring(index1, index1 + length1);
                final String substring2 = s2.substring(index2, index2 + length2);

                return substring1.compareTo(substring2);
            }
        }

        if (index1 < 0 && index2 >= 0) {
            return -1;
        }

        return 0;
    }

    // Based on java.lang.Integer.parseInt()
    public static int parseInt(final String s, final int radix, final int start,
            final int length) throws NumberFormatException {
        if (s == null) {
            throw new NumberFormatException("null");
        }

        if (radix < Character.MIN_RADIX) {
            throw new NumberFormatException("radix " + radix + " less than Character.MIN_RADIX");
        }

        if (radix > Character.MAX_RADIX) {
            throw new NumberFormatException("radix " + radix + " greater than Character.MAX_RADIX");
        }

        int result = 0;
        boolean negative = false;
        int i = start, len = length;
        int limit = -Integer.MAX_VALUE;
        int multmin;
        int digit;

        if (len > 0) {
            char firstChar = s.charAt(i);
            if (firstChar < '0') { // Possible leading "+" or "-"
                if (firstChar == '-') {
                    negative = true;
                    limit = Integer.MIN_VALUE;
                } else if (firstChar != '+')
                    throw new NumberFormatException(s);

                if (len == 1) // Cannot have lone "+" or "-"
                    throw new NumberFormatException(s);
                i++;
            }
            multmin = limit / radix;
            while ((i - start) < len) {
                // Accumulating negatively avoids surprises near MAX_VALUE
                digit = Character.digit(s.charAt(i++), radix);
                if (digit < 0) {
                    throw new NumberFormatException(s);
                }
                if (result < multmin) {
                    throw new NumberFormatException(s);
                }
                result *= radix;
                if (result < limit + digit) {
                    throw new NumberFormatException(s);
                }
                result -= digit;
            }
        } else {
            throw new NumberFormatException(s);
        }
        return negative ? result : -result;
    }
}
