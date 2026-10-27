package emissary.output.policy.request;

import emissary.core.IBaseDataObject;
import emissary.output.policy.OutputPolicy;

import jakarta.annotation.Nullable;

import java.util.Objects;

/**
 * Facts about one alternate view.
 */
public final class ViewRequest {

    private final PayloadRequest payload;
    private final String viewName;

    /**
     * Create a request.
     *
     * @param payload the owning payload facts
     * @param viewName the alternate view name
     */
    public ViewRequest(final PayloadRequest payload, final String viewName) {
        this.payload = Objects.requireNonNull(payload, "payload");
        this.viewName = Objects.requireNonNull(viewName, "viewName");
    }

    public PayloadRequest payload() {
        return this.payload;
    }

    public String viewName() {
        return this.viewName;
    }

    /** The owning payload, for stages that need more than the view name. */
    public IBaseDataObject data() {
        return this.payload.payload();
    }

    /** Bytes of the candidate, including primary content represented by {@code PrimaryView}. */
    @Nullable
    public byte[] content() {
        return OutputPolicy.PRIMARY_VIEW_NAME.equals(this.viewName) ? data().data() : data().getAlternateView(this.viewName);
    }
}
