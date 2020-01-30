package emissary.pickup;

import emissary.core.IPausable;
import emissary.util.ClassComparator;

public interface IPickUp extends IPausable {

    /**
     * Shutdown the pickup service
     */
    void shutDown();

    /**
     * Test to see if supplied object implements IPickUp
     *
     * @param clazz the class object to test
     * @return true if implements IPickUp, false otherwise
     */
    static boolean isImplementation(Class<? extends Object> clazz) {
        return ClassComparator.isaImplementation(clazz, IPickUp.class);
    }
}
