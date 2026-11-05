package emissary.output.sink;

import emissary.output.formatter.IDropOffFormatter;
import emissary.output.formatter.XmlFormatter;
import emissary.output.io.DateFilterFilenameGenerator;
import emissary.util.io.FileNameGenerator;

/**
 * Sink that writes XML via a {@link XmlFormatter}.
 */
public class XmlSink extends AbstractRollableSink {

    @Override
    protected String defaultName() {
        return "XML";
    }

    @Override
    protected IDropOffFormatter createFormatter() {
        return new XmlFormatter();
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
