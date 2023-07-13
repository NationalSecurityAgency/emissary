package emissary.util;

import java.util.HashMap;
import java.util.Map;

public class InternalProvenance {

    public static Map<String, String> CreatePickupMessageMap(String id) {
        Map<String, String> jsonMap = new HashMap<>();
        jsonMap.put("ID", id);
        jsonMap.put("STAGE", "pickup");
        return jsonMap;
    }
}
