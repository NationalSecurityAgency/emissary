package emissary.output.filter;

import java.util.List;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;

/**
 * Defines a simple interface for accepting a filter condition
 */
public interface IFilterCondition {
    /**
     * Should a payload be accepted for filtering
     * 
     * @param payload the payload to test
     * @return true if the payload should be filtered, false otherwise
     */
    boolean accept(IBaseDataObject payload);

    /**
     * Should a list of payloads be accepted for filtering
     * 
     * @param payloads the payloads to test
     * @return true if the payload should be filtered, false otherwise
     */
    boolean accept(List<IBaseDataObject> payloads);

    /**
     * Initialize the IFilterCondition using the specified Configurator
     * 
     * @param configG the Configurator to use to initialize the IFilterCondition
     */
    void initialize(Configurator configG);
}
