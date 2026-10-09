package emissary.output.sink.filter;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;
import emissary.output.filter.IFilterCondition;

import jakarta.annotation.Nullable;

import java.util.List;

/**
 * Payload filter that denies a payload whose family carries more than {@link #MAX_CHILDREN} descendants. The threshold
 * is the maximum number of extracted children (siblings) of the top-level document.
 *
 * <p>
 * When asked about a single payload the count comes from {@link IBaseDataObject#getNumChildren()}; when asked about a
 * family list (the TLD followed by its children) the count is derived from the list size minus the TLD. Deny-first: a
 * payload at or below the threshold is allowed.
 */
public class MaxChildrenFilter implements IFilterCondition {

    public static final String MAX_CHILDREN = "MAX_CHILDREN";

    private int maxChildren = Integer.MAX_VALUE;

    @Override
    public void initialize(@Nullable final Configurator configG) {
        if (configG != null) {
            this.maxChildren = configG.findIntEntry(MAX_CHILDREN, this.maxChildren);
        }
    }

    @Override
    public boolean accept(final IBaseDataObject payload) {
        return payload.getNumChildren() <= this.maxChildren;
    }

    @Override
    public boolean accept(final List<IBaseDataObject> payloads) {
        return payloads.isEmpty() || payloads.size() - 1 <= this.maxChildren;
    }
}
