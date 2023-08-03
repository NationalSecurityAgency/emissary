package emissary.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ObjectTracing {

    private ObjectTracing() {}

    private static final List<String> fieldNames = new ArrayList<>();

    protected static final Logger logger = LoggerFactory.getLogger(ObjectTracing.class);

    public static void setUpFieldNames(List<String> newFieldNames) {
        fieldNames.addAll(newFieldNames);
    }

    public static Map<String, String> createTraceMessageMap(String[] fieldValues) {

        Map<String, String> jsonMap = new HashMap<>();

        if (fieldValues.length != fieldNames.size()) {
            logger.error("Cannot create log entry, the number of fields does not equals the number of values");
            return Collections.emptyMap();
        }

        for (int i = 0; i < fieldValues.length; i++) {
            jsonMap.put(fieldNames.get(i), fieldValues[i]);
        }
        return jsonMap;
    }
}
