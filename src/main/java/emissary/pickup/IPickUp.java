package emissary.pickup;

import emissary.core.IPausable;

public interface IPickUp extends IPausable {

    /**
     * Shutdown the pickup service
     */
    void shutDown();

}
