package emissary.client.response;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@XmlRootElement(name = "directories")
@XmlAccessorType(XmlAccessType.NONE)
public class DirectoryResponseEntity extends BaseResponseEntity {
    private static final long serialVersionUID = 5686691885767273319L;

    private static final Logger logger = LoggerFactory.getLogger(DirectoryResponseEntity.class);

    @XmlElement(name = "local")
    private DirectoryList local = new DirectoryList();

    public DirectoryResponseEntity() {}

    public DirectoryResponseEntity(DirectoryList local) {
        this.local = local;
    }

    public DirectoryList getLocal() {
        return local;
    }

    public void getLocal(DirectoryList local) {
        this.local = local;
    }

    public void setLocal(DirectoryList local) {
        this.local = local;
    }

    @Override
    public void append(BaseEntity e) {
        addErrors(e.getErrors());
    }

    @Override
    public void dumpToConsole() {
        getLocal().dumpToConsole();
        logger.info("");
    }
}
