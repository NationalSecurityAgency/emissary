package emissary.core;

import emissary.config.ServiceConfigGuide;
import emissary.test.core.junit5.LogbackTester;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SafeUsageCheckerTest extends UnitTest {
    @Test
    void testDifferentConfigs() {
        assertTrue(SafeUsageChecker.ENABLED_FROM_CONFIGURATION, "Enabled from config file should be true");

        final ServiceConfigGuide scg = new ServiceConfigGuide();

        scg.addEntry(SafeUsageChecker.ENABLED_KEY, Boolean.toString(false));

        assertFalse(new SafeUsageChecker(scg).enabled, "Enabled should be false!");

        scg.removeAllEntries(SafeUsageChecker.ENABLED_KEY);
        scg.addEntry(SafeUsageChecker.ENABLED_KEY, Boolean.toString(true));

        assertTrue(new SafeUsageChecker(scg).enabled, "Enabled should be true!");
    }

    @Test
    void testDisabled() throws IOException {
        final ServiceConfigGuide scg = new ServiceConfigGuide();

        scg.addEntry(SafeUsageChecker.ENABLED_KEY, Boolean.toString(false));

        final SafeUsageChecker suc = new SafeUsageChecker(scg);
        final byte[] bytes0 = new byte[10];
        final byte[] bytes1 = new byte[10];

        suc.reset();
        suc.resetCacheThenRecordSnapshot(bytes0);
        suc.recordSnapshot(bytes1);

        Arrays.fill(bytes0, (byte) 10);
        Arrays.fill(bytes1, (byte) 20);

        try (LogbackTester logbackTester = new LogbackTester(SafeUsageChecker.class.getName())) {
            suc.checkForUnsafeDataChanges();
            logbackTester.checkLogList(Collections.emptyList());
        }
    }
}
