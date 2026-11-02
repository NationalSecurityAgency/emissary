package emissary.output.sink;

import emissary.core.IBaseDataObject;
import emissary.output.io.DateFilterFilenameGenerator;
import emissary.util.io.FileNameGenerator;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/** Sink that writes payloads as JSON files. */
public class JsonSink extends AbstractRollableSink {

    private JsonWriter writer;

    @Override
    protected String defaultName() {
        return "JSON";
    }

    @Override
    protected void initializeOutput() {
        this.writer = new JsonWriter(this.configG, this.sinkConfig, this.policy);
    }

    @Override
    public String outputParameterName(final String key) {
        return this.writer.outputParameterName(key);
    }

    @Override
    protected void writePayloads(final OutputStream out, final List<IBaseDataObject> list, final Map<String, Object> params)
            throws IOException {
        this.writer.writeTo(out, list, params);
    }

    @Override
    protected FileNameGenerator createFilenameGenerator() {
        return new DateFilterFilenameGenerator(".json");
    }
}
