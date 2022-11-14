package emissary.client.response;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentsFormatterTest {

    @Test
    void errorJson() {
        String expected = "{\"timestamp\":\"12345\",\"host\":\"local\",\"error\":\"errMsg\"}";
        AgentsFormatter formatter = AgentsFormatter.builder().withHost("local").withTimestamp("12345").build();
        assertEquals(expected, formatter.json("error", "errMsg"));
    }

    @Test
    void agentJson() {
        Agent agent = new Agent("Agent-01", "Idle");
        String expected = "{\"timestamp\":\"12345\",\"host\":\"local\",\"name\":\"Agent-01\",\"status\":\"Idle\"}";
        AgentsFormatter formatter = AgentsFormatter.builder().withHost("local").withTimestamp("12345").build();
        assertEquals(expected, formatter.json(agent));
    }
}
