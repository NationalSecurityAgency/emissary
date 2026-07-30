package emissary.core;

import emissary.core.DiffCheckConfiguration.DiffCheckBuilder;
import emissary.core.DiffCheckConfiguration.DiffCheckOptions;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiffCheckConfigurationTest extends UnitTest {
    @Test
    void testNonParameter() {
        final DiffCheckBuilder diffCheckBuilder = DiffCheckConfiguration.configure();
        final DiffCheckConfiguration emptyConfiguration = diffCheckBuilder.build();

        assertEquals(0, emptyConfiguration.getEnabled().size(), "Configuration should be empty!");

        diffCheckBuilder.enableData();
        diffCheckBuilder.enableInternalId();
        diffCheckBuilder.enableTimestamp();
        diffCheckBuilder.enableTransformHistory();

        final DiffCheckConfiguration nonParameterConfiguration = diffCheckBuilder.build();

        assertTrue(nonParameterConfiguration.checkData());
        assertTrue(nonParameterConfiguration.checkInternalId());
        assertTrue(nonParameterConfiguration.checkTimestamp());
        assertTrue(nonParameterConfiguration.checkTransformHistory());
        assertFalse(nonParameterConfiguration.performDetailedParameterDiff());
        assertFalse(nonParameterConfiguration.performKeyValueParameterDiff());

        diffCheckBuilder.disableData();
        diffCheckBuilder.disableInternalId();
        diffCheckBuilder.disableTimestamp();
        diffCheckBuilder.disableTransformHistory();

        assertEquals(0, diffCheckBuilder.build().getEnabled().size(), "Configuration should be empty!");
    }

    @Test
    void testKeyValueDetailed() {
        final DiffCheckBuilder diffCheckBuilder = DiffCheckConfiguration.configure();

        diffCheckBuilder.enableDetailedParameterDiff();

        final DiffCheckConfiguration detailedParameterConfiguration = diffCheckBuilder.build();

        assertTrue(detailedParameterConfiguration.performDetailedParameterDiff());
        assertFalse(detailedParameterConfiguration.performKeyValueParameterDiff());

        diffCheckBuilder.enableKeyValueParameterDiff();

        final DiffCheckConfiguration keyValueParameterConfiguration = diffCheckBuilder.build();

        assertFalse(keyValueParameterConfiguration.performDetailedParameterDiff());
        assertTrue(keyValueParameterConfiguration.performKeyValueParameterDiff());

        diffCheckBuilder.disableKeyValueParameterDiff();

        assertEquals(0, diffCheckBuilder.build().getEnabled().size(), "Configuration should be empty!");

        diffCheckBuilder.enableKeyValueParameterDiff();

        final DiffCheckConfiguration keyValueParameterConfiguration2 = diffCheckBuilder.build();

        assertFalse(keyValueParameterConfiguration2.performDetailedParameterDiff());
        assertTrue(keyValueParameterConfiguration2.performKeyValueParameterDiff());

        diffCheckBuilder.enableDetailedParameterDiff();

        final DiffCheckConfiguration detailedParameterConfiguration2 = diffCheckBuilder.build();

        assertTrue(detailedParameterConfiguration2.performDetailedParameterDiff());
        assertFalse(detailedParameterConfiguration2.performKeyValueParameterDiff());

        diffCheckBuilder.disableDetailedParameterDiff();

        assertEquals(0, diffCheckBuilder.build().getEnabled().size(), "Configuration should be empty!");
    }

    @Test
    void checkReset() {
        final DiffCheckBuilder diffCheckBuilder = DiffCheckConfiguration.configure();

        diffCheckBuilder.enableData();
        diffCheckBuilder.enableInternalId();
        diffCheckBuilder.enableTimestamp();
        diffCheckBuilder.enableTransformHistory();
        diffCheckBuilder.enableDetailedParameterDiff();
        diffCheckBuilder.reset();

        assertEquals(0, diffCheckBuilder.build().getEnabled().size(), "Configuration should be empty!");
    }

    @Test
    void checkExplicit() {
        final DiffCheckBuilder diffCheckBuilder = DiffCheckConfiguration.configure();
        final DiffCheckConfiguration explicitDetailedConfiguration = diffCheckBuilder.explicit(
                DiffCheckOptions.DATA,
                DiffCheckOptions.DETAILED_PARAMETER_DIFF,
                DiffCheckOptions.INTERNAL_ID,
                DiffCheckOptions.TIMESTAMP,
                DiffCheckOptions.TRANSFORM_HISTORY);

        assertTrue(explicitDetailedConfiguration.checkData());
        assertTrue(explicitDetailedConfiguration.checkInternalId());
        assertTrue(explicitDetailedConfiguration.checkTimestamp());
        assertTrue(explicitDetailedConfiguration.checkTransformHistory());
        assertTrue(explicitDetailedConfiguration.performDetailedParameterDiff());
        assertFalse(explicitDetailedConfiguration.performKeyValueParameterDiff());

        final DiffCheckConfiguration explicitKeyValueConfiguration = diffCheckBuilder.explicit(
                DiffCheckOptions.DATA,
                DiffCheckOptions.KEY_VALUE_PARAMETER_DIFF,
                DiffCheckOptions.INTERNAL_ID,
                DiffCheckOptions.TIMESTAMP,
                DiffCheckOptions.TRANSFORM_HISTORY);

        assertTrue(explicitKeyValueConfiguration.checkData());
        assertTrue(explicitKeyValueConfiguration.checkInternalId());
        assertTrue(explicitKeyValueConfiguration.checkTimestamp());
        assertTrue(explicitKeyValueConfiguration.checkTransformHistory());
        assertFalse(explicitKeyValueConfiguration.performDetailedParameterDiff());
        assertTrue(explicitKeyValueConfiguration.performKeyValueParameterDiff());

        assertThrows(IllegalArgumentException.class, () -> diffCheckBuilder.explicit(
                DiffCheckOptions.DETAILED_PARAMETER_DIFF,
                DiffCheckOptions.KEY_VALUE_PARAMETER_DIFF));
    }

    @Test
    void testFromConfiguration() {
        final DiffCheckBuilder originalBuilder = DiffCheckConfiguration.configure();
        originalBuilder.enableData();
        originalBuilder.enableTimestamp();
        originalBuilder.enableDetailedParameterDiff();
        final DiffCheckConfiguration originalConfig = originalBuilder.build();

        final DiffCheckBuilder copiedBuilder = DiffCheckConfiguration.from(originalConfig);
        final DiffCheckConfiguration copiedConfig = copiedBuilder.build();

        assertTrue(copiedConfig.checkData());
        assertTrue(copiedConfig.checkTimestamp());
        assertFalse(copiedConfig.checkInternalId());
        assertFalse(copiedConfig.checkTransformHistory());
        assertTrue(copiedConfig.performDetailedParameterDiff());
        assertFalse(copiedConfig.performKeyValueParameterDiff());
    }

    @Test
    void testStrictMode() {
        final DiffCheckBuilder builder = DiffCheckConfiguration.configure();

        // Default is strict
        DiffCheckConfiguration config = builder.build();
        assertTrue(config.isStrict());

        // Disable strict mode
        builder.setStrict(false);
        config = builder.build();
        assertFalse(config.isStrict());

        // Re-enable strict mode
        builder.setStrict(true);
        config = builder.build();
        assertTrue(config.isStrict());
    }

    @Test
    void testOnlyCheckDataFactory() {
        final DiffCheckConfiguration config = DiffCheckConfiguration.onlyCheckData();

        assertTrue(config.checkData());
        assertFalse(config.checkTimestamp());
        assertFalse(config.checkInternalId());
        assertFalse(config.checkTransformHistory());
        assertFalse(config.performDetailedParameterDiff());
        assertFalse(config.performKeyValueParameterDiff());
        assertEquals(1, config.getEnabled().size());
    }

    @Test
    void testMultipleBuilderModifications() {
        final DiffCheckBuilder builder = DiffCheckConfiguration.configure();

        builder.enableData();
        builder.enableTimestamp();
        builder.enableInternalId();
        builder.enableTransformHistory();

        final DiffCheckConfiguration config1 = builder.build();
        assertTrue(config1.checkData());
        assertTrue(config1.checkTimestamp());
        assertTrue(config1.checkInternalId());
        assertTrue(config1.checkTransformHistory());
        assertEquals(4, config1.getEnabled().size());

        // Disable some options
        builder.disableTimestamp();
        builder.disableInternalId();

        final DiffCheckConfiguration config2 = builder.build();
        assertTrue(config2.checkData());
        assertFalse(config2.checkTimestamp());
        assertFalse(config2.checkInternalId());
        assertTrue(config2.checkTransformHistory());
        assertEquals(2, config2.getEnabled().size());
    }

    @Test
    void testMutualExclusivityOfParameterDiffs() {
        final DiffCheckBuilder builder = DiffCheckConfiguration.configure();

        builder.enableDetailedParameterDiff();
        DiffCheckConfiguration config = builder.build();
        assertTrue(config.performDetailedParameterDiff());
        assertFalse(config.performKeyValueParameterDiff());

        // Enabling key-value should disable detailed
        builder.enableKeyValueParameterDiff();
        config = builder.build();
        assertFalse(config.performDetailedParameterDiff());
        assertTrue(config.performKeyValueParameterDiff());

        // Enabling detailed should disable key-value
        builder.enableDetailedParameterDiff();
        config = builder.build();
        assertTrue(config.performDetailedParameterDiff());
        assertFalse(config.performKeyValueParameterDiff());
    }

    @Test
    void testCumulativeEnablingAllOptions() {
        final DiffCheckBuilder builder = DiffCheckConfiguration.configure();

        builder.enableData();
        builder.enableTimestamp();
        builder.enableInternalId();
        builder.enableTransformHistory();
        builder.enableDetailedParameterDiff();

        final DiffCheckConfiguration config = builder.build();

        assertTrue(config.checkData());
        assertTrue(config.checkTimestamp());
        assertTrue(config.checkInternalId());
        assertTrue(config.checkTransformHistory());
        assertTrue(config.performDetailedParameterDiff());
        assertFalse(config.performKeyValueParameterDiff());
        assertEquals(5, config.getEnabled().size());
    }
}
