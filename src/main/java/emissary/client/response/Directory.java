package emissary.client.response;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.Serializable;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.NONE)
public class Directory implements Comparable<Directory>, Serializable {

    private static final long serialVersionUID = 2428511052308449193L;

    @XmlElement(name = "dataId")
    private String dataId;

    @XmlElement(name = "entry")
    private String entry;

    public Directory() {}

    public Directory(String dataId, String entry) {
        this.dataId = dataId;
        this.entry = entry;
    }

    public String getDataId() {
        return dataId;
    }

    public void setDataId(String dataId) {
        this.dataId = dataId;
    }

    public String getEntry() {
        return entry;
    }

    public void setEntry(String entry) {
        this.entry = entry;
    }

    @Override
    public int compareTo(Directory o) {
        return getEntry().compareTo(o.getEntry());
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return getEntry();
    }
}
