package emissary.client.response;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

@XmlAccessorType(XmlAccessType.NONE)
public class AgentList implements Serializable {
    private static final long serialVersionUID = -6660679929326876133L;

    private static final Logger logger = LoggerFactory.getLogger(AgentList.class);

    @XmlElement(name = "host")
    private String host;

    @XmlElement(name = "agents")
    private SortedSet<Agent> agents;

    public AgentList() {
        agents = new TreeSet<>();
    }

    public AgentList(String host, SortedSet<Agent> agents) {
        this.host = host;
        this.agents = agents;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Set<Agent> getAgents() {
        return agents;
    }

    public void setAgents(SortedSet<Agent> agents) {
        this.agents = agents;
    }

    public void addAgent(Agent agent) {
        this.agents.add(agent);
    }

    public void dumpToConsole() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(getHost()).append(" :");
        for (Agent agent : getAgents()) {
            sb.append("\n         ").append(agent);
        }
        logger.info("{}", sb);
    }
}
