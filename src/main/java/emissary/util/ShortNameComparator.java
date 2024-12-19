package emissary.util;

import emissary.core.Family;
import emissary.core.IBaseDataObject;

import org.apache.commons.lang3.Validate;

import java.io.Serializable;
import java.util.Comparator;

/**
 * Allow a Collection or Array of IBaseDataObject to be sorted by shortName such that all attachments come in order and
 * all parents are immediately followed by their children and all siblings are in numerical (i.e. not string) order.
 */
public class ShortNameComparator implements Comparator<IBaseDataObject>, Serializable {

    // Serializable
    static final long serialVersionUID = -7621558910791975422L;

    @Override
    public int compare(IBaseDataObject obj1, IBaseDataObject obj2) {
        Validate.isTrue(obj1 != null, "Required: obj1 != null");
        Validate.isTrue(obj2 != null, "Required: obj2 != true");

        String s1 = obj1.shortName();
        String s2 = obj2.shortName();
        int index1 = s1.indexOf(Family.SEP);
        int index2 = s2.indexOf(Family.SEP);

        // Loop through each level in obj1's shortName.
        while (index1 != -1) {
            // If obj1's shortName has more levels than obj2's shortName then obj1 > obj2.
            if (index2 == -1) {
                return 1;
            }

            // Get start character of level.
            index1 += Family.SEP.length();
            index2 += Family.SEP.length();

            // Get character length of level.
            final int nextIndex1 = s1.indexOf(Family.SEP, index1);
            final int nextIndex2 = s2.indexOf(Family.SEP, index2);
            final int length1 = ((nextIndex1 <= -1) ? s1.length() : nextIndex1) - index1;
            final int length2 = ((nextIndex2 <= -1) ? s2.length() : nextIndex2) - index2;

            // If the obj1's level and obj2's level are the same, then go to the next level.
            if (length1 == length2 && s1.regionMatches(index1, s2, index2, length1)) {
                index1 = nextIndex1;
                index2 = nextIndex2;

                continue;
            }

            // Otherwise, try comparing the levels as integers.
            try {
                int int1 = Integer.parseInt(s1, index1, index1 + length1, 10);
                int int2 = Integer.parseInt(s2, index2, index2 + length2, 10);

                return int1 - int2;
            } catch (NumberFormatException e) {
                // Otherwise, compare the levels as strings.
                final String substring1 = s1.substring(index1, index1 + length1);
                final String substring2 = s2.substring(index2, index2 + length2);

                return substring1.compareTo(substring2);
            }
        }

        // If obj1's shortName has fewer levels than obj2's shortName then obj1 < obj2.
        if (index2 != -1) {
            return -1;
        }

        // Otherwise, obj1 and obj2 compare as equivalent
        return 0;
    }
}
