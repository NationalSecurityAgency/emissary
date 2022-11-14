package emissary.client.response;

import emissary.util.TimeUtil;

import com.google.gson.JsonObject;

/**
 * Formatter for Emissary MobileAgents. Currently,supports json output, but could be expanded to support any number of
 * output formats.
 */
public class AgentsFormatter {

    private final String host;
    private final String timestamp;

    private AgentsFormatter(final Builder builder) {
        this.host = builder.host;
        this.timestamp = builder.timestamp;
    }

    public String json(final Agent agent) {
        JsonObject json = json();
        json.addProperty("name", agent.getName());
        json.addProperty("status", agent.getStatus());
        return json.toString();
    }

    public String json(final String name, final String value) {
        JsonObject json = json();
        json.addProperty(name, value);
        return json.toString();
    }

    private JsonObject json() {
        JsonObject json = new JsonObject();
        json.addProperty("timestamp", timestamp);
        json.addProperty("host", host);
        return json;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String host;
        private String timestamp = TimeUtil.getCurrentDateFullISO8601();

        public Builder withHost(final String host) {
            this.host = host;
            return this;
        }

        public Builder withTimestamp(final String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public AgentsFormatter build() {
            return new AgentsFormatter(this);
        }
    }
}
