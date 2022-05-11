package emissary.command.converter;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;

import emissary.pickup.WorkBundle;
import emissary.test.core.UnitTest;
import org.junit.Before;
import org.junit.Test;

public class WorkspaceSortModeConverterTest extends UnitTest {
    private Comparator<WorkBundle> comparator;
    private WorkspaceSortModeConverter converter;

    @Before
    @Override
    public void setUp() throws Exception {
        comparator = null;
        converter = new WorkspaceSortModeConverter();
    }

    @Test
    public void convertDefault() throws Exception {
        // test
        assertNull(converter.convert(""));
    }

    @Test
    public void convertYoungestFirst() throws Exception {
        // test
        comparator = converter.convert(WorkspaceSortModeConverter.YOUNGEST_FIRST);

        // verify
        assertTrue(comparator instanceof WorkspaceSortModeConverter.YoungestFirstComparator);
    }

    @Test
    public void convertOldestFirst() throws Exception {
        // test
        comparator = converter.convert(WorkspaceSortModeConverter.OLDEST_FIRST);

        // verify
        assertTrue(comparator instanceof WorkspaceSortModeConverter.OldestFirstComparator);
    }

    @Test
    public void convertSmallestFirst() throws Exception {
        // test
        comparator = converter.convert(WorkspaceSortModeConverter.SMALLEST_FIRST);

        // verify
        assertTrue(comparator instanceof WorkspaceSortModeConverter.SmallestFirstComparator);
    }

    @Test
    public void convertLargestFirst() throws Exception {
        // test
        comparator = converter.convert(WorkspaceSortModeConverter.LARGEST_FIRST);

        // verify
        assertTrue(comparator instanceof WorkspaceSortModeConverter.LargestFirstComparator);
    }

}
