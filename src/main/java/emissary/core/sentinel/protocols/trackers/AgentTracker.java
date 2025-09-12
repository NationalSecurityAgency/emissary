package emissary.core.sentinel.protocols.trackers;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.StringJoiner;

public class AgentTracker implements Tracker, Comparable<AgentTracker> {

    private final String agentName;
    private String agentId;
    private String shortName;
    private String directoryEntryKey;
    private long timer = -1;

    public AgentTracker(String agentName) {
        this.agentName = agentName;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        if (Strings.CS.contains(agentId, "No_AgentID_Set")) {
            clear();
        } else {
            this.agentId = agentId;
            if (Strings.CS.contains(agentId, "Agent-")) {
                this.shortName = getShortName(agentId);
            }
        }
    }

    public String getShortName() {
        return shortName;
    }

    public static String getShortName(String agentId) {
        return StringUtils.substringAfter(StringUtils.substringAfter(agentId, "Agent-"), "-");
    }

    public String getDirectoryEntryKey() {
        return directoryEntryKey;
    }

    public void setDirectoryEntryKey(String directoryEntryKey) {
        this.directoryEntryKey = directoryEntryKey;
    }

    public String getPlaceName() {
        return getPlaceName(this.directoryEntryKey);
    }

    public static String getPlaceName(String directoryEntryKey) {
        return StringUtils.defaultString(StringUtils.substringAfterLast(directoryEntryKey, "/"));
    }

    public long getTimer() {
        return timer;
    }

    public void resetTimer() {
        this.timer = -1;
    }

    public void incrementTimer(long time) {
        if (this.timer == -1) {
            this.timer = 0;
        } else {
            this.timer += time;
        }
    }

    public void clear() {
        this.agentId = "";
        this.shortName = "";
        this.directoryEntryKey = "";
        resetTimer();
    }

    @Override
    public int compareTo(AgentTracker o) {
        return this.agentName.compareTo(o.agentName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof AgentTracker)) {
            return false;
        }

        AgentTracker that = (AgentTracker) o;

        return new EqualsBuilder()
                .append(getTimer(), that.getTimer())
                .append(getAgentName(), that.getAgentName())
                .append(getAgentId(), that.getAgentId())
                .append(getShortName(), that.getShortName())
                .append(getDirectoryEntryKey(), that.getDirectoryEntryKey())
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(getAgentName())
                .append(getAgentId())
                .append(getShortName())
                .append(getDirectoryEntryKey())
                .append(getTimer())
                .toHashCode();
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "{", "}")
                .add("\"agentName\":\"" + agentName + "\"")
                .add("\"directoryEntry\":\"" + directoryEntryKey + "\"")
                .add("\"shortName\":\"" + shortName + "\"")
                .add("\"timeInMinutes\":" + timer)
                .toString();
    }
}
