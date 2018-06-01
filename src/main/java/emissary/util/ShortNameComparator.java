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

        String[] p1 = s1.split(Family.SEP);
        String[] p2 = s2.split(Family.SEP);

        for (int i = 1; i < p1.length; i++) {

            if (i >= p2.length) {
                return 1;
            }

            if (p1[i].equals(p2[i])) {
                continue;
            }

            try {
                int i1 = Integer.parseInt(p1[i]);
                int i2 = Integer.parseInt(p2[i]);
                return i1 - i2;
            } catch (NumberFormatException e) {
                return p1[i].compareTo(p2[i]);
            }

        }

        if (p2.length > p1.length) {
            return -1;
        }
        return 0;
    }

}
