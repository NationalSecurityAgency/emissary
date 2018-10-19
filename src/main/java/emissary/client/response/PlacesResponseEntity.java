package emissary.client.response;

import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "peers")
@XmlAccessorType(XmlAccessType.NONE)
public class PlacesResponseEntity extends BaseResponseEntity {
    private static final long serialVersionUID = 5686691885767273319L;

    @XmlElement(name = "local")
    private PlaceList local;

    @XmlElement(name = "cluster")
    private Set<PlaceList> cluster;

    public PlacesResponseEntity() {}

    public PlacesResponseEntity(PlaceList local) {
        this.local = local;
    }

    public void addClusterPlaces(PlaceList pl) {
        if (cluster == null) {
            cluster = new HashSet<>();
        }

        if (pl != null) {
            this.cluster.add(pl);
        }
    }

    public PlaceList getLocal() {
        return local;
    }

    public void setLocal(PlaceList local) {
        this.local = local;
    }

    public Set<PlaceList> getCluster() {
        return cluster;
    }

    @Override
    public void append(BaseEntity e) {
        addErrors(e.getErrors());
        if (e instanceof PlacesResponseEntity) {
            PlacesResponseEntity pre = this.getClass().cast(e);
            addClusterPlaces(pre.getLocal());
        }
    }

    public void dumpToConsole() {
        getLocal().dumpToConsole();
        for (PlaceList placeList : getCluster()) {
            placeList.dumpToConsole();
        }
        System.out.println();
    }
}
