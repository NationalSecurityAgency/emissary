package emissary.id;

import java.io.IOException;

/**
 * An interface between and Identification place and the engine that does the work
 */
public interface Engine {
    /**
     * (Re)configure the engine
     * 
     * @param config the configuration stream or resource
     */
    void reconfigure(emissary.config.Configurator config) throws IOException;

    /**
     * Determine if the engine is ready
     */
    boolean ready();

    /**
     * Identify a work unit
     * 
     * @param u the thing to be identified
     * @return identifcation holder that tells what was found
     */
    Identification identify(WorkUnit u);
}
