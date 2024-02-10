package emissary.client.response;

import emissary.directory.DirectoryEntry;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.Serializable;

@XmlAccessorType(XmlAccessType.NONE)
public class Directory implements Comparable<Directory>, Serializable {

    private static final long serialVersionUID = 2428511052308449193L;

    private DirectoryEntry directoryEntry;

    @XmlElement(name = "entry")
    private String entry;

    @XmlElement(name = "dataId")
    private String dataId;

    @XmlElement(name = "cost")
    private int cost;

    @XmlElement(name = "quality")
    private int quality;

    @XmlElement(name = "expense")
    private int expense;

    public Directory() {}

    public Directory(DirectoryEntry directoryEntry) {
        this.directoryEntry = directoryEntry;
        setUpEntryInfo();
    }

    private void setUpEntryInfo() {
        entry = directoryEntry.getKey();
        dataId = directoryEntry.getDataId();
        cost = directoryEntry.getCost();
        quality = directoryEntry.getQuality();
        expense = directoryEntry.getExpense();
    }

    public DirectoryEntry getDirectoryEntry() {
        return directoryEntry;
    }

    public void setDirectoryEntry(DirectoryEntry directoryEntry) {
        this.directoryEntry = directoryEntry;
    }

    public String getEntry() {
        return entry;
    }

    public void setEntry(String entry) {
        this.entry = entry;
    }

    public String getDataId() {
        return dataId;
    }

    public void setDataId(String dataId) {
        this.dataId = dataId;
    }

    public int getCost() {
        return cost;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    public int getQuality() {
        return quality;
    }

    public void setQuality(int quality) {
        this.quality = quality;
    }

    public int getExpense() {
        return expense;
    }

    public void setExpense(int expense) {
        this.expense = expense;
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
