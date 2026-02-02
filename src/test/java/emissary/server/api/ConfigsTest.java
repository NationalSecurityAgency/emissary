package emissary.server.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
