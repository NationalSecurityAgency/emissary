package emissary.output.sink;

/**
 * Metadata-only JSON sink. Its standard {@code JsonMetadataSink.cfg} sets {@code EMIT=metadata}; serialization is
 * shared with {@link JsonSink}.
 */
public class JsonMetadataSink extends JsonSink {

    @Override
    protected String defaultName() {
        return "JSONM";
    }
}
