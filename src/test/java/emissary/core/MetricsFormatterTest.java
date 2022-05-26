package emissary.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import com.codahale.metrics.Timer;
import emissary.test.core.UnitTest;
import org.junit.jupiter.api.Test;

class MetricsFormatterTest extends UnitTest {
    @Test
    void testFormatter() {
        MetricsFormatter m = MetricsFormatter.builder().withRateUnit(TimeUnit.SECONDS).withDurationUnit(TimeUnit.SECONDS).build();
        String s = m.formatTimer("FOO", new Timer());
        assertTrue(s.contains("STAT"), "Formatted string from formatter - " + s);
    }
}
