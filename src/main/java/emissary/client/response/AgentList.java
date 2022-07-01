package emissary.client.response;

import java.io.Serializable;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@XmlAccessorType(XmlAccessType.NONE)
public class AgentList implements Serializable {
    private static final long serialVersionUID = -6660679929326876133L;

    private static final Logger logger = LoggerFactory.getLogger(AgentList.class);

    @XmlElement(name = "host")
    private String host;

    @XmlElement(name = "agents")
    private SortedSet<String> agents;

    public AgentList() {
        agents = new TreeSet<>();
    }

    public AgentList(String host, SortedSet<String> peers) {
        this.host = host;
        this.agents = peers;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Set<String> getAgents() {
        return agents;
    }

    public void setAgents(SortedSet<String> agents) {
        this.agents = agents;
    }

    public void addAgent(String agent) {
        this.agents.add(agent);
    }

    public void dumpToConsole() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(getHost()).append(" :");
        for (String peer : getAgents()) {
            sb.append("\n         ").append(peer);
        }
        logger.info("{}", sb);
    }
}
