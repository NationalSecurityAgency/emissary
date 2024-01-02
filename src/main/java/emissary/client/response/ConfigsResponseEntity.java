package emissary.client.response;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@XmlRootElement(name = "configs")
@XmlAccessorType(XmlAccessType.NONE)
public class ConfigsResponseEntity extends BaseResponseEntity {

    private static final long serialVersionUID = -4303550651362965501L;

    private static final Logger logger = LoggerFactory.getLogger(ConfigsResponseEntity.class);

    @XmlElement(name = "local")
    private ConfigList local = new ConfigList();

    public ConfigsResponseEntity() {}

    public ConfigsResponseEntity(ConfigList local) {
        this.local = local;
    }

    public ConfigList getLocal() {
        return local;
    }

    public void setLocal(ConfigList local) {
        this.local = local;
    }

    @Override
    public void append(BaseEntity e) {
        addErrors(e.getErrors());
    }

    @Override
    public void dumpToConsole() {
        getLocal().dumpToConsole();
        getErrors().forEach(logger::error);
        logger.info("");
    }
}
