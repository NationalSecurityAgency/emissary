package emissary.util;

import java.util.HashMap;
import java.util.Map;

public class InternalProvenanceUtil {

    private InternalProvenanceUtil() {
        throw new IllegalStateException("Utility Class");
    }

    public static Map<String, String> createPickupMessageMap(String inputFileName) {
        Map<String, String> jsonMap = new HashMap<>();
        jsonMap.put("inputFilename", inputFileName);
        jsonMap.put("stage", "pickup");
        return jsonMap;
    }
}
