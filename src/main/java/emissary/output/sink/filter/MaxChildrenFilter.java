package emissary.output.sink.filter;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;

import jakarta.annotation.Nullable;

import java.util.List;

/** Denies payloads whose family exceeds {@link #MAX_CHILDREN} descendants. */
public class MaxChildrenFilter extends AbstractPayloadFilter {

    public static final String MAX_CHILDREN = "MAX_CHILDREN";

    private int maxChildren = Integer.MAX_VALUE;

    @Override
    public void configure(@Nullable final Configurator configG) {
        if (configG != null) {
            this.maxChildren = configG.findIntEntry(MAX_CHILDREN, this.maxChildren);
        }
    }

    @Override
    public boolean test(final IBaseDataObject payload) {
        return payload.getNumChildren() <= this.maxChildren;
    }

    @Override
    public boolean test(final List<IBaseDataObject> payloads) {
        return payloads.isEmpty() || payloads.size() - 1 <= this.maxChildren;
    }

    @Override
    public String describe(final boolean verbose) {
        return super.describe(verbose) + (verbose ? " " + MAX_CHILDREN + " = " + this.maxChildren : "");
    }
}
