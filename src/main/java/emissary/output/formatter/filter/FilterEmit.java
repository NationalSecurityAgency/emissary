package emissary.output.formatter.filter;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;

import jakarta.annotation.Nullable;

import java.util.Locale;

/**
 * Denies a whole output dimension per {@code EMIT}. Installed first when restrictive. Policy lives here; the
 * formatter's shape code (mixins, payload/views blocks) only avoids emitting empty keys.
 */
public class FilterEmit extends AbstractItemFilter {

    public static final String EMIT = "EMIT";

    /** What a formatter emits. */
    public enum EmitMode {
        /** metadata parameters and payload/views */
        BOTH,
        /** metadata parameters only */
        METADATA,
        /** payload/views only */
        CONTENT
    }

    private EmitMode emitMode = EmitMode.BOTH;

    @Override
    public void configure(@Nullable final Configurator config) {
        super.configure(config);
        if (config == null) {
            return;
        }
        String emit = config.findStringEntry(EMIT, null);
        if (emit != null) {
            try {
                this.emitMode = EmitMode.valueOf(emit.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                this.emitMode = EmitMode.BOTH;
            }
        }
    }

    /** Set the mode directly. */
    public void setEmitMode(final EmitMode emitMode) {
        this.emitMode = emitMode == null ? EmitMode.BOTH : emitMode;
    }

    public EmitMode getEmitMode() {
        return this.emitMode;
    }

    @Override
    public boolean test(@Nullable final IBaseDataObject d, final OutputItem item) {
        if (item.isView()) {
            return this.emitMode != EmitMode.METADATA;
        }
        return this.emitMode != EmitMode.CONTENT;
    }

    @Override
    public String describe(final boolean verbose) {
        return super.describe(verbose) + (verbose ? " " + EMIT + " = " + this.emitMode.name().toLowerCase(Locale.ROOT) : "");
    }
}
