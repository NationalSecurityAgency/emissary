package emissary.output.sink;

/**
 * Sink for metadata-only JSON output; its {@code JsonMetadataSink.cfg} configuration denies all content.
 */
public class JsonMetadataSink extends JsonSink {

    @Override
    protected String defaultName() {
        return "JSONM";
    }
}
