package emissary.command.converter;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;

import emissary.pickup.WorkBundle;
import emissary.test.core.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkspaceSortModeConverterTest extends UnitTest {
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
        // test
        assertNull(converter.convert(""));
    }

    @Test
    void convertYoungestFirst() {
        // test
        comparator = converter.convert(WorkspaceSortModeConverter.YOUNGEST_FIRST);

        // verify
        assertTrue(comparator instanceof WorkspaceSortModeConverter.YoungestFirstComparator);
    }

    @Test
    void convertOldestFirst() {
        // test
        comparator = converter.convert(WorkspaceSortModeConverter.OLDEST_FIRST);

        // verify
        assertTrue(comparator instanceof WorkspaceSortModeConverter.OldestFirstComparator);
    }

    @Test
    void convertSmallestFirst() {
        // test
        comparator = converter.convert(WorkspaceSortModeConverter.SMALLEST_FIRST);

        // verify
        assertTrue(comparator instanceof WorkspaceSortModeConverter.SmallestFirstComparator);
    }

    @Test
    void convertLargestFirst() {
        // test
        comparator = converter.convert(WorkspaceSortModeConverter.LARGEST_FIRST);

        // verify
        assertTrue(comparator instanceof WorkspaceSortModeConverter.LargestFirstComparator);
    }

}
