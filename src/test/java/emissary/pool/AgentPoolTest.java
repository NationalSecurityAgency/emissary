package emissary.pool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import emissary.test.core.junit5.UnitTest;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class AgentPoolTest extends UnitTest {

    public static Stream<Arguments> poolSizeVales() {
        return Stream.of(
                Arguments.of(60000L, null, 15, null, null),
                Arguments.of(1900000000L, null, 20, null, null),
                Arguments.of(2200000000L, null, 25, null, null),
                Arguments.of(9900000000L, null, 50, null, null),
                Arguments.of(0L, 10, 10, null, null),
                Arguments.of(60000L, -10, 15, null, null),
                Arguments.of(0L, -10, 10, IllegalArgumentException.class, "Must be greater then zero."),
                Arguments.of(0L, 0, 10, IllegalArgumentException.class, "Must be greater then zero."),
                Arguments.of(-100L, null, 10, IllegalArgumentException.class, "Must be greater then zero."));
    }

    @ParameterizedTest
    @MethodSource("poolSizeVales")
    void testComputePoolSize(long maxMemoryInBytes,
            Integer propertyOverride,
            int expectedPoolSize,
            @Nullable Class<? extends Exception> expectedException,
            String expectedExceptionMsg) throws IllegalArgumentException {
        // setup expected exception
        if (expectedException != null) {
            Exception e = assertThrows(expectedException, () -> AgentPool.computePoolSize(maxMemoryInBytes, propertyOverride));
            assertEquals(expectedExceptionMsg, e.getMessage());
        } else {
            assertEquals(expectedPoolSize, AgentPool.computePoolSize(maxMemoryInBytes, propertyOverride), "Pool Size Calculation Error");
        }
    }
}
