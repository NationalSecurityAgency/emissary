package emissary.util;

import emissary.core.BaseDataObject;
import emissary.core.Family;
import emissary.core.IBaseDataObject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShortNameComparatorTest {
    private static final String ABC = "abc";
    private static final String DEF = "def";
    private static final String PARENT = "foo";
    private static final String CHILD_ABC = PARENT + Family.SEP + ABC;
    private static final String CHILD_DEF = PARENT + Family.SEP + DEF;
    private static final String CHILD_1 = PARENT + Family.SEP + "1";
    private static final String CHILD_2 = PARENT + Family.SEP + "2";
    private static final String CHILD_10 = PARENT + Family.SEP + "10";
    private static final String GRANDCHILD_1_1 = CHILD_1 + Family.SEP + "1";

    @Test
    void testParameters() {
        final ShortNameComparator comparator = new ShortNameComparator();
        final IBaseDataObject ibdo = new BaseDataObject();

        assertThrows(IllegalArgumentException.class, () -> comparator.compare(null, ibdo));
        assertThrows(IllegalArgumentException.class, () -> comparator.compare(ibdo, null));
    }

    @Test
    void testComparator() {
        check(ABC, DEF, 0, 0);
        check(CHILD_ABC, CHILD_DEF, -3, 3);
        check(CHILD_1, CHILD_2, -1, 1);
        check(CHILD_1, CHILD_10, -9, 9);
        check(CHILD_1, GRANDCHILD_1_1, -1, 1);
    }

    private static void check(final String name1, final String name2, final int forwardResult, final int reverseResult) {
        final IBaseDataObject ibdo1 = new BaseDataObject(null, name1);
        final IBaseDataObject ibdo2 = new BaseDataObject(null, name2);
        final ShortNameComparator snc = new ShortNameComparator();

        assertEquals(forwardResult, snc.compare(ibdo1, ibdo2));
        assertEquals(reverseResult, snc.compare(ibdo2, ibdo1));
    }
}
