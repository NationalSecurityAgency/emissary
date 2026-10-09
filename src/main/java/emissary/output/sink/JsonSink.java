package emissary.output.sink;

import emissary.config.Configurator;
import emissary.output.formatter.IDropOffFormatter;
import emissary.output.formatter.JsonFormatter;

import jakarta.annotation.Nullable;

/**
 * Sink that writes JSON via a {@link JsonFormatter}.
 */
public class JsonSink extends AbstractSink {

    @Override
    public void initialize(final Configurator configG, @Nullable final String name, final Configurator sinkConfig) {
        if (name == null) {
            this.name = "JSON";
        }
        super.initialize(configG, name, sinkConfig);
    }

    @Override
    protected IDropOffFormatter createFormatter() {
        return new JsonFormatter();
    }
}
