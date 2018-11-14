package emissary.client.response;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "mapResponseEntity")
@XmlAccessorType(XmlAccessType.NONE)
public class MapResponseEntity extends BaseResponseEntity {
    private static final long serialVersionUID = 5686691885767273319L;

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
        this.response.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> System.out.print(entry.getKey() + ": " + entry.getValue()));
    }
}
