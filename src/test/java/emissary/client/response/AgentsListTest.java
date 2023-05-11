package emissary.client.response;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.SortedSet;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentsListTest {

    private ListAppender<ILoggingEvent> appender;
    private final LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
    private final Logger rootLogger = lc.getLogger(Logger.ROOT_LOGGER_NAME);

    @BeforeEach
    void setup() {
        appender = new ListAppender<>();
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void teardown() {
        rootLogger.detachAppender(appender);
    }

    @Test
    void dumpToConsole() {
        Agent agent1 = new Agent("Agent-01", "header-footer.dat(1) - LOWER_CASE.TO_UPPER.TRANSFORM.http://localhost:8001/ToUpperPlace");
        Agent agent2 = new Agent("Agent-02", "Idle");
        Agent agent3 = new Agent();
        agent3.setName("Agent-03");
        agent3.setStatus("junk");
        SortedSet<Agent> agentSet = new TreeSet<>();
        agentSet.add(agent1);
        agentSet.add(agent2);
        agentSet.add(agent3);
        AgentList agentList = new AgentList("localhost", agentSet);

        agentList.dumpToConsole();

        String expected = "localhost :" +
                "\n         Agent-01: header-footer.dat(1) - LOWER_CASE.TO_UPPER.TRANSFORM.http://localhost:8001/ToUpperPlace" +
                "\n         Agent-02: Idle" +
                "\n         Agent-03: junk";

        assertTrue(appender.list.stream().anyMatch(i -> i.getFormattedMessage().contains(expected)));
    }
}
