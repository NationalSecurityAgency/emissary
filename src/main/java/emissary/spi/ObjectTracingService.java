package emissary.spi;

import emissary.core.IBaseDataObject;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import static net.logstash.logback.marker.Markers.appendEntries;

public class ObjectTracingService {

    @Nullable
    @SuppressWarnings("NonFinalStaticField")
    private static ServiceLoader<ObjectTracing> loader = null;
    private static final Logger objectTraceLogger = LoggerFactory.getLogger("objectTrace");

    private ObjectTracingService() {}

    public static synchronized void emitLifecycleEvent(IBaseDataObject d, String filename, ObjectTracing.Stage stage, boolean useObjectTracing) {
        emitLifecycleEvent(d, filename, stage, useObjectTracing, null, null);
    }

    public static synchronized void emitLifecycleEvent(IBaseDataObject d, String filename, ObjectTracing.Stage stage, boolean useObjectTracing,
            @Nullable String filterName, @Nullable String outputFileName) {
        if (useObjectTracing) {
            if (loader == null) {
                loader = ServiceLoader.load(ObjectTracing.class);
            }

            // have the appropriate providers add fields
            Map<String, String> jsonFieldMap = new HashMap<>();
            for (ObjectTracing tracing : loader) {
                if (stage.equals(ObjectTracing.Stage.PICK_UP)) {
                    tracing.getObjectTracePickUpFields(d, filename, stage, jsonFieldMap);
                } else {
                    tracing.getObjectTraceDropOffFields(d, filename, stage, filterName, outputFileName, jsonFieldMap);
                }
            }
            // once we have added fields from all providers, perform remapping of field names as appropriate
            for (ObjectTracing tracing : loader) {
                tracing.mapFieldNames(jsonFieldMap);
            }
            objectTraceLogger.info(appendEntries(jsonFieldMap), "");
        }
    }
}
