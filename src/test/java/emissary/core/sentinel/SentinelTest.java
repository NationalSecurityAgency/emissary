package emissary.core.sentinel;

import emissary.config.ConfigUtil;
import emissary.core.sentinel.protocols.trackers.AgentTracker;
import emissary.test.core.junit5.UnitTest;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class SentinelTest extends UnitTest {

    Sentinel sentinel;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        sentinel = new SentinelTester();
    }

    @Test
    void trackerTimer() {
        AgentTracker tracker = new AgentTracker("MobileAgent-01");
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
        AgentTracker tracker = new AgentTracker("MobileAgent-01");
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
    void trackerValidJson() {
        ObjectMapper mapper = new ObjectMapper();
        try (JsonParser parser = mapper.getFactory().createParser(new AgentTracker("MobileAgent-01").toString())) {
            mapper.readTree(parser);
        } catch (IOException e) {
            fail(e);
        }
    }

    @Test
    void trackerValidJson2() {
        ObjectMapper mapper = new ObjectMapper();
        try (JsonParser parser = mapper.getFactory().createParser(new AgentTracker("MobileAgent-01").toString())) {
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
