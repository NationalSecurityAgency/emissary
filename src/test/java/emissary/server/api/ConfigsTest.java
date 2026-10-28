package emissary.server.api;

import emissary.config.ConfigEntry;
import emissary.config.Configurator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigsTest {

    @Test
    void validate() {
        assertDoesNotThrow(() -> Configs.validate("some.random.config.PlaceConfig"));
        assertThrows(IllegalArgumentException.class, () -> Configs.validate("/dev/some.random.config.PlaceConfig"));
        assertThrows(IllegalArgumentException.class, () -> Configs.validate("https://dev/some.random.config.PlaceConfig"));
        assertThrows(IllegalArgumentException.class, () -> Configs.validate("..some.random.config.PlaceConfig"));
        assertThrows(IllegalArgumentException.class, () -> Configs.validate("."));
        assertThrows(IllegalArgumentException.class, () -> Configs.validate("%2e%2e%2fsome.random.config.PlaceConfig"));
        assertThrows(IllegalArgumentException.class, () -> Configs.validate("%252e%252e%252fsome.random.config.PlaceConfig"));
        assertThrows(IllegalArgumentException.class, () -> Configs.validate("U+002Fsome.random.config.PlaceConfigU+002F."));
    }

    @Test
    void redactsSensitiveValuesUnlessRequested() {
        final Configurator config = mock(Configurator.class);
        when(config.getEntries()).thenReturn(List.of(
                new ConfigEntry("DB_PASSWORD", "hunter2"),
                new ConfigEntry("NORMAL", "value")));

        assertEquals("***REDACTED***", valueOf(Configs.normalizeEntries(config, false), "DB_PASSWORD"));
        assertEquals("hunter2", valueOf(Configs.normalizeEntries(config, true), "DB_PASSWORD"));
        assertEquals("value", valueOf(Configs.normalizeEntries(config, false), "NORMAL"));
    }

    private static String valueOf(final List<ConfigEntry> entries, final String key) {
        return entries.stream().filter(entry -> key.equals(entry.getKey())).findFirst().orElseThrow().getValue();
    }
}
