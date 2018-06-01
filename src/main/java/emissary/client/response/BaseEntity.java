package emissary.client.response;

import java.util.Set;

public interface BaseEntity {

    void addError(String error);

    Set<String> getErrors();

    void append(BaseEntity entity);

    void dumpToConsole();

}
