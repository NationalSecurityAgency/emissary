package emissary.spi;

import emissary.core.IBaseDataObject;

import java.util.Map;

public interface ObjectTracing {

    enum Stage {
        PickUp, DropOff
    }

    /**
     * With this provider, add the appropriate fields/values to the fieldMap
     * 
     * @param d The IBDO
     * @param filename The filename of the object
     * @param stage The stage
     * @param fieldMap The map of fields we are adding to
     */
    void getObjectTraceFields(IBaseDataObject d, String filename, ObjectTracing.Stage stage, Map<String, String> fieldMap);

    /**
     * Remaps field names if needed
     * 
     * @param fieldMap The map of fields and values
     */
    void mapFieldNames(Map<String, String> fieldMap);
}
