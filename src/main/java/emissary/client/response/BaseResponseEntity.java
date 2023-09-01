package emissary.client.response;

import jakarta.xml.bind.annotation.XmlAccessOrder;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorOrder;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;

@XmlAccessorType(XmlAccessType.NONE)
@XmlAccessorOrder(XmlAccessOrder.ALPHABETICAL)
public class BaseResponseEntity implements Serializable, BaseEntity {

    private static final long serialVersionUID = 3432436269155177605L;

    @XmlElement(name = "errors")
    private final Set<String> errors = new HashSet<>();

    @Override
    public void addError(String error) {
        errors.add(error);
    }

    @Override
    public Set<String> getErrors() {
        return this.errors;
    }

    @Override
    public void append(BaseEntity entity) {
        // This method is here so you consume a call from a peer
        addErrors(entity.getErrors());
    }

    protected void addErrors(@Nullable Set<String> errors) {
        if (errors != null) {
            for (String err : errors) {
                this.addError(err);
            }
        }
    }

    @Override
    public void dumpToConsole() {
        // no-op
    }
}
