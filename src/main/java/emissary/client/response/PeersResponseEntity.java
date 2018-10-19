package emissary.client.response;

import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "peers")
@XmlAccessorType(XmlAccessType.NONE)
public class PeersResponseEntity extends BaseResponseEntity {

    private static final long serialVersionUID = 5686691885767273319L;

    @XmlElement(name = "local")
    private PeerList local;

    @XmlElement(name = "cluster")
    private Set<PeerList> cluster;

    public PeersResponseEntity() {}

    public PeersResponseEntity(PeerList local) {
        this.local = local;
    }

    public void addClusterPeers(PeerList pl) {
        if (cluster == null) {
            cluster = new HashSet<PeerList>();
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
        if (errors.size() > 0) {
            System.out.print("\nErrors");
            for (String error : errors) {
                System.out.print(error);
            }
        }
        System.out.println();
    }
}
