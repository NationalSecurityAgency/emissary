package emissary.roll;

import java.io.Closeable;

/**
 * Interface for objects that need to take action on various intervals.
 */
public interface Rollable extends Closeable {
    /**
     * Rolls selected object. Operations should be timely in nature to prevent Agent threads from blocking if internal
     * implementations operate in that fashion.
     */
    void roll();

    /**
     * True if this object is currently rolling, false otherwise. This method should be thread safe.
     * 
     * @return true if this object is currently rolling; false otherwise
     */
    boolean isRolling();
}
