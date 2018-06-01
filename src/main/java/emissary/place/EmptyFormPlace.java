package emissary.place;

/**
 * Marker interface to force the framework to allow an empty current form stack after calling
 * IServiceProviderPlace#process without registering it as an error.
 */
public interface EmptyFormPlace extends IServiceProviderPlace {
}
