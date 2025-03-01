package emissary.client.response;

import jakarta.annotation.Nullable;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

@XmlRootElement(name = "places")
@XmlAccessorType(XmlAccessType.NONE)
public class PlacesResponseEntity extends BaseResponseEntity {
    private static final long serialVersionUID = 5686691885767273319L;

    private static final Logger logger = LoggerFactory.getLogger(PlacesResponseEntity.class);

    @XmlElement(name = "local")
    private PlaceList local = new PlaceList();

    @XmlElement(name = "cluster")
    private Set<PlaceList> cluster = new HashSet<>();

    public PlacesResponseEntity() {}

    public PlacesResponseEntity(PlaceList local) {
        this.local = local;
    }

    public void addClusterPlaces(@Nullable PlaceList pl) {
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

    @Override
    public void dumpToConsole() {
        getLocal().dumpToConsole();
        for (PlaceList placeList : getCluster()) {
            placeList.dumpToConsole();
        }
        logger.info("");
    }
}
