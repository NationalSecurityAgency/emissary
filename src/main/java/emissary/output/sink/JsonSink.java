package emissary.output.sink;

import emissary.output.formatter.IDropOffFormatter;
import emissary.output.formatter.JsonFormatter;
import emissary.output.io.DateFilterFilenameGenerator;
import emissary.util.io.FileNameGenerator;

/**
 * Sink that writes JSON via a {@link JsonFormatter}.
 */
public class JsonSink extends AbstractRollableSink {

    @Override
    protected String defaultName() {
        return "JSON";
    }

    @Override
    protected IDropOffFormatter createFormatter() {
        return new JsonFormatter();
    }

    @Override
    protected FileNameGenerator createFilenameGenerator() {
        return new DateFilterFilenameGenerator(".json");
    }
}
