package emissary.core;

public interface IPausable {

    /**
     * Stop taking work
     */
    default void pause() {}

    /**
     * Resume taking work
     */
    default void unpause() {}

    /**
     * Check to see if the current thread is paused
     *
     * @return true if work is paused, false otherwise
     */
    default boolean isPaused() {
        return false;
    }
}
