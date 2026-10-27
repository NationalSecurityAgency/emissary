package emissary.output.policy;

import emissary.config.Configurator;
import emissary.core.EmissaryRuntimeException;
import emissary.output.policy.Decision.Outcome;
import emissary.output.policy.OutputPolicy.EmitMode;
import emissary.output.policy.rule.RuleParser;
import emissary.output.policy.rule.RuleStage;

import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Builds an {@link OutputPolicy} from the sink configuration.
 *
 * <p>
 * Reads {@code POLICY_DEFAULT}, {@code EMIT}, and the ordered {@code POLICY_STAGE} entries. Invalid settings fail at
 * startup instead of changing output silently.
 */
final class PolicyConfig {

    private static final String POLICY_DEFAULT = "POLICY_DEFAULT";
    private static final String POLICY_STAGE = "POLICY_STAGE";
    private static final String EMIT = "EMIT";

    private PolicyConfig() {}

    /** Build a policy, or the allow-all policy when no configuration is supplied. */
    static OutputPolicy from(@Nullable final Configurator config) {
        if (config == null) {
            return OutputPolicy.allowAll();
        }
        return builder(config).build();
    }

    /** Create a builder pre-populated from configuration. */
    static Builder builder(@Nullable final Configurator config) {
        final Builder builder = Builder.create();
        if (config == null) {
            return builder;
        }
        applyDefault(config, builder);
        applyEmit(config, builder);
        applyStages(config, builder);
        return builder;
    }

    private static void applyDefault(final Configurator config, final Builder builder) {
        final String value = config.findStringEntry(POLICY_DEFAULT, "allow");
        if ("allow".equalsIgnoreCase(value.trim())) {
            builder.defaults(Outcome.ALLOW);
        } else if ("deny".equalsIgnoreCase(value.trim())) {
            builder.defaults(Outcome.DENY);
        } else {
            throw new EmissaryRuntimeException("Invalid " + POLICY_DEFAULT + " \"" + value + "\": expected 'allow' or 'deny'");
        }
    }

    private static void applyEmit(final Configurator config, final Builder builder) {
        builder.emit(emitMode(config));
    }

    /**
     * Read the configured {@code EMIT} mode.
     *
     * @param config configuration to read, or {@code null}
     * @return the configured mode, or {@link EmitMode#BOTH} when unset
     * @throws EmissaryRuntimeException if the value is not a known mode
     */
    private static EmitMode emitMode(@Nullable final Configurator config) {
        if (config == null) {
            return EmitMode.BOTH;
        }
        final String value = config.findStringEntry(EMIT, null);
        if (value == null) {
            return EmitMode.BOTH;
        }
        try {
            return EmitMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new EmissaryRuntimeException("Invalid " + EMIT + " \"" + value + "\": expected 'both', 'metadata', or 'content'",
                    e);
        }
    }

    private static void applyStages(final Configurator config, final Builder builder) {
        final List<String> entries = config.findEntries(POLICY_STAGE);
        for (final String raw : entries) {
            if (raw == null || raw.trim().isEmpty()) {
                throw new EmissaryRuntimeException("Invalid " + POLICY_STAGE + ": empty entry");
            }
            final String entry = raw.trim();
            final String[] parts = entry.split("\\s+", 2);
            final String type = parts[0].toLowerCase(Locale.ROOT);
            if ("rule".equals(type)) {
                if (parts.length < 2) {
                    throw new EmissaryRuntimeException("Invalid " + POLICY_STAGE + " \"" + entry + "\": rule body is empty");
                }
                builder.rule(parts[1].trim());
            } else if ("filter".equals(type) || "class".equals(type)) {
                if (parts.length < 2) {
                    throw new EmissaryRuntimeException("Invalid " + POLICY_STAGE + " \"" + entry + "\": filter class is empty");
                }
                builder.stage(createFilter(parts[1].trim(), config));
            } else {
                throw new EmissaryRuntimeException("Invalid " + POLICY_STAGE + " \"" + entry
                        + "\": expected 'rule ...' or 'filter ...'");
            }
        }
    }

    private static OutputFilter createFilter(final String className, final Configurator config) {
        final Object instance;
        try {
            instance = emissary.core.Factory.create(className);
        } catch (RuntimeException | AssertionError e) {
            throw new EmissaryRuntimeException("Unable to create policy filter " + className, e);
        }
        if (!(instance instanceof OutputFilter)) {
            throw new EmissaryRuntimeException("Policy filter " + className + " does not implement " + OutputFilter.class.getName());
        }
        final OutputFilter filter = (OutputFilter) instance;
        filter.configure(config);
        return filter;
    }

    /** Internal builder; each rule remains individually identifiable in evaluation and traces. */
    static final class Builder {
        private final List<OutputFilter> stages = new ArrayList<>();
        private Outcome defaultOutcome = Outcome.ALLOW;
        private EmitMode emitMode = EmitMode.BOTH;
        private int ruleIndex;

        static Builder create() {
            return new Builder();
        }

        Builder defaults(final Outcome outcome) {
            this.defaultOutcome = Objects.requireNonNull(outcome, "outcome");
            return this;
        }

        Builder emit(final EmitMode mode) {
            this.emitMode = Objects.requireNonNull(mode, "mode");
            return this;
        }

        Builder stage(final OutputFilter filter) {
            this.stages.add(Objects.requireNonNull(filter, "filter"));
            return this;
        }

        Builder rule(final String text) {
            return stage(new RuleStage(Collections.singletonList(RuleParser.parse(text, ++this.ruleIndex))));
        }

        OutputPolicy build() {
            return new OutputPolicy(this.stages, this.defaultOutcome, this.emitMode);
        }
    }
}
