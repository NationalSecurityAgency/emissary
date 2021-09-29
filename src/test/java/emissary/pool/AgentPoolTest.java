package emissary.pool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import emissary.test.core.UnitTest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AgentPoolTest extends UnitTest {

    @Parameterized.Parameter(0)
    public long maxMemoryInBytes;
    @Parameterized.Parameter(1)
    public Integer propertyOverride;
    @Parameterized.Parameter(2)
    public int expectedPoolSize;
    @Parameterized.Parameter(3)
    public Class<? extends Exception> expectedException;
    @Parameterized.Parameter(4)
    public String expectedExceptionMsg;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Parameterized.Parameters
    public static Iterable<Object[]> poolSizeVales() {
        return Arrays.asList(new Object[][] {
                {60000L, null, 15, null, null},
                {1900000000L, null, 20, null, null},
                {2200000000L, null, 25, null, null},
                {9900000000L, null, 50, null, null},
                {0L, 10, 10, null, null},
                {60000L, -10, 15, null, null},
                {0L, -10, 10, IllegalArgumentException.class, "Must be greater then zero."},
                {0L, 0, 10, IllegalArgumentException.class, "Must be greater then zero."},
                {-100L, null, 10, IllegalArgumentException.class, "Must be greater then zero."}
        });
    }

    @Test
    public void testComputePoolSize() throws IllegalArgumentException {
        // setup expected exception
        if (expectedException != null) {
            thrown.expect(expectedException);
            thrown.expectMessage(expectedExceptionMsg);
        }

        assertEquals(expectedPoolSize, AgentPool.computePoolSize(maxMemoryInBytes, propertyOverride), "Pool Size Calculation Error");
    }
}
