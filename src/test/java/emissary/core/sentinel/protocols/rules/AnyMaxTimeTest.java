package emissary.core.sentinel.protocols.rules;

import emissary.core.sentinel.protocols.AgentProtocol;
import emissary.pool.AgentPool;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnyMaxTimeTest extends UnitTest {

    Collection<AgentProtocol.PlaceAgentStats> placeAgentStats;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        AgentProtocol.PlaceAgentStats stats = new AgentProtocol.PlaceAgentStats("TestPlace");
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
        AgentRule rule = new AnyMaxTime("rule1", "TestPlace", 5, 0.75);
        assertTrue(rule.overTimeLimit(placeAgentStats));
    }

    @Test
    void notOverTimeLimit() {
        AgentRule rule = new AnyMaxTime("rule1", "TestPlace", 6, 0.75);
        assertFalse(rule.overTimeLimit(placeAgentStats));
    }

    @Nested
    class ConditionTest extends UnitTest {

        final String toUpperLowerPattern = "To(?:Lower|Upper)Place";
        final String toLowerPlace = "ToLowerPlace";
        final String toUpperPlace = "ToUpperPlace";
        final int defaultPoolSize = 5;
        final int defaultTimeLimit = 5;

        AgentPool pool;
        List<AgentProtocol.PlaceAgentStats> stats;

        @Override
        @BeforeEach
        public void setUp() throws Exception {
            super.setUp();
            pool = mock(AgentPool.class);
            stats = stats();
        }

        @Test
        void condition1() {
            assertTrue(testRule(toUpperLowerPattern, defaultTimeLimit, 1.0, defaultPoolSize));
        }

        @Test
        void condition2() {
            assertFalse(testRule(toUpperLowerPattern, defaultTimeLimit, 1.0, defaultPoolSize + 1));
        }

        @Test
        void condition3() {
            assertFalse(testRule(toUpperLowerPattern, defaultTimeLimit + 1, 1.0, defaultPoolSize));
        }

        @Test
        void condition4() {
            assertFalse(testRule(toUpperLowerPattern, defaultTimeLimit + 1, 1.0, defaultPoolSize + 1));
        }

        @Test
        void condition5() {
            assertTrue(testRule(toLowerPlace, defaultTimeLimit - 1, 0.5, defaultPoolSize));
        }

        @Test
        void condition6() {
            assertFalse(testRule(toLowerPlace, defaultTimeLimit, 0.5, defaultPoolSize));
        }

        @Test
        void condition7() {
            assertFalse(testRule(toLowerPlace, defaultTimeLimit - 1, 0.75, defaultPoolSize));
        }

        @Test
        void condition8() {
            assertTrue(testRule(toUpperPlace, defaultTimeLimit, 0.2, defaultPoolSize));
        }

        @Test
        void condition9() {
            assertFalse(testRule(toUpperPlace, defaultTimeLimit, 0.5, defaultPoolSize));
        }

        boolean testRule(String matcher, int time, double threshold, int poolSize) {
            Rule rule = new AnyMaxTime("rule", matcher, time, threshold);
            try (MockedStatic<AgentPool> agentPool = Mockito.mockStatic(AgentPool.class)) {
                agentPool.when(AgentPool::lookup).thenReturn(pool);
                when(pool.getCurrentPoolSize()).thenReturn(poolSize);
                return rule.condition(stats);
            }
        }

        List<AgentProtocol.PlaceAgentStats> stats() {
            AgentProtocol.PlaceAgentStats lowerStats = new AgentProtocol.PlaceAgentStats("ToLowerPlace");
            lowerStats.update(defaultTimeLimit - 4); // MobileAgent-01
            lowerStats.update(defaultTimeLimit - 2); // MobileAgent-02
            lowerStats.update(defaultTimeLimit - 1); // MobileAgent-03

            AgentProtocol.PlaceAgentStats upperStats = new AgentProtocol.PlaceAgentStats("ToUpperPlace");
            upperStats.update(defaultTimeLimit - 3); // MobileAgent-04
            upperStats.update(defaultTimeLimit); // MobileAgent-05

            return List.of(lowerStats, upperStats);
        }
    }

}
