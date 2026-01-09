package emissary.core;

import jakarta.annotation.Nullable;

/**
 * Information class about processing stages in the workflow
 */
enum Stage {

    /*
     * The order of these stage declarations is important, as the order of the declarations directly corresponds to the
     * stage sequence order
     */

    /** Prepare, coordinate idents */
    STUDY(false),
    /** Identification phase */
    ID(false),
    /** Coordinate processing */
    COORDINATE(false),
    /** Before transform hook */
    PRETRANSFORM(true),
    /** Transformation phase */
    TRANSFORM(false),
    /** After transform hook */
    POSTTRANSFORM(true),
    /** Analysis/metadata generation */
    ANALYZE(true),
    /** Verify prior to emitting output */
    VERIFY(false),
    /** Emit output */
    IO(false),
    /** Finish */
    REVIEW(false);

    final boolean isParallel;

    /**
     * Constructor, which specifies whether the specific stage support parallel operations
     *
     * @param isParallel flag indicating parallel processing support
     */
    Stage(boolean isParallel) {
        this.isParallel = isParallel;
    }

    /**
     * Indicates whether the specific stage support parallel operations
     *
     * @return isParallel indicator
     */
    public boolean isParallel() {
        return isParallel;
    }

    /**
     * Attempts to resolve the stage value by name
     *
     * @param name value to look up
     * @return resolved Stage name or null if no matching value was found
     */
    @Nullable
    public static Stage getByName(String name) {
        try {
            return Stage.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    /**
     * Attempts to resolve the stage value by ordinal position
     *
     * @param index value to look up
     * @return resolved Stage, or null if no matching value was found
     */
    @Nullable
    public static Stage getByOrdinal(final int index) {
        if (index < 0 || index >= Stage.values().length) {
            return null;
        }
        return Stage.values()[index];
    }

    /**
     * Attempts to resolve the stage value by ordinal position
     *
     * @param index value to look up
     * @return resolved Stage name or null if no matching value was found
     */

    public static String getStageName(final int index) {
        Stage stage = getByOrdinal(index);
        return stage == null ? "UNDEFINED" : stage.name();
    }

    /**
     * Return the stage following the provided stage
     *
     * @param current current stage
     * @return the Stage following the current stage or null there is no next value
     */
    @SuppressWarnings("EnumOrdinal")
    public static Stage nextStageAfter(final Stage current) {
        final int nextIndex = current.ordinal() + 1;
        return getByOrdinal(nextIndex);
    }

    /**
     * Return the stage following the named stage
     *
     * @param name stage to look up
     * @return the Stage following the name stage or null there is no next value
     */
    @Nullable
    @SuppressWarnings("EnumOrdinal")
    public static Stage nextStageAfter(final String name) {
        Stage current = Stage.getByName(name);
        if (current == null) {
            return null;
        }
        final int nextIndex = current.ordinal() + 1;
        return getByOrdinal(nextIndex);
    }

    /**
     * Resolves a Stage from a given ordinal position and indicates whether is supports parallel operations
     *
     * @param index ordinal position for the Stage
     * @return value indicating whether the Stage supports parallel processing. Returns false if the specified index is
     *         invalid.
     */
    public static boolean isParallelStage(final int index) {
        Stage stage = getByOrdinal(index);
        return stage != null && stage.isParallel();
    }

}
