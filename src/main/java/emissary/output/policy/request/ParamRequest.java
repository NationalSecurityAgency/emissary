package emissary.output.policy.request;

import jakarta.annotation.Nullable;

import java.util.Objects;

/**
 * Facts about a metadata parameter key or one of its values. A {@code null} value means this is a key-level decision.
 */
public final class ParamRequest {

    private final PayloadRequest payload;
    private final String key;

    @Nullable
    private final Object value;
    private final boolean keyDecision;

    /**
     * Create a request.
     *
     * @param payload the owning payload facts
     * @param key the parameter key
     * @param value the value, or {@code null} for a key-level decision
     */
    public ParamRequest(final PayloadRequest payload, final String key, @Nullable final Object value) {
        this(payload, key, value, value == null);
    }

    private ParamRequest(final PayloadRequest payload, final String key, @Nullable final Object value, final boolean keyDecision) {
        this.payload = Objects.requireNonNull(payload, "payload");
        this.key = Objects.requireNonNull(key, "key");
        this.value = value;
        this.keyDecision = keyDecision;
    }

    /** A value question, including a value that is explicitly null. */
    public static ParamRequest forValue(final PayloadRequest payload, final String key, @Nullable final Object value) {
        return new ParamRequest(payload, key, value, false);
    }

    public PayloadRequest payload() {
        return this.payload;
    }

    public String key() {
        return this.key;
    }

    @Nullable
    public Object value() {
        return this.value;
    }

    /** Whether this is a key-level rather than a value-level decision. */
    public boolean isKeyDecision() {
        return this.keyDecision;
    }
}
