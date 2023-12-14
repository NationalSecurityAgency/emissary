package emissary.core.sentinel;

import emissary.config.ConfigUtil;
import emissary.core.IMobileAgent;
import emissary.core.Namespace;
import emissary.test.core.junit5.UnitTest;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SentinelTest extends UnitTest {

    Sentinel sentinel;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        sentinel = new SentinelTester();
    }

    @Test
    void watch() throws Exception {
        String agentKey = "MobileAgent-01";
        String placename = "thePlace";
        String shortname = "testing.txt";

        IMobileAgent hdma = mock(IMobileAgent.class);
        when(hdma.getName()).thenReturn(agentKey);
        when(hdma.isInUse()).thenReturn(true);
        when(hdma.agentID()).thenReturn("Agent-1234-" + shortname);
        when(hdma.getLastPlaceProcessed()).thenReturn("http://host.domain.com:8001/" + placename);

        Sentinel.Tracker tracker;
        try (MockedStatic<Namespace> namespace = Mockito.mockStatic(Namespace.class)) {
            namespace.when(Namespace::keySet).thenReturn(Set.of(agentKey));
            namespace.when(() -> Namespace.lookup(agentKey)).thenReturn(hdma);
            sentinel.watch();
            tracker = sentinel.trackers.get(agentKey);
        }

        assertNotNull(tracker);
        assertEquals(0, tracker.getTimer());
        assertEquals(placename, tracker.getPlaceName());
        assertEquals(shortname, tracker.getShortName());
    }

    @Test
    void trackerTimer() {
        Sentinel.Tracker tracker = new Sentinel.Tracker("MobileAgent-01");
        tracker.setAgentId("Agent-1234-testing.txt");
        tracker.setDirectoryEntryKey("http://host.domain.com:8001/thePlace");
        tracker.incrementTimer(5);

        assertEquals(0, tracker.getTimer());
        tracker.incrementTimer(5);
        assertEquals(5, tracker.getTimer());

        tracker.incrementTimer(1);
        assertEquals(6, tracker.getTimer());
    }

    @Test
    void trackerNoAgentIdSet() {
        Sentinel.Tracker tracker = new Sentinel.Tracker("MobileAgent-01");
        tracker.setAgentId("Agent-1234-testing.txt");
        tracker.setDirectoryEntryKey("http://host.domain.com:8001/thePlace");
        tracker.incrementTimer(5);

        tracker.setAgentId("Agent-1234-No_AgentID_Set");
        assertEquals(-1, tracker.getTimer());
        assertEquals("", tracker.getAgentId());
        assertEquals("", tracker.getDirectoryEntryKey());
        assertEquals("", tracker.getPlaceName());
        assertEquals("", tracker.getShortName());
    }

    @Test
    void trackerSorting() {
        Sentinel.Tracker tracker1 = new Sentinel.Tracker("MobileAgent-01");
        Sentinel.Tracker tracker2 = new Sentinel.Tracker("MobileAgent-10");
        Sentinel.Tracker tracker3 = new Sentinel.Tracker("MobileAgent-20");

        sentinel.trackers.put("MobileAgent-01", tracker1);
        sentinel.trackers.put("MobileAgent-10", tracker2);
        sentinel.trackers.put("MobileAgent-20", tracker3);

        List<Sentinel.Tracker> sorted = sentinel.trackers.values().stream().sorted().collect(Collectors.toList());
        assertEquals("MobileAgent-01", sorted.get(0).getAgentName());
        assertEquals("MobileAgent-10", sorted.get(1).getAgentName());
        assertEquals("MobileAgent-20", sorted.get(2).getAgentName());
    }

    @Test
    void trackerValidJson() {
        ObjectMapper mapper = new ObjectMapper();
        try (JsonParser parser = mapper.getFactory().createParser(new Sentinel.Tracker("MobileAgent-01").toString())) {
            mapper.readTree(parser);
        } catch (IOException e) {
            fail(e);
        }
    }

    @Test
    void trackerValidJson2() {
        ObjectMapper mapper = new ObjectMapper();
        try (JsonParser parser = mapper.getFactory().createParser(new Sentinel.Tracker("MobileAgent-01").toString())) {
            mapper.readTree(parser);
        } catch (IOException e) {
            fail(e);
        }
    }

    private static class SentinelTester extends Sentinel {
        @Override
        protected void configure() {
            try {
                this.config = ConfigUtil.getConfigInfo(SentinelTest.class);
                init();
            } catch (IOException e) {
                logger.warn("Cannot read SentinelTest.cfg, taking default values");
            }
        }
    }
}
