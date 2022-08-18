package emissary.client.response;

import java.io.Serializable;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@XmlAccessorType(XmlAccessType.NONE)
public class PeerList implements Serializable {
    private static final long serialVersionUID = -5236361363769379766L;

    private static final Logger logger = LoggerFactory.getLogger(PeerList.class);

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
        sb.append("\n").append(getHost()).append(" :");
        for (String peer : getPeers()) {
            sb.append("\n         ").append(peer);
        }
        logger.info("{}", sb);
    }


}
