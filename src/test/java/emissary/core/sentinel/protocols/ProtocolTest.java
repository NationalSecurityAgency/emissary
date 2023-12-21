package emissary.core.sentinel.protocols;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.core.sentinel.Sentinel;
import emissary.core.sentinel.protocols.actions.Action;
import emissary.core.sentinel.protocols.actions.Notify;
import emissary.core.sentinel.protocols.rules.AllMaxTime;
import emissary.core.sentinel.protocols.rules.AnyMaxTime;
import emissary.core.sentinel.protocols.rules.Rule;
import emissary.directory.DirectoryEntry;
import emissary.directory.DirectoryPlace;
import emissary.pool.AgentPool;
import emissary.test.core.junit5.UnitTest;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProtocolTest extends UnitTest {

    Protocol protocol;
    Rule rule1 = mock(Rule.class);
    Action action = mock(Action.class);
    Map<String, Sentinel.Tracker> trackers;
    final String TO_UPPER_LOWER_PATTER = "To(?:Lower|Upper)Place";
    final String TO_LOWER_PLACE = "ToLowerPlace";
    final String TO_UPPER_PLACE = "ToUpperPlace";
    final int DEFAULT_POOL_SIZE = 5;

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        protocol = new Protocol();
        protocol.action = action;
        protocol.rules.put("RULE1", rule1);
        protocol.enabled = true;

        trackers = new HashMap<>();
        for (int i = 1; i <= 5; ++i) {
            String agent = "Agent-0" + i;
            Sentinel.Tracker tracker = new Sentinel.Tracker(agent);
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
        protocol.run(trackers);
        verify(action, times(1)).trigger(any());
    }

    @Test
    void generatePlaceAgentStats() {
        Map<String, Protocol.PlaceAgentStats> paStats = protocol.generatePlaceAgentStats(trackers);
        Protocol.PlaceAgentStats stats = paStats.get("thePlace");
        assertEquals("thePlace", stats.getPlace());
        assertEquals(5, stats.getCount());
        assertEquals(6, stats.getMinTimeInPlace());
        assertEquals(10, stats.getMaxTimeInPlace());
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
            protocol.init(config);
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
        Protocol protocol = new Protocol();
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

    @Test
    void protocol1() {
        Action action = mock(Action.class);

        Protocol protocol = new Protocol();
        protocol.action = action;
        protocol.rules.put("TEST_RULE1", new AllMaxTime("rule1", TO_UPPER_LOWER_PATTER, 5, 1.0));
        protocol.rules.put("TEST_RULE2", new AnyMaxTime("rule2", TO_UPPER_LOWER_PATTER, 30, 0.2));

        testProtocol(protocol, action, trackers(), DEFAULT_POOL_SIZE, 1);
        testProtocol(protocol, action, trackers(), DEFAULT_POOL_SIZE + 1, 0);
    }

    @Test
    void protocol2() {
        Action action = mock(Action.class);

        Protocol protocol = new Protocol();
        protocol.action = action;
        protocol.rules.put("TEST_RULE1", new AllMaxTime("rule1", TO_UPPER_LOWER_PATTER, 5, 1.0));
        protocol.rules.put("TEST_RULE2", new AnyMaxTime("rule2", TO_UPPER_LOWER_PATTER, 40, 0.2));

        testProtocol(protocol, action, trackers(), DEFAULT_POOL_SIZE, 0);
    }

    @Test
    void protocol3() {
        Action action = mock(Action.class);

        Protocol protocol = new Protocol();
        protocol.action = action;
        protocol.rules.put("TEST_RULE", new AnyMaxTime("LongRunning", TO_UPPER_LOWER_PATTER, 30, 0.01));

        testProtocol(protocol, action, trackers(), DEFAULT_POOL_SIZE, 1);
    }

    @Test
    void protocol4() {
        Action action = mock(Action.class);

        Protocol protocol = new Protocol();
        protocol.action = action;
        protocol.rules.put("TEST_RULE", new AnyMaxTime("LongRunning", TO_LOWER_PLACE, 30, 0.01));

        testProtocol(protocol, action, trackers(), DEFAULT_POOL_SIZE, 0);
    }

    @Test
    void protocol5() {
        Action action = mock(Action.class);

        Protocol protocol = new Protocol();
        protocol.action = action;
        protocol.rules.put("TEST_RULE", new AnyMaxTime("LongRunning", TO_UPPER_PLACE, 30, 0.01));

        testProtocol(protocol, action, trackers(), DEFAULT_POOL_SIZE, 1);
    }

    void testProtocol(Protocol protocol, Action action, Map<String, Sentinel.Tracker> trackers, int poolSize, int expected) {
        AgentPool pool = mock(AgentPool.class);
        try (MockedStatic<AgentPool> agentPool = Mockito.mockStatic(AgentPool.class)) {
            agentPool.when(AgentPool::lookup).thenReturn(pool);
            when(pool.getCurrentPoolSize()).thenReturn(poolSize);

            protocol.run(trackers);
            verify(action, times(expected)).trigger(trackers);
        }
    }

    Map<String, Sentinel.Tracker> trackers() {
        Sentinel.Tracker agent1 = new Sentinel.Tracker("MobileAgent-01");
        agent1.setAgentId("Agent-1234-testing1.txt");
        agent1.setDirectoryEntryKey("http://host.domain.com:8001/ToLowerPlace");
        agent1.incrementTimer(1); // init
        agent1.incrementTimer(5);

        Sentinel.Tracker agent2 = new Sentinel.Tracker("MobileAgent-02");
        agent2.setAgentId("Agent-2345-testing2.txt");
        agent2.setDirectoryEntryKey("http://host.domain.com:8001/ToLowerPlace");
        agent2.incrementTimer(1); // init
        agent2.incrementTimer(15);

        Sentinel.Tracker agent3 = new Sentinel.Tracker("MobileAgent-03");
        agent3.setAgentId("Agent-3456-testing3.txt");
        agent3.setDirectoryEntryKey("http://host.domain.com:8001/ToLowerPlace");
        agent3.incrementTimer(1); // init
        agent3.incrementTimer(9);

        Sentinel.Tracker agent4 = new Sentinel.Tracker("MobileAgent-04");
        agent4.setAgentId("Agent-4567-testing4.txt");
        agent4.setDirectoryEntryKey("http://host.domain.com:8001/ToUpperPlace");
        agent4.incrementTimer(1); // init
        agent4.incrementTimer(35);

        Sentinel.Tracker agent5 = new Sentinel.Tracker("MobileAgent-05");
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
