package emissary.output.sink;

import emissary.config.Configurator;

import jakarta.annotation.Nullable;

/**
 * Sink for metadata-only JSON output, e.g. an instance of {@link JsonSink} whose class-level configuration resource
 * {@code emissary.output.sink.JsonMetadataSink.cfg} denies all content (primary view and alternate views) so only the
 * metadata parameters and IBDO fields are written. The framework resolves that configuration from the sink's class
 * name, including startup flavor overrides.
 */
public class JsonMetadataSink extends JsonSink {

    @Override
    public void initialize(final Configurator configG, @Nullable final String name, final Configurator sinkConfig) {
        super.initialize(configG, name != null ? name : "METADATA", sinkConfig);
    }
}
