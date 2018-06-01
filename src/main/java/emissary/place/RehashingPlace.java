package emissary.place;

/**
 * Marker interface to force ServiceProvider impl to recompute hash checksum after processing
 * 
 * @see emissary.place.ServiceProviderPlace#agentProcessCall(emissary.core.IBaseDataObject)
 */
public interface RehashingPlace extends IServiceProviderPlace {
}
