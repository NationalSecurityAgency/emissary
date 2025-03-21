package emissary.client.response;

import jakarta.annotation.Nullable;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@XmlRootElement(name = "agents")
@XmlAccessorType(XmlAccessType.NONE)
public class AgentsResponseEntity extends BaseResponseEntity {
    private static final long serialVersionUID = 5686691885767273319L;

    @XmlElement(name = "local")
    private AgentList local = new AgentList();

    @XmlElement(name = "cluster")
    private final Set<AgentList> cluster = new HashSet<>();

    public AgentsResponseEntity() {}

    public AgentsResponseEntity(AgentList local) {
        this.local = local;
    }

    public void addClusterAgents(@Nullable AgentList pl) {
        if (pl != null) {
            this.cluster.add(pl);
        }
    }

    public AgentList getLocal() {
        return local;
    }

    public void setLocal(AgentList local) {
        this.local = local;
    }

    public Set<AgentList> getCluster() {
        return cluster;
    }

    @Override
    public void append(BaseEntity e) {
        addErrors(e.getErrors());
        if (e instanceof AgentsResponseEntity) {
            AgentsResponseEntity pre = this.getClass().cast(e);
            addClusterAgents(pre.getLocal());
        }
    }

    @Override
    public void dumpToConsole() {
        Stream.concat(cluster.stream(), Stream.of(local)).filter(agentList -> agentList.getHost() != null).collect(Collectors.toSet()).stream()
                .sorted(Comparator.comparing(AgentList::getHost)).forEach(agentList -> {
                    if (!agentList.getAgents().isEmpty()) {
                        agentList.dumpToConsole();
                    }
                });
    }
}
