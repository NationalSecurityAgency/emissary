package emissary.util;

import emissary.config.Configurator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ObjectTracing {

    private ObjectTracing() {}

    private static List<String> fieldNames;

    protected static final Logger logger = LoggerFactory.getLogger(ObjectTracing.class);


    public static synchronized void configure() {
        Configurator configG;
        try {
            configG = emissary.config.ConfigUtil.getConfigInfo(ObjectTracing.class);
        } catch (IOException e) {
            logger.error("Cannot open default config file", e);
            return;
        }

        fieldNames = configG.findEntries("FIELD_NAME");

        StringBuilder fieldNameString = new StringBuilder();
        for (String name : fieldNames) {
            fieldNameString.append(name).append(" ");
        }
        logger.info("The fields configured to be logged in ObjectTracing are: {}", fieldNameString);
    }

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
