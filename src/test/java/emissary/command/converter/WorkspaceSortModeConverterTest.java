package emissary.command.converter;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

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
        assertThat(null, equalTo(converter.convert("")));
    }

    @Test
    public void convertYoungestFirst() throws Exception {
        // test
        comparator = converter.convert(WorkspaceSortModeConverter.YOUNGEST_FIRST);

        // verify
        assertThat(true, equalTo(comparator instanceof WorkspaceSortModeConverter.YoungestFirstComparator));
    }

    @Test
    public void convertOldestFirst() throws Exception {
        // test
        comparator = converter.convert(WorkspaceSortModeConverter.OLDEST_FIRST);

        // verify
        assertThat(true, equalTo(comparator instanceof WorkspaceSortModeConverter.OldestFirstComparator));
    }

    @Test
    public void convertSmallestFirst() throws Exception {
        // test
        comparator = converter.convert(WorkspaceSortModeConverter.SMALLEST_FIRST);

        // verify
        assertThat(true, equalTo(comparator instanceof WorkspaceSortModeConverter.SmallestFirstComparator));
    }

    @Test
    public void convertLargestFirst() throws Exception {
        // test
        comparator = converter.convert(WorkspaceSortModeConverter.LARGEST_FIRST);

        // verify
        assertThat(true, equalTo(comparator instanceof WorkspaceSortModeConverter.LargestFirstComparator));
    }

}
