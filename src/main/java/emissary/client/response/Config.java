package emissary.client.response;

import emissary.config.ConfigEntry;

import java.io.Serializable;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.NONE)
public class Config implements Serializable {

    private static final long serialVersionUID = 957308283685018516L;

    @XmlElement(name = "flavors")
    List<String> flavors;

    @XmlElement(name = "configs")
    List<String> configs;

    @XmlElement(name = "entries")
    List<ConfigEntry> entries;

    public Config() {}

    public Config(List<String> flavors, List<String> configs, List<ConfigEntry> entries) {
        this.flavors = flavors;
        this.configs = configs;
        this.entries = entries;
    }

    public List<String> getFlavors() {
        return flavors;
    }

    public void setFlavors(List<String> flavors) {
        this.flavors = flavors;
    }

    public List<String> getConfigs() {
        return configs;
    }

    public void setConfigs(List<String> configs) {
        this.configs = configs;
    }

    public List<ConfigEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<ConfigEntry> entries) {
        this.entries = entries;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append("configs: ").append(String.join(",", getConfigs()));
        sb.append("\n").append("flavors: ").append(String.join(",", getFlavors()));
        for (ConfigEntry entry : getEntries()) {
            sb.append("\n    ").append(entry.getKey()).append(": ").append(entry.getValue());
        }
        return sb.toString();
    }
}
