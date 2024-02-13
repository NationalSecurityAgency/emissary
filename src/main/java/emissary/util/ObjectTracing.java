package emissary.util;

import emissary.core.IBaseDataObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static net.logstash.logback.marker.Markers.appendEntries;

@Deprecated
public class ObjectTracing {

    protected static Logger objectTraceLogger = LoggerFactory.getLogger("objectTrace");;

    public enum Stage {
        PickUp, DropOff
    }

    public ObjectTracing() {}

    public void emitLifecycleEvent(IBaseDataObject d, ObjectTracing.Stage stage) {
        emitLifecycleEvent(d.getFilename(), stage);
    }

    public void emitLifecycleEvent(String filename, ObjectTracing.Stage stage) {

        Map<String, String> jsonMap = new HashMap<>();

        // add our fields
        jsonMap.put("inputFileName", filename);
        jsonMap.put("stage", String.valueOf(stage));

        objectTraceLogger.info(appendEntries(jsonMap), "");
    }
}
