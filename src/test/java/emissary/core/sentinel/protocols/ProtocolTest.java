package emissary.core.sentinel.protocols;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.core.IMobileAgent;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.core.sentinel.Sentinel;
import emissary.core.sentinel.protocols.actions.Action;
import emissary.core.sentinel.protocols.actions.Notify;
import emissary.core.sentinel.protocols.rules.AllMaxTime;
import emissary.core.sentinel.protocols.rules.AnyMaxTime;
import emissary.core.sentinel.protocols.rules.Rule;
import emissary.core.sentinel.protocols.trackers.AgentTracker;
import emissary.directory.DirectoryEntry;
import emissary.directory.DirectoryPlace;
import emissary.pool.AgentPool;
import emissary.test.core.junit5.UnitTest;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProtocolTest extends UnitTest {

    AgentProtocol protocol;
    Rule rule1;
    Action action;
    Map<String, AgentTracker> trackers;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        rule1 = mock(Rule.class);
        action = mock(Action.class);

        protocol = new AgentProtocol();
        protocol.action = action;
        protocol.rules.put("RULE1", rule1);
        protocol.enabled = true;

        trackers = new HashMap<>();
        for (int i = 1; i <= 5; ++i) {
            String agent = "Agent-0" + i;
            AgentTracker tracker = new AgentTracker(agent);
            tracker.setAgentId(agent);
            tracker.setDirectoryEntryKey("http://host.domain.com:8001/thePlace");
            tracker.incrementTimer(0);
            tracker.incrementTimer(5 + i);
            trackers.put(agent, tracker);
        }
    }

    @Test
    void isEnabled() {
        assertTrue(protocol.isEnabled());
    }

    @Test
    void run() {
        when(rule1.condition(any())).thenReturn(true);
        protocol.runRules(trackers);
        verify(action, times(1)).trigger(any());
    }

    @Test
    void init() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("ENABLED", "true");
        config.addEntry("RULE_ID", "LONG_RUNNING");
        config.addEntry("LONG_RUNNING_PLACE_MATCHER", ".*");
        config.addEntry("LONG_RUNNING_PLACE_THRESHOLD", "1.0");
        config.addEntry("LONG_RUNNING_TIME_LIMIT_MINUTES", "60");

        DirectoryPlace dir = mock(DirectoryPlace.class);
        when(dir.getEntries()).thenReturn(
                List.of(new DirectoryEntry("UNKNOWN.FOOPLACE.ID.http://host.domain.com:8001/thePlace", "This is a place", 10, 90)));
        try (MockedStatic<Namespace> namespace = Mockito.mockStatic(Namespace.class)) {
            namespace.when(() -> Namespace.lookup(DirectoryPlace.class)).thenReturn(Set.of(dir));
            protocol.configure(config);
            assertEquals(Notify.class, protocol.action.getClass());
            assertEquals(AllMaxTime.class, protocol.rules.get("LONG_RUNNING").getClass());
        }
    }

    @Test
    void getPlaceKey() {
        assertEquals("thePlace", trackers.get("Agent-01").getPlaceName());
    }

    @Test
    void validate() throws NamespaceException {
        DirectoryPlace dir = mock(DirectoryPlace.class);
        when(dir.getEntries()).thenReturn(
                List.of(new DirectoryEntry("UNKNOWN.FOOPLACE.ID.http://host.domain.com:8001/thePlace", "This is a place", 10, 90)));
        try (MockedStatic<Namespace> namespace = Mockito.mockStatic(Namespace.class)) {
            namespace.when(() -> Namespace.lookup(DirectoryPlace.class)).thenReturn(Set.of(dir));
            assertEquals("thePlace", protocol.validate("thePlace"));
            assertThrows(IllegalStateException.class, () -> protocol.validate("noPlace"));
        }
    }

    @Test
    void protocolValidJson() {
        AgentProtocol protocol = new AgentProtocol();
        protocol.action = new Notify();
        protocol.rules.put("TEST_RULE1", new AllMaxTime("rule1", "thisPlace", 20, 0.75));
        protocol.rules.put("TEST_RULE2", new AnyMaxTime("rule2", "thatPlace", 10, 0.5));

        ObjectMapper mapper = new ObjectMapper();
        try (JsonParser parser = mapper.getFactory().createParser(protocol.toString())) {
            mapper.readTree(parser);
        } catch (IOException e) {
            fail(e);
        }
    }

    @Nested
    class RunTest extends UnitTest {

        final String toUpperLowerPattern = "To(?:Lower|Upper)Place";
        final String toLowerPlace = "ToLowerPlace";
        final String toUpperPlace = "ToUpperPlace";
        final int defaultPoolSize = 5;

        Action action;
        AgentPool pool;
        Map<String, AgentTracker> trackers;

        @Override
        @BeforeEach
        public void setUp() throws Exception {
            super.setUp();
            action = mock(Action.class);
            pool = mock(AgentPool.class);
            trackers = trackers();
        }

        @Test
        void protocol1() {
            AgentProtocol protocol = new AgentProtocol();
            protocol.action = action;
            protocol.rules.put("TEST_RULE1", new AllMaxTime("rule1", toUpperLowerPattern, 5, 1.0));
            protocol.rules.put("TEST_RULE2", new AnyMaxTime("rule2", toUpperLowerPattern, 30, 0.2));

            testProtocol(protocol, defaultPoolSize, 1);
        }

        @Test
        void protocol2() {
            AgentProtocol protocol = new AgentProtocol();
            protocol.action = action;
            protocol.rules.put("TEST_RULE1", new AllMaxTime("rule1", toUpperLowerPattern, 5, 1.0));
            protocol.rules.put("TEST_RULE2", new AnyMaxTime("rule2", toUpperLowerPattern, 40, 0.2));

            testProtocol(protocol, defaultPoolSize, 0);
        }

        @Test
        void protocol3() {
            AgentProtocol protocol = new AgentProtocol();
            protocol.action = action;
            protocol.rules.put("TEST_RULE", new AnyMaxTime("LongRunning", toUpperLowerPattern, 30, 0.01));

            testProtocol(protocol, defaultPoolSize, 1);
        }

        @Test
        void protocol4() {
            AgentProtocol protocol = new AgentProtocol();
            protocol.action = action;
            protocol.rules.put("TEST_RULE", new AnyMaxTime("LongRunning", toLowerPlace, 30, 0.01));

            testProtocol(protocol, defaultPoolSize, 0);
        }

        @Test
        void protocol5() {
            AgentProtocol protocol = new AgentProtocol();
            protocol.action = action;
            protocol.rules.put("TEST_RULE", new AnyMaxTime("LongRunning", toUpperPlace, 30, 0.01));

            testProtocol(protocol, defaultPoolSize, 1);
        }

        @Test
        void protocol6() {
            AgentProtocol protocol = new AgentProtocol();
            protocol.action = action;
            protocol.rules.put("TEST_RULE1", new AllMaxTime("rule1", toUpperLowerPattern, 5, 1.0));
            protocol.rules.put("TEST_RULE2", new AnyMaxTime("rule2", toUpperLowerPattern, 30, 0.2));

            testProtocol(protocol, defaultPoolSize + 1, 0);
        }

        void testProtocol(AgentProtocol protocol, int poolSize, int expected) {
            try (MockedStatic<AgentPool> agentPool = Mockito.mockStatic(AgentPool.class)) {
                agentPool.when(AgentPool::lookup).thenReturn(pool);
                when(pool.getCurrentPoolSize()).thenReturn(poolSize);
                protocol.runRules(trackers);
                verify(action, times(expected)).trigger(trackers.entrySet()
                        .stream()
                        .filter(e -> e.getValue().isFlagged())
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue)));
            }
        }

        Map<String, AgentTracker> trackers() {
            AgentTracker agent1 = new AgentTracker("MobileAgent-01");
            agent1.setAgentId("Agent-1234-testing1.txt");
            agent1.setDirectoryEntryKey("http://host.domain.com:8001/ToLowerPlace");
            agent1.incrementTimer(1); // init
            agent1.incrementTimer(5);

            AgentTracker agent2 = new AgentTracker("MobileAgent-02");
            agent2.setAgentId("Agent-2345-testing2.txt");
            agent2.setDirectoryEntryKey("http://host.domain.com:8001/ToLowerPlace");
            agent2.incrementTimer(1); // init
            agent2.incrementTimer(15);

            AgentTracker agent3 = new AgentTracker("MobileAgent-03");
            agent3.setAgentId("Agent-3456-testing3.txt");
            agent3.setDirectoryEntryKey("http://host.domain.com:8001/ToLowerPlace");
            agent3.incrementTimer(1); // init
            agent3.incrementTimer(9);

            AgentTracker agent4 = new AgentTracker("MobileAgent-04");
            agent4.setAgentId("Agent-4567-testing4.txt");
            agent4.setDirectoryEntryKey("http://host.domain.com:8001/ToUpperPlace");
            agent4.incrementTimer(1); // init
            agent4.incrementTimer(35);

            AgentTracker agent5 = new AgentTracker("MobileAgent-05");
            agent5.setAgentId("Agent-5678-testing5.txt");
            agent5.setDirectoryEntryKey("http://host.domain.com:8001/ToUpperPlace");
            agent5.incrementTimer(1); // init
            agent5.incrementTimer(7);

            return Map.of(
                    "MobileAgent-01", agent1,
                    "MobileAgent-02", agent2,
                    "MobileAgent-03", agent3,
                    "MobileAgent-04", agent4,
                    "MobileAgent-05", agent5);
        }
    }

    @Test
    void trackerSorting() {
        AgentTracker tracker1 = new AgentTracker("MobileAgent-01");
        AgentTracker tracker2 = new AgentTracker("MobileAgent-10");
        AgentTracker tracker3 = new AgentTracker("MobileAgent-20");

        protocol.trackers.put("MobileAgent-01", tracker1);
        protocol.trackers.put("MobileAgent-10", tracker2);
        protocol.trackers.put("MobileAgent-20", tracker3);

        List<AgentTracker> sorted = protocol.trackers.values().stream().sorted().collect(Collectors.toList());
        assertEquals("MobileAgent-01", sorted.get(0).getAgentName());
        assertEquals("MobileAgent-10", sorted.get(1).getAgentName());
        assertEquals("MobileAgent-20", sorted.get(2).getAgentName());
    }

    @Test
    void watch() throws Exception {
        String agentKey = "MobileAgent-01";
        String placename = "thePlace";
        String shortname = "testing.txt";

        IMobileAgent hdma = mock(IMobileAgent.class);
        when(hdma.getName()).thenReturn(agentKey);
        when(hdma.isInUse()).thenReturn(true);
        when(hdma.agentId()).thenReturn("Agent-1234-" + shortname);
        when(hdma.getShortName()).thenReturn(shortname);
        when(hdma.getLastPlaceProcessed()).thenReturn("http://host.domain.com:8001/" + placename);

        Sentinel sm = mock(Sentinel.class);
        when(sm.getPollingInterval()).thenReturn(5L);

        AgentTracker tracker;
        try (MockedStatic<Namespace> namespace = Mockito.mockStatic(Namespace.class);
                MockedStatic<Sentinel> sentinel = Mockito.mockStatic(Sentinel.class)) {
            namespace.when(Namespace::keySet).thenReturn(Set.of(agentKey));
            namespace.when(() -> Namespace.lookup(agentKey)).thenReturn(hdma);
            sentinel.when(() -> Sentinel.lookup()).thenReturn(sm);
            protocol.run();
            tracker = protocol.trackers.get(agentKey);
        }

        assertNotNull(tracker);
        assertEquals(0, tracker.getTimer());
        assertEquals(placename, tracker.getPlaceName());
        assertEquals(shortname, tracker.getShortName());
    }
}
