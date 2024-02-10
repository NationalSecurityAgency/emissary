package emissary.command.converter;

import emissary.pickup.WorkBundle;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import javax.annotation.Nullable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceSortModeConverterTest extends UnitTest {
    @Nullable
    private Comparator<WorkBundle> comparator;
    private WorkspaceSortModeConverter converter;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        comparator = null;
        converter = new WorkspaceSortModeConverter();
    }

    @Test
    void convertDefault() {
        assertNull(converter.convert(""));
    }

    @Test
    void convertYoungestFirst() {
        comparator = converter.convert(WorkspaceSortModeConverter.YOUNGEST_FIRST);
        assertTrue(comparator instanceof WorkspaceSortModeConverter.YoungestFirstComparator);
        testComparePriority(comparator);

        WorkBundle one = new WorkBundle();
        WorkBundle two = new WorkBundle();

        one.setYoungestFileModificationTime(1);
        two.setYoungestFileModificationTime(2);
        assertEquals(1, comparator.compare(one, two));
        assertEquals(-1, comparator.compare(two, one));

        two.setYoungestFileModificationTime(1);
        assertEquals(0, comparator.compare(one, two));
    }

    @Test
    void convertOldestFirst() {
        comparator = converter.convert(WorkspaceSortModeConverter.OLDEST_FIRST);
        assertTrue(comparator instanceof WorkspaceSortModeConverter.OldestFirstComparator);
        testComparePriority(comparator);

        WorkBundle one = new WorkBundle();
        WorkBundle two = new WorkBundle();

        one.setOldestFileModificationTime(1);
        two.setOldestFileModificationTime(2);
        assertEquals(-1, comparator.compare(one, two));
        assertEquals(1, comparator.compare(two, one));

        two.setOldestFileModificationTime(1);
        assertEquals(0, comparator.compare(one, two));
    }

    @Test
    void convertSmallestFirst() {
        comparator = converter.convert(WorkspaceSortModeConverter.SMALLEST_FIRST);
        assertTrue(comparator instanceof WorkspaceSortModeConverter.SmallestFirstComparator);
        testComparePriority(comparator);

        WorkBundle one = new WorkBundle();
        WorkBundle two = new WorkBundle();

        one.setTotalFileSize(1);
        two.setTotalFileSize(2);
        assertEquals(-1, comparator.compare(one, two));
        assertEquals(1, comparator.compare(two, one));

        two.setTotalFileSize(1);
        assertEquals(0, comparator.compare(one, two));
    }

    @Test
    void convertLargestFirst() {
        comparator = converter.convert(WorkspaceSortModeConverter.LARGEST_FIRST);
        assertTrue(comparator instanceof WorkspaceSortModeConverter.LargestFirstComparator);
        testComparePriority(comparator);

        WorkBundle one = new WorkBundle();
        WorkBundle two = new WorkBundle();

        one.setTotalFileSize(1);
        two.setTotalFileSize(2);
        assertEquals(1, comparator.compare(one, two));
        assertEquals(-1, comparator.compare(two, one));

        two.setTotalFileSize(1);
        assertEquals(0, comparator.compare(one, two));
    }

    private void testComparePriority(Comparator<WorkBundle> comparator) {
        WorkBundle one = new WorkBundle();
        WorkBundle two = new WorkBundle();

        one.setPriority(1);
        two.setPriority(2);
        assertEquals(-1, comparator.compare(one, two));
        assertEquals(1, comparator.compare(two, one));

        two.setPriority(1);
        assertEquals(0, comparator.compare(one, two));
    }

}
