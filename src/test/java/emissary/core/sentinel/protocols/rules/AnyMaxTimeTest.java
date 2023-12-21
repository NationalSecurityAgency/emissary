package emissary.core.sentinel.protocols.rules;

import emissary.core.sentinel.protocols.Protocol;
import emissary.pool.AgentPool;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnyMaxTimeTest extends UnitTest {

    Collection<Protocol.PlaceAgentStats> placeAgentStats;

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        Protocol.PlaceAgentStats stats = new Protocol.PlaceAgentStats("TestPlace");
        placeAgentStats = List.of(stats);
        for (int i = 1; i < 6; ++i) {
            stats.update(i);
        }
    }

    @Test
    void constructorBlankPlace() {
        assertThrows(IllegalArgumentException.class, () -> new AnyMaxTime("rule1", "", "3L", "1.0"));
    }

    @Test
    void constructorInvalidTimeLimit() {
        assertThrows(NumberFormatException.class, () -> new AnyMaxTime("rule1", "TestPlace", "L", "1.0"));
        assertThrows(IllegalArgumentException.class, () -> new AnyMaxTime("rule1", "TestPlace", "0L", "1.0"));
    }

    @Test
    void constructorInvalidThreshold() {
        assertThrows(NumberFormatException.class, () -> new AnyMaxTime("rule1", "TestPlace", "3L", "."));
        assertThrows(IllegalArgumentException.class, () -> new AnyMaxTime("rule1", "TestPlace", "3L", "0.0"));
    }

    @Test
    void overTimeLimit() {
        Rule rule = new AnyMaxTime("rule1", "TestPlace", 5, 0.75);
        assertTrue(rule.overTimeLimit(placeAgentStats));
    }

    @Test
    void notOverTimeLimit() {
        Rule rule = new AnyMaxTime("rule1", "TestPlace", 6, 0.75);
        assertFalse(rule.overTimeLimit(placeAgentStats));
    }

    @Nested
    class ConditionTest extends UnitTest {

        final String TO_UPPER_LOWER_PATTERN = "To(?:Lower|Upper)Place";
        final String TO_LOWER_PLACE = "ToLowerPlace";
        final String TO_UPPER_PLACE = "ToUpperPlace";
        final int DEFAULT_POOL_SIZE = 5;
        final int DEFAULT_TIME_LIMIT = 5;

        AgentPool pool;

        List<Protocol.PlaceAgentStats> stats;

        @BeforeEach
        public void setUp() throws Exception {
            super.setUp();
            pool = mock(AgentPool.class);
            stats = stats();
        }

        @Test
        void condition1() {
            testRule(new AnyMaxTime("rule", TO_UPPER_LOWER_PATTERN, DEFAULT_TIME_LIMIT, 1.0), DEFAULT_POOL_SIZE, true);
        }

        @Test
        void condition2() {
            testRule(new AnyMaxTime("rule1", TO_UPPER_LOWER_PATTERN, DEFAULT_TIME_LIMIT, 1.0), DEFAULT_POOL_SIZE + 1, false);
        }

        @Test
        void condition3() {
            testRule(new AnyMaxTime("rule", TO_UPPER_LOWER_PATTERN, DEFAULT_TIME_LIMIT + 1, 1.0), DEFAULT_POOL_SIZE, false);
        }

        @Test
        void condition4() {
            testRule(new AnyMaxTime("rule", TO_UPPER_LOWER_PATTERN, DEFAULT_TIME_LIMIT + 1, 1.0), DEFAULT_POOL_SIZE + 1, false);
        }

        @Test
        void condition5() {
            testRule(new AnyMaxTime("rule", TO_LOWER_PLACE, DEFAULT_TIME_LIMIT - 1, 0.5), DEFAULT_POOL_SIZE, true);
        }

        @Test
        void condition6() {
            testRule(new AnyMaxTime("rule", TO_LOWER_PLACE, DEFAULT_TIME_LIMIT, 0.5), DEFAULT_POOL_SIZE, false);
        }

        @Test
        void condition7() {
            testRule(new AnyMaxTime("rule", TO_LOWER_PLACE, DEFAULT_TIME_LIMIT - 1, 0.75), DEFAULT_POOL_SIZE, false);
        }

        @Test
        void condition8() {
            testRule(new AnyMaxTime("rule", TO_UPPER_PLACE, DEFAULT_TIME_LIMIT, 0.2), DEFAULT_POOL_SIZE, true);
        }

        @Test
        void condition9() {
            testRule(new AnyMaxTime("rule", TO_UPPER_PLACE, DEFAULT_TIME_LIMIT, 0.5), DEFAULT_POOL_SIZE, false);
        }

        void testRule(Rule rule, int poolSize, boolean expected) {
            try (MockedStatic<AgentPool> agentPool = Mockito.mockStatic(AgentPool.class)) {
                agentPool.when(AgentPool::lookup).thenReturn(pool);
                when(pool.getCurrentPoolSize()).thenReturn(poolSize);
                assertEquals(expected, rule.condition(stats));
            }
        }

        List<Protocol.PlaceAgentStats> stats() {
            Protocol.PlaceAgentStats lowerStats = new Protocol.PlaceAgentStats("ToLowerPlace");
            lowerStats.update(DEFAULT_TIME_LIMIT - 4); // MobileAgent-01
            lowerStats.update(DEFAULT_TIME_LIMIT - 2); // MobileAgent-02
            lowerStats.update(DEFAULT_TIME_LIMIT - 1); // MobileAgent-03

            Protocol.PlaceAgentStats upperStats = new Protocol.PlaceAgentStats("ToUpperPlace");
            upperStats.update(DEFAULT_TIME_LIMIT - 3); // MobileAgent-04
            upperStats.update(DEFAULT_TIME_LIMIT); // MobileAgent-05

            return List.of(lowerStats, upperStats);
        }
    }

}
