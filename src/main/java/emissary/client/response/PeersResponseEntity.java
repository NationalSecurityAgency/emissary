package emissary.client.response;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

@XmlRootElement(name = "peers")
@XmlAccessorType(XmlAccessType.NONE)
public class PeersResponseEntity extends BaseResponseEntity {

    private static final long serialVersionUID = 5686691885767273319L;

    private static final Logger logger = LoggerFactory.getLogger(PeersResponseEntity.class);

    @XmlElement(name = "local")
    private PeerList local = new PeerList();

    @XmlElement(name = "cluster")
    private Set<PeerList> cluster = new HashSet<>();

    public PeersResponseEntity() {}

    public PeersResponseEntity(PeerList local) {
        this.local = local;
    }

    public void addClusterPeers(PeerList pl) {
        if (cluster == null) {
            cluster = new HashSet<>();
        }
        this.cluster.add(pl);
    }

    public PeerList getLocal() {
        return local;
    }

    public void setLocal(PeerList local) {
        this.local = local;
    }

    public Set<PeerList> getCluster() {
        return cluster;
    }

    @Override
    public void append(BaseEntity e) {
        addErrors(e.getErrors());
        if (e instanceof PeersResponseEntity) {
            PeersResponseEntity pre = this.getClass().cast(e);
            addClusterPeers(pre.getLocal());
        }
    }

    @Override
    public void dumpToConsole() {
        getLocal().dumpToConsole();
        for (PeerList peer : getCluster()) {
            if (null != peer) {
                peer.dumpToConsole();
            }
        }
        Set<String> errors = getErrors();
        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder("\nErrors");
            for (String error : errors) {
                sb.append(error);
            }
            logger.error("{}", sb);
        }
    }
}
