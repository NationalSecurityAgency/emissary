package emissary.spi;

import emissary.core.IBaseDataObject;

import java.util.Map;

public class EmissaryObjectTracingProvider implements ObjectTracing {

    @Override
    public void getObjectTracePickUpFields(IBaseDataObject d, String filename, Stage stage, Map<String, String> fieldMap) {
        fieldMap.put("inputFileName", filename);
        fieldMap.put("stage", String.valueOf(stage));
    }

    @Override
    public void getObjectTraceDropOffFields(IBaseDataObject d, String filename, Stage stage, String filterName, String outputFileName,
            Map<String, String> fieldMap) {
        fieldMap.put("inputFileName", filename);
        fieldMap.put("stage", String.valueOf(stage));
        fieldMap.put("outputFileName", outputFileName);
        fieldMap.put("uuid", String.valueOf(d.getInternalId()));
        fieldMap.put("outputType", filterName);
    }

    @Override
    public void mapFieldNames(Map<String, String> fieldMap) {
        // no actions to perform
    }
}
