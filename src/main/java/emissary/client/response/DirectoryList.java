package emissary.client.response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.NONE)
public class DirectoryList implements Serializable {
    private static final long serialVersionUID = -6660679929326876133L;

    private static final Logger logger = LoggerFactory.getLogger(DirectoryList.class);

    @XmlElement(name = "directoryPlace")
    private String directoryPlace;

    @XmlElement(name = "entries")
    private SortedSet<Directory> entries;

    public DirectoryList() {
        entries = new TreeSet<>();
    }

    public DirectoryList(String directoryPlace, SortedSet<Directory> entries) {
        this.directoryPlace = directoryPlace;
        this.entries = entries;
    }

    public String getDirectoryPlace() {
        return directoryPlace;
    }

    public void setDirectoryPlace(String directoryPlace) {
        this.directoryPlace = directoryPlace;
    }

    public Set<Directory> getEntries() {
        return entries;
    }

    public void setEntries(SortedSet<Directory> entries) {
        this.entries = entries;
    }

    public void addEntries(Directory entry) {
        this.entries.add(entry);
    }

    public void dumpToConsole() {
        if (getDirectoryPlace() != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("DirectoryPlace: ").append("\n");
            sb.append("  ").append(getDirectoryPlace()).append("\n").append("Entries: ");
            for (Directory entry : getEntries()) {
                sb.append("\n  ").append(entry);
            }
            logger.info("{}", sb);
        }
    }
}
