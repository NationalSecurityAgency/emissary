package emissary.command.converter;

import emissary.pickup.Priority;
import emissary.pickup.PriorityDirectory;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PriorityDirectoryConverterTest extends UnitTest {
    private PriorityDirectoryConverter converter;
    @Nullable
    private PriorityDirectory pd;

    @Override
    @BeforeEach
    public void setUp() {
        converter = new PriorityDirectoryConverter();
        pd = null;
    }

    @Test
    void convert() {
        // test
        pd = converter.convert("/SomePath");

        // verify
        assertEquals("/SomePath/", pd.getDirectoryName());
        assertEquals(Priority.DEFAULT, pd.getPriority());
    }

    @Test
    void convertNull() {
        // This case should never happen due to Picocli test
        assertThrows(NullPointerException.class, () -> converter.convert(null));
    }

    @Test
    void convertEmpty() {
        // test
        pd = converter.convert("");

        // verify
        assertEquals("/", pd.getDirectoryName());
        assertEquals(Priority.DEFAULT, pd.getPriority());
    }

    @Test
    void convertPrioritySyntax() {
        // test
        pd = converter.convert("/SomePath/SomeSub:10");

        // verify
        assertEquals("/SomePath/SomeSub/", pd.getDirectoryName());
        assertEquals(10, pd.getPriority());
    }

    @Test
    void convertBadPrioritySyntax() {
        // TODO Investigate if we should add additional checking in the parameter converter to make an exception thrown
        // test
        pd = converter.convert("/SomePath/SomeSub:10:23");

        // verify
        assertEquals("/SomePath/SomeSub:10/", pd.getDirectoryName());
        assertEquals(23, pd.getPriority());
    }


}
