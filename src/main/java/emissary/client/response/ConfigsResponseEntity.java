package emissary.client.response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

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
