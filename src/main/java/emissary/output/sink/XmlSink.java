package emissary.output.sink;

import emissary.core.IBaseDataObject;
import emissary.output.io.DateFilterFilenameGenerator;
import emissary.util.io.FileNameGenerator;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/** Sink that writes payloads as XML files. */
public class XmlSink extends AbstractRollableSink {

    private XmlWriter writer;

    @Override
    protected String defaultName() {
        return "XML";
    }

    @Override
    protected void initializeOutput() {
        this.writer = new XmlWriter(this.policy);
    }

    @Override
    protected void writePayloads(final OutputStream out, final List<IBaseDataObject> list, final Map<String, Object> params)
            throws IOException {
        this.writer.writeTo(out, list, params);
    }

    @Override
    protected FileNameGenerator createFilenameGenerator() {
        return new DateFilterFilenameGenerator(".xml");
    }

    @Override
    protected boolean isAppendNewLine() {
        return false;
    }
}
