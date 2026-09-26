package emissary.output.sink.filter;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;
import emissary.output.filter.IFilterCondition;

import jakarta.annotation.Nullable;

import java.util.List;

/** Base for a payload filter: one allow test per payload. Adapts legacy {@link IFilterCondition}. */
public abstract class AbstractPayloadFilter implements IFilterCondition {

    /** Whether this payload may be emitted. */
    public boolean test(final IBaseDataObject payload) {
        return true;
    }

    /** Whether this list of payloads may be emitted; all must pass by default. */
    public boolean test(final List<IBaseDataObject> payloads) {
        return payloads.stream().allMatch(this::test);
    }

    /** Configure this filter. */
    public void configure(@Nullable final Configurator config) {
        // nothing configured by default
    }

    @Override
    public void initialize(@Nullable final Configurator configG) {
        configure(configG);
    }

    @Override
    public boolean accept(final IBaseDataObject payload) {
        return test(payload);
    }

    @Override
    public boolean accept(final List<IBaseDataObject> payloads) {
        return test(payloads);
    }

    /** Concise label for this filter. */
    public final String describe() {
        return describe(false);
    }

    /** Description of this filter, optionally including its configured state. */
    public String describe(final boolean verbose) {
        return getClass().getSimpleName();
    }
}
