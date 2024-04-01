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
}
