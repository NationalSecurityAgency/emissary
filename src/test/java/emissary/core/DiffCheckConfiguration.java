package emissary.core;

import jakarta.annotation.Nullable;
import org.jdom2.Element;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Utility class to help simplify configuration of {@link IBaseDataObjectDiffHelper}
 */
public class DiffCheckConfiguration {
    /**
     * Possible configuration options
     */
    public enum DiffCheckOptions {
        DATA, TIMESTAMP, INTERNAL_ID, TRANSFORM_HISTORY, KEY_VALUE_PARAMETER_DIFF, DETAILED_PARAMETER_DIFF
    }

    /**
     * Stateful field of 'enabled' options
     */
    private final Set<DiffCheckOptions> enabled;

    /**
     * Flag indicating if the verification is in strict mode.
     */
    private final boolean strict;

    /**
     * The JDOM element containing expected values for lenient checks.
     */
    private final Element lenientExpectationElement;

    /**
     * Start building a new configuration
     * 
     * @return a new builder instance
     */
    public static DiffCheckConfiguration.DiffCheckBuilder configure() {
        return new DiffCheckConfiguration.DiffCheckBuilder();
    }

    /**
     * Helper method to simplify only enable checking of data
     * 
     * @return a new config instance which only enables checking data
     */
    public static DiffCheckConfiguration onlyCheckData() {
        return new DiffCheckConfiguration(EnumSet.of(DiffCheckOptions.DATA), true, null);
    }

    /**
     * Check if the configuration is in strict mode
     *
     * @return if strict mode is enabled
     */
    public boolean isStrict() {
        return this.strict;
    }

    /**
     * Accessor for the lenient expectation XML element
     *
     * @return the lenient expectation element
     */
    public Element getLenientExpectationElement() {
        return this.lenientExpectationElement;
    }

    /**
     * Check if data should be diffed
     * 
     * @return if checking data is enabled
     */
    public boolean checkData() {
        return enabled.contains(DiffCheckOptions.DATA);
    }

    /**
     * Check if the timestamp should be diffed
     * 
     * @return if checking timestamps is enabled
     */
    public boolean checkTimestamp() {
        return enabled.contains(DiffCheckOptions.TIMESTAMP);
    }

    /**
     * Check if the internal ID should be diffed
     * 
     * @return if checking the internal ID is enabled
     */
    public boolean checkInternalId() {
        return enabled.contains(DiffCheckOptions.INTERNAL_ID);
    }

    /**
     * Check if the transform history should be diffed
     * 
     * @return if checking the transform history is enabled
     */
    public boolean checkTransformHistory() {
        return enabled.contains(DiffCheckOptions.TRANSFORM_HISTORY);
    }

    /**
     * Check if parameter diff should produce detailed output
     * 
     * @return if performing a detailed parameter diff
     */
    public boolean performDetailedParameterDiff() {
        return enabled.contains(DiffCheckOptions.DETAILED_PARAMETER_DIFF);
    }

    /**
     * Check if parameter diff should produce non-matching key/value output
     * 
     * @return if performing a key/value parameter diff
     */
    public boolean performKeyValueParameterDiff() {
        return enabled.contains(DiffCheckOptions.KEY_VALUE_PARAMETER_DIFF);
    }

    /**
     * Accessor for enabled options
     * 
     * @return the enabled options
     */
    public Set<DiffCheckOptions> getEnabled() {
        return enabled;
    }

    /**
     * Private constructor to skip builder pattern
     * 
     * @param enabled set of pre-configured options
     */
    private DiffCheckConfiguration(final EnumSet<DiffCheckOptions> enabled, final boolean strict,
            @Nullable final Element lenientExpectationElement) {
        this.enabled = Collections.unmodifiableSet(enabled);
        this.strict = strict;
        this.lenientExpectationElement = lenientExpectationElement;
    }

    /**
     * Creates a builder pre-populated with the settings of an existing configuration.
     *
     * @param prototype The configuration instance to copy from.
     * @return A DiffCheckBuilder primed with the prototype's settings.
     */
    public static DiffCheckBuilder from(final DiffCheckConfiguration prototype) {
        return new DiffCheckBuilder(prototype);
    }

    /**
     * Builder class for {@link DiffCheckConfiguration}
     */
    public static class DiffCheckBuilder {

        /**
         * Internal state whilst building
         */
        private final EnumSet<DiffCheckOptions> building;

        /**
         * Internal strict state tracking
         */
        private boolean strict = true;

        /**
         * Internal element tracking for lenient modes
         */
        @Nullable
        private Element lenientExpectationElement = null;

        /**
         * Set strict processing mode behavior
         *
         * @param strict true to execute exact-match tracking, false for lenient checks
         * @return the builder
         */
        public DiffCheckBuilder setStrict(boolean strict) {
            this.strict = strict;
            return this;
        }

        /**
         * Set the underlying template element to evaluate lenient parameter rules
         *
         * @param element the source XML mapping element
         * @return the builder
         */
        public DiffCheckBuilder setLenientExpectationElement(Element element) {
            this.lenientExpectationElement = element;
            /* Setting a lenient expectation element implies lenient mode */
            this.strict = false;
            return this;
        }

        /**
         * Finish building and create the final DiffCheckConfiguration object
         * 
         * @return a new Configuration instance with the enabled options
         */
        public DiffCheckConfiguration build() {
            return new DiffCheckConfiguration(building, strict, lenientExpectationElement);
        }

        /**
         * Provide explicit list of options to enable
         * 
         * @param options to enable explicitly
         * @return a new Configuration instance with the enabled options
         */
        public DiffCheckConfiguration explicit(final DiffCheckOptions... options) {
            reset();
            building.addAll(Arrays.asList(options));

            if (building.contains(DiffCheckOptions.DETAILED_PARAMETER_DIFF) && building.contains(DiffCheckOptions.KEY_VALUE_PARAMETER_DIFF)) {
                throw new IllegalArgumentException("Cannot contain DETAILED_PARAMETER_DIFF and KEY_VALUE_PARAMETER_DIFF!");
            }

            return build();
        }

        /**
         * Private constructor
         */
        private DiffCheckBuilder() {
            building = EnumSet.noneOf(DiffCheckOptions.class);
        }

        /**
         * Public/Package-private constructor to build from an existing configuration
         *
         * @param configuration the configuration to duplicate state from
         */
        private DiffCheckBuilder(final DiffCheckConfiguration configuration) {
            this.building = EnumSet.noneOf(DiffCheckOptions.class);

            if (configuration.checkData()) {
                this.building.add(DiffCheckOptions.DATA);
            }
            if (configuration.checkTimestamp()) {
                this.building.add(DiffCheckOptions.TIMESTAMP);
            }
            if (configuration.checkInternalId()) {
                this.building.add(DiffCheckOptions.INTERNAL_ID);
            }
            if (configuration.checkTransformHistory()) {
                this.building.add(DiffCheckOptions.TRANSFORM_HISTORY);
            }
            if (configuration.performDetailedParameterDiff()) {
                this.building.add(DiffCheckOptions.DETAILED_PARAMETER_DIFF);
            }
            if (configuration.performKeyValueParameterDiff()) {
                this.building.add(DiffCheckOptions.KEY_VALUE_PARAMETER_DIFF);
            }

            this.strict = configuration.isStrict();
            this.lenientExpectationElement = configuration.getLenientExpectationElement();
        }

        /**
         * Reset the list of enabled options
         * 
         * @return the builder
         */
        public DiffCheckBuilder reset() {
            building.clear();
            this.strict = true;
            this.lenientExpectationElement = null;
            return this;
        }

        /**
         * Enable data for diff checking
         * 
         * @return the builder
         */
        public DiffCheckBuilder enableData() {
            building.add(DiffCheckOptions.DATA);
            return this;
        }

        /**
         * Disable data for diff checking
         * 
         * @return the builder
         */
        public DiffCheckBuilder disableData() {
            building.remove(DiffCheckOptions.DATA);
            return this;
        }

        /**
         * Enable timestamp for diff checking
         * 
         * @return the builder
         */
        public DiffCheckBuilder enableTimestamp() {
            building.add(DiffCheckOptions.TIMESTAMP);
            return this;
        }

        /**
         * Disable timestamp for diff checking
         * 
         * @return the builder
         */
        public DiffCheckBuilder disableTimestamp() {
            building.remove(DiffCheckOptions.TIMESTAMP);
            return this;
        }

        /**
         * Enable internal ID for diff checking
         * 
         * @return the builder
         */
        public DiffCheckBuilder enableInternalId() {
            building.add(DiffCheckOptions.INTERNAL_ID);
            return this;
        }

        /**
         * Disable internal ID for diff checking
         * 
         * @return the builder
         */
        public DiffCheckBuilder disableInternalId() {
            building.remove(DiffCheckOptions.INTERNAL_ID);
            return this;
        }

        /**
         * Enable transform history for diff checking
         * 
         * @return the builder
         */
        public DiffCheckBuilder enableTransformHistory() {
            building.add(DiffCheckOptions.TRANSFORM_HISTORY);
            return this;
        }

        /**
         * Disable transform history for diff checking
         * 
         * @return the builder
         */
        public DiffCheckBuilder disableTransformHistory() {
            building.remove(DiffCheckOptions.TRANSFORM_HISTORY);
            return this;
        }

        /**
         * Enable transform history for diff checking
         * 
         * @return the builder
         */
        public DiffCheckBuilder enableKeyValueParameterDiff() {
            building.add(DiffCheckOptions.KEY_VALUE_PARAMETER_DIFF);
            building.remove(DiffCheckOptions.DETAILED_PARAMETER_DIFF);
            return this;
        }

        /**
         * Disable transform history for diff checking
         * 
         * @return the builder
         */
        public DiffCheckBuilder disableKeyValueParameterDiff() {
            building.remove(DiffCheckOptions.KEY_VALUE_PARAMETER_DIFF);
            return this;
        }

        /**
         * Enable transform history for diff checking
         * 
         * @return the builder
         */
        public DiffCheckBuilder enableDetailedParameterDiff() {
            building.add(DiffCheckOptions.DETAILED_PARAMETER_DIFF);
            building.remove(DiffCheckOptions.KEY_VALUE_PARAMETER_DIFF);
            return this;
        }

        /**
         * Disable transform history for diff checking
         * 
         * @return the builder
         */
        public DiffCheckBuilder disableDetailedParameterDiff() {
            building.remove(DiffCheckOptions.DETAILED_PARAMETER_DIFF);
            return this;
        }
    }
}
