package emissary.core;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import com.codahale.metrics.Timer;
import emissary.test.core.UnitTest;
import org.junit.Test;

public class MetricsFormatterTest extends UnitTest {
    @Test
    public void testFormatter() {
        MetricsFormatter m = MetricsFormatter.builder().withRateUnit(TimeUnit.SECONDS).withDurationUnit(TimeUnit.SECONDS).build();
        String s = m.formatTimer("FOO", new Timer());
        assertTrue("Formatted string from formatter - " + s, s.indexOf("STAT") > -1);
    }
}
