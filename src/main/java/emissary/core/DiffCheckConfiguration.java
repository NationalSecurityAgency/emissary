package emissary.core;

import java.util.Arrays;
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
        DATA, TIMESTAMP, INTERNAL_ID, TRANSFORM_HISTORY
    }

    /**
     * Stateful field of 'enabled' options
     */
    private final EnumSet<DiffCheckOptions> enabled;

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
        return new DiffCheckConfiguration(EnumSet.of(DiffCheckOptions.DATA));
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
    private DiffCheckConfiguration(final EnumSet<DiffCheckOptions> enabled) {
        this.enabled = enabled;
    }

    /**
     * Builder class for {@link DiffCheckConfiguration}
     */
    public static class DiffCheckBuilder {

        /**
         * Internal state whilst building
         */
        private EnumSet<DiffCheckOptions> building;

        /**
         * Finish building and create the final DiffCheckConfiguration object
         * 
         * @return a new Configuration instance with the enabled options
         */
        public DiffCheckConfiguration build() {
            return new DiffCheckConfiguration(building);
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
            return build();
        }

        /**
         * Private constructor
         */
        private DiffCheckBuilder() {
            building = EnumSet.noneOf(DiffCheckOptions.class);
        }

        /**
         * Reset the list of enabled options
         * 
         * @return the builder
         */
        public DiffCheckBuilder reset() {
            building.clear();
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
    }
}
