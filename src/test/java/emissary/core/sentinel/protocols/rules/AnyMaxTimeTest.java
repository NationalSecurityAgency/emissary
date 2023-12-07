package emissary.core.sentinel.protocols.rules;

import emissary.core.sentinel.protocols.Protocol;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnyMaxTimeTest extends UnitTest {

    Protocol.PlaceAgentStats placeAgentStats;

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        placeAgentStats = new Protocol.PlaceAgentStats("TestPlace");
        for (int i = 1; i < 6; ++i) {
            placeAgentStats.update(i);
        }
    }

    @Test
    void constructorBlankPlace() {
        assertThrows(IllegalArgumentException.class, () -> new AnyMaxTime("", "3L", "1.0"));
    }

    @Test
    void constructorInvalidTimeLimit() {
        assertThrows(NumberFormatException.class, () -> new AnyMaxTime("TestPlace", "L", "1.0"));
        assertThrows(IllegalArgumentException.class, () -> new AnyMaxTime("TestPlace", "0L", "1.0"));
    }

    @Test
    void constructorInvalidThreshold() {
        assertThrows(NumberFormatException.class, () -> new AnyMaxTime("TestPlace", "3L", "."));
        assertThrows(IllegalArgumentException.class, () -> new AnyMaxTime("TestPlace", "3L", "0.0"));
    }

    @Test
    void overTimeLimit() {
        Rule rule = new AnyMaxTime("TestPlace", 5, 0.75);
        assertTrue(rule.overTimeLimit(placeAgentStats));
    }

    @Test
    void notOverTimeLimit() {
        Rule rule = new AnyMaxTime("TestPlace", 6, 0.75);
        assertFalse(rule.overTimeLimit(placeAgentStats));
    }
}
