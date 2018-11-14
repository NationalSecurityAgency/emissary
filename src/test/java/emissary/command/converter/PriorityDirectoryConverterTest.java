package emissary.command.converter;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

import emissary.pickup.Priority;
import emissary.pickup.PriorityDirectory;
import emissary.test.core.UnitTest;
import org.hamcrest.junit.ExpectedException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class PriorityDirectoryConverterTest extends UnitTest {
    @Rule
    public ExpectedException expected = ExpectedException.none();
    private PriorityDirectoryConverter converter;
    private PriorityDirectory pd;

    @Before
    public void setUp() {
        converter = new PriorityDirectoryConverter();
        pd = null;
    }

    @Test
    public void convert() throws Exception {
        // test
        pd = converter.convert("/SomePath");

        // verify
        assertThat(pd.getDirectoryName(), equalTo("/SomePath/"));
        assertThat(pd.getPriority(), equalTo(Priority.DEFAULT));
    }

    @Test(expected = NullPointerException.class)
    public void convertNull() {
        // This case should never happen due to JCommander
        // test
        converter.convert(null);
    }

    @Test
    public void convertEmpty() {
        // test
        pd = converter.convert("");

        // verify
        assertThat(pd.getDirectoryName(), equalTo("/"));
        assertThat(pd.getPriority(), equalTo(Priority.DEFAULT));
    }

    @Test
    public void convertPrioritySyntax() {
        // test
        pd = converter.convert("/SomePath/SomeSub:10");

        // verify
        assertThat(pd.getDirectoryName(), equalTo("/SomePath/SomeSub/"));
        assertThat(pd.getPriority(), equalTo(10));
    }

    @Test
    public void convertBadPrioritySyntax() {
        // TODO Investigate if we should add additional checking in the parameter converter to make an exception thrown
        // test
        pd = converter.convert("/SomePath/SomeSub:10:23");

        // verify
        assertThat(pd.getDirectoryName(), equalTo("/SomePath/SomeSub:10/"));
        assertThat(pd.getPriority(), equalTo(23));
    }


}
