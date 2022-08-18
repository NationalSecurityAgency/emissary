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
public class PlaceList implements Serializable {
    private static final long serialVersionUID = 996866694436011053L;

    private static final Logger logger = LoggerFactory.getLogger(PlaceList.class);

    @XmlElement(name = "host")
    private String host;

    @XmlElement(name = "places")
    private SortedSet<String> places;

    public PlaceList() {
        places = new TreeSet<>();
    }

    public PlaceList(String host, SortedSet<String> places) {
        this.host = host;
        this.places = places;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Set<String> getPlaces() {
        return places;
    }

    public void setPlaces(SortedSet<String> places) {
        this.places = places;
    }

    public void addPlace(String place) {
        this.places.add(place);
    }

    public void dumpToConsole() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(getHost()).append(" :");
        for (String place : getPlaces()) {
            sb.append("\n         ").append(place);
        }
        logger.info("{}", sb);
    }
}
