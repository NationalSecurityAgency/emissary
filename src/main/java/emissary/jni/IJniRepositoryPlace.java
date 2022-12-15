package emissary.jni;

import emissary.place.IServiceProviderPlace;

import java.rmi.RemoteException;

public interface IJniRepositoryPlace extends IServiceProviderPlace {

    public boolean nativeLibraryQuery(String query);

    public byte[] nativeLibraryDeliver(String query) throws RemoteException;

    public long lastModified(String query);
}
