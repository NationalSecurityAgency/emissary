package emissary.client.response;

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.NONE)
public class PlaceList {

    @XmlElement(name = "host")
    private String host;

    @XmlElement(name = "places")
    private SortedSet<String> places;

    public PlaceList() {
        places = new TreeSet<>();
    }

    public PlaceList(String host, SortedSet<String> peers) {
        this.host = host;
        this.places = peers;
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
        sb.append("\n" + getHost() + " :");
        for (String peer : getPlaces()) {
            sb.append("\n         " + peer);
        }
        System.out.print(sb.toString());
    }
}
