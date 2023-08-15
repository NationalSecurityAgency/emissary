package emissary.util;

import emissary.core.IBaseDataObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class ObjectTracing {

    private ObjectTracing() {}

    protected static final Logger logger = LoggerFactory.getLogger(ObjectTracing.class);

    /**
     * Given an IBDO, create a map with log entries we care about and return it
     * 
     * @param d the IBDO
     * @return the map of log entries to add to the log
     */
    public static Map<String, String> createTraceMessageMap(IBaseDataObject d) {

        Map<String, String> jsonMap = new HashMap<>();

        // add our fields
        jsonMap.put("inputFileName", d.getFilename());
        jsonMap.put("stage", "PickUpPlace");
        return jsonMap;
    }
}
