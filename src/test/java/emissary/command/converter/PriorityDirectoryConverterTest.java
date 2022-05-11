package emissary.command.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import emissary.pickup.Priority;
import emissary.pickup.PriorityDirectory;
import emissary.test.core.UnitTest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PriorityDirectoryConverterTest extends UnitTest {
    @Rule
    public ExpectedException expected = ExpectedException.none();
    private PriorityDirectoryConverter converter;
    private PriorityDirectory pd;

    @Override
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
        assertEquals("/SomePath/", pd.getDirectoryName());
        assertEquals(Priority.DEFAULT, pd.getPriority());
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
        assertEquals("/", pd.getDirectoryName());
        assertEquals(Priority.DEFAULT, pd.getPriority());
    }

    @Test
    public void convertPrioritySyntax() {
        // test
        pd = converter.convert("/SomePath/SomeSub:10");

        // verify
        assertEquals("/SomePath/SomeSub/", pd.getDirectoryName());
        assertEquals(10, pd.getPriority());
    }

    @Test
    public void convertBadPrioritySyntax() {
        // TODO Investigate if we should add additional checking in the parameter converter to make an exception thrown
        // test
        pd = converter.convert("/SomePath/SomeSub:10:23");

        // verify
        assertEquals("/SomePath/SomeSub:10/", pd.getDirectoryName());
        assertEquals(23, pd.getPriority());
    }


}
