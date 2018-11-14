package emissary.client.response;

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.NONE)
public class AgentList {

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
        sb.append("\n" + getHost() + " :");
        for (String peer : getAgents()) {
            sb.append("\n         " + peer);
        }
        System.out.print(sb.toString());
    }
}
