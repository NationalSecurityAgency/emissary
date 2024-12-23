package emissary.util;

import emissary.core.BaseDataObject;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShortNameComparatorTest extends UnitTest {
    // @formatter: off
    private static final String ABC = "abc";
    private static final String DEF = "def";
    private static final String FOO_ATT_ABC = "foo-att-abc";
    private static final String FOO_ATT_DEF = "foo-att-def";
    private static final String FOO_ATT_1 = "foo-att-1";
    private static final String FOO_ATT_2 = "foo-att-2";
    private static final String FOO_ATT_1_ATT_1 = "foo-att-1-att-1";
    private static final String FOO_ATT_1_ATT_2 = "foo-att-1-att-2";
    private static final String FOO_ATT_10 = "foo-att-10";
    private static final String FOO_ATT_10_ATT_1 = "foo-att-10-att-1";
    // @formatter: on
    private static final ShortNameComparator COMPARATOR = new ShortNameComparator();

    @Test
    void testParameters() {
        final ShortNameComparator comparator = new ShortNameComparator();
        final IBaseDataObject ibdo = new BaseDataObject();

        assertThrows(IllegalArgumentException.class, () -> comparator.compare(null, ibdo));
        assertThrows(IllegalArgumentException.class, () -> comparator.compare(ibdo, null));
    }

    @Test
    void testComparator() {
        check(ABC, DEF, 0);
        check(FOO_ATT_ABC, FOO_ATT_DEF, -3);
        check(FOO_ATT_1, FOO_ATT_2, -1);
        check(FOO_ATT_1, FOO_ATT_10, -9);
        check(FOO_ATT_1, FOO_ATT_1_ATT_1, -1);
    }

    @Test
    void testListSorting() {
        // start with an unsorted list
        List<String> unsorted = List.of(
                FOO_ATT_1,
                FOO_ATT_10,
                FOO_ATT_2,
                FOO_ATT_10_ATT_1,
                FOO_ATT_1_ATT_2,
                FOO_ATT_1_ATT_1);

        // map to a list of IBDO, sort using Comparator, map back to a list of Strings
        List<String> actual = unsorted
                .stream()
                .map(name -> DataObjectFactory.getInstance(new byte[0], name))
                .sorted(COMPARATOR)
                .map(IBaseDataObject::shortName)
                .collect(Collectors.toList());

        // list with shortnames in expected sort order
        List<String> expected = List.of(
                FOO_ATT_1,
                FOO_ATT_1_ATT_1,
                FOO_ATT_1_ATT_2,
                FOO_ATT_2,
                FOO_ATT_10,
                FOO_ATT_10_ATT_1);

        // verify that the sorted output is correct
        assertIterableEquals(expected, actual, "List wasn't sorted in the expected order");
    }

    private static void check(final String name1, final String name2, final int forwardResult) {
        final IBaseDataObject ibdo1 = new BaseDataObject(null, name1);
        final IBaseDataObject ibdo2 = new BaseDataObject(null, name2);
        final ShortNameComparator snc = new ShortNameComparator();

        assertEquals(forwardResult, snc.compare(ibdo1, ibdo2));
        assertEquals(-forwardResult, snc.compare(ibdo2, ibdo1));
    }
}
