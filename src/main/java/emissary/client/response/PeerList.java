package emissary.client.response;

import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.NONE)
public class PeerList {

    @XmlElement(name = "host")
    private String host;

    @XmlElement(name = "peers")
    private Set<String> peers;

    public PeerList(String host, Set<String> peers) {
        this.host = host;
        this.peers = peers;
    }

    // used by object mapper
    public PeerList() {}

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Set<String> getPeers() {
        return peers;
    }

    public void setPeers(Set<String> peers) {
        this.peers = peers;
    }

    public void dumpToConsole() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n" + getHost() + " :");
        for (String peer : getPeers()) {
            sb.append("\n         " + peer);
        }
        System.out.print(sb.toString());
    }


}
