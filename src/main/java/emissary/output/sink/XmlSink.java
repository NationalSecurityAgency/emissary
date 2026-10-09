package emissary.output.sink;

import emissary.config.Configurator;
import emissary.output.formatter.IDropOffFormatter;
import emissary.output.formatter.XmlFormatter;

import jakarta.annotation.Nullable;

/**
 * Sink that writes XML via a {@link XmlFormatter}.
 */
public class XmlSink extends AbstractSink {

    @Override
    public void initialize(final Configurator configG, @Nullable final String name, final Configurator sinkConfig) {
        if (name == null) {
            this.name = "XML";
        }
        super.initialize(configG, name, sinkConfig);
    }

    @Override
    protected IDropOffFormatter createFormatter() {
        return new XmlFormatter();
    }
}
