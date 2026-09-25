package emissary.output.formatter.filter;

import jakarta.annotation.Nullable;

/**
 * One unit of output a filter decides about: a metadata parameter (a key, optionally with a value) or a view (by name).
 * The primary payload is just a {@code view("PrimaryView")}.
 */
public final class OutputItem {

    /** Kind of output item. */
    public enum Kind {
        /** a metadata parameter, by key and optionally value */
        PARAMETER,
        /** a view, by name */
        VIEW
    }

    private final Kind kind;
    private final String name;
    private final Object value;
    private final byte[] bytes;

    private OutputItem(final Kind kind, final String name, @Nullable final Object value, @Nullable final byte[] bytes) {
        this.kind = kind;
        this.name = name;
        this.value = value;
        this.bytes = bytes;
    }

    /** A parameter item by key only. */
    public static OutputItem parameter(final String key) {
        return new OutputItem(Kind.PARAMETER, key, null, null);
    }

    /** A parameter item for one specific value of a key. */
    public static OutputItem parameter(final String key, @Nullable final Object value) {
        return new OutputItem(Kind.PARAMETER, key, value, null);
    }

    /** A view item by name only. */
    public static OutputItem view(final String viewName) {
        return new OutputItem(Kind.VIEW, viewName, null, null);
    }

    /** A view item by name and content. */
    public static OutputItem view(final String viewName, @Nullable final byte[] bytes) {
        return new OutputItem(Kind.VIEW, viewName, null, bytes);
    }

    public Kind kind() {
        return this.kind;
    }

    public boolean isView() {
        return this.kind == Kind.VIEW;
    }

    public boolean isParameter() {
        return this.kind == Kind.PARAMETER;
    }

    /** The parameter key or view name. */
    public String name() {
        return this.name;
    }

    /** The parameter value, or null for a key-level decision. */
    @Nullable
    public Object value() {
        return this.value;
    }

    /** The view bytes, or null when not known. */
    @Nullable
    public byte[] bytes() {
        return this.bytes;
    }

    @Override
    public String toString() {
        if (isParameter()) {
            return this.value == null ? "param " + this.name : "param " + this.name + "=" + this.value;
        }
        return "view " + this.name;
    }
}
