package emissary.spi;

import emissary.core.IBaseDataObject;

import java.util.Map;

public class EmissaryObjectTracingProvider implements ObjectTracing {

    @Override
    public void getObjectTraceFields(IBaseDataObject d, String filename, Stage stage, Map<String, String> fieldMap) {
        fieldMap.put("inputFileName", filename);
        fieldMap.put("stage", String.valueOf(stage));
    }

    @Override
    public void mapFieldNames(Map<String, String> fieldMap) {
        // no actions to perform
    }
}
