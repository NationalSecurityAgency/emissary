package emissary.place.sample;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;
import emissary.place.ServiceProviderPlace;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * This place writes a parameter based on whether the input data was batched or not.
 */
public class BatchPlace extends ServiceProviderPlace {
    public static final String ENABLE_BATCH_PROCESSING = "ENABLE_BATCH_PROCESSING";
    public static final String PROCESSED = "PROCESSED";
    public static final String SINGLE = "SINGLE";
    public static final String BATCH = "BATCH";

    /**
     * Create the place with an existing {@link Configurator}.
     *
     * @param configs config data
     */
    public BatchPlace(Configurator configs) throws IOException {
        super(configs);
    }

    @Override
    public boolean isBatchProcessingEnabled() {
        return Objects.requireNonNull(configG).findBooleanEntry(ENABLE_BATCH_PROCESSING, false);
    }

    @Override
    public void process(IBaseDataObject d) {
        d.setParameter(PROCESSED, SINGLE);
    }

    @Override
    public void process(List<IBaseDataObject> dList) {
        for (IBaseDataObject d : dList) {
            d.setParameter(PROCESSED, BATCH);
        }
    }

}
