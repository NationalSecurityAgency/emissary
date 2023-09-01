package emissary.client.response;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.TreeMap;

@XmlRootElement(name = "mapResponseEntity")
@XmlAccessorType(XmlAccessType.NONE)
public class MapResponseEntity extends BaseResponseEntity {
    private static final long serialVersionUID = 5686691885767273319L;

    private static final Logger logger = LoggerFactory.getLogger(MapResponseEntity.class);

    @XmlElement(name = "response")
    private final Map<String, String> response;

    public MapResponseEntity() {
        response = new TreeMap<>();
    }

    public void addKeyValue(String key, String value) {
        this.response.put(key, value);
    }

    public Map<String, String> getResponse() {
        return this.response;
    }

    @Override
    public void append(BaseEntity e) {
        addErrors(e.getErrors());
        if (e instanceof MapResponseEntity) {
            MapResponseEntity entity = this.getClass().cast(e);
            this.response.putAll(entity.getResponse());
        }
    }

    @Override
    public void dumpToConsole() {
        this.response.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> logger.info("{}: {}", entry.getKey(), entry.getValue()));
    }
}
