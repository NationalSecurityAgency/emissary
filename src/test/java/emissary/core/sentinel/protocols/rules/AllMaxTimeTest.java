package emissary.core.sentinel.protocols.rules;

import emissary.core.sentinel.protocols.Protocol;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllMaxTimeTest extends UnitTest {

    Protocol.PlaceAgentStats placeAgentStats;

    @BeforeEach
    public void setUp() {
        placeAgentStats = new Protocol.PlaceAgentStats("TestPlace");
        for (int i = 1; i < 6; ++i) {
            placeAgentStats.update(i);
        }
    }

    @Test
    void constructorBlankPlace() {
        assertThrows(IllegalArgumentException.class, () -> new AllMaxTime("", "3L", "1.0"));
    }

    @Test
    void constructorInvalidTimeLimit() {
        assertThrows(NumberFormatException.class, () -> new AllMaxTime("TestPlace", "L", "1.0"));
        assertThrows(IllegalArgumentException.class, () -> new AllMaxTime("TestPlace", "0L", "1.0"));
    }

    @Test
    void constructorInvalidThreshold() {
        assertThrows(NumberFormatException.class, () -> new AllMaxTime("TestPlace", "3L", "."));
        assertThrows(IllegalArgumentException.class, () -> new AllMaxTime("TestPlace", "3L", "0.0"));
    }

    @Test
    void overTimeLimit() {
        Rule rule = new AllMaxTime("TestPlace", 1, 0.75);
        assertTrue(rule.overTimeLimit(placeAgentStats));
    }

    @Test
    void notOverTimeLimit() {
        Rule rule = new AllMaxTime("TestPlace", 2, 0.75);
        assertFalse(rule.overTimeLimit(placeAgentStats));
    }
}
