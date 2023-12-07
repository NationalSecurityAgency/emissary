package emissary.core.sentinel.protocols;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.core.sentinel.Sentinel;
import emissary.core.sentinel.protocols.actions.Action;
import emissary.core.sentinel.protocols.actions.Notify;
import emissary.core.sentinel.protocols.rules.AllMaxTime;
import emissary.core.sentinel.protocols.rules.Rule;
import emissary.directory.DirectoryEntry;
import emissary.directory.DirectoryPlace;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
            tracker.setServiceKey("http://host.domain.com:8001/thePlace");
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
        assertEquals("thePlace", protocol.getPlaceKey(trackers.get("Agent-01")));
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
}
