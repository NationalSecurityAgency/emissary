package emissary.core;

import emissary.test.core.junit5.UnitTest;

import com.codahale.metrics.Timer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsFormatterTest extends UnitTest {
    @Test
    void testFormatter() {
        MetricsFormatter m = MetricsFormatter.builder().withRateUnit(TimeUnit.SECONDS).withDurationUnit(TimeUnit.SECONDS).build();
        String s = m.formatTimer("FOO", new Timer());
        assertTrue(s.contains("STAT"), "Formatted string from formatter - " + s);
    }
}
