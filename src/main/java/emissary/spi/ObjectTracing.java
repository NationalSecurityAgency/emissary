package emissary.spi;

import emissary.core.IBaseDataObject;

import java.util.Map;

public interface ObjectTracing {

    enum Stage {
        PICK_UP, DROP_OFF
    }

    /**
     * With this provider, add the appropriate fields/values to the fieldMap
     * 
     * @param d The IBDO
     * @param filename The filename of the object
     * @param stage The stage
     * @param fieldMap The map of fields we are adding to
     */
    void getObjectTracePickUpFields(IBaseDataObject d, String filename, ObjectTracing.Stage stage, Map<String, String> fieldMap);

    /**
     * With this provider, add the appropriate fields/values to the fieldMap
     *
     * @param d The IBDO
     * @param filename The filename of the object
     * @param stage The stage
     * @param filterName The name of the output filter (json, xml, etc.)
     * @param outputFileName The output filename that is generated
     * @param fieldMap The map of fields we are adding to
     */
    void getObjectTraceDropOffFields(IBaseDataObject d, String filename, Stage stage, String filterName, String outputFileName,
            Map<String, String> fieldMap);

    /**
     * Remaps field names if needed
     * 
     * @param fieldMap The map of fields and values
     */
    void mapFieldNames(Map<String, String> fieldMap);
}
