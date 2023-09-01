package emissary.client.response;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@XmlAccessorType(XmlAccessType.NONE)
public class ConfigList implements Serializable {

    private static final long serialVersionUID = 746634175699321058L;

    private static final Logger logger = LoggerFactory.getLogger(ConfigList.class);

    @XmlElement(name = "configs")
    private List<Config> configs;

    public ConfigList() {
        configs = new ArrayList<>();
    }

    public List<Config> getConfigs() {
        return Collections.unmodifiableList(configs);
    }

    public void setConfigs(List<Config> configs) {
        this.configs = configs;
    }

    public void addConfig(Config config) {
        this.configs.add(config);
    }

    public void dumpToConsole() {
        StringBuilder sb = new StringBuilder();
        for (Config config : getConfigs()) {
            sb.append("\n").append(config);
        }
        logger.info("{}", sb);
    }
}
