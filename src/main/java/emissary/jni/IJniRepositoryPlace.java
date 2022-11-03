package emissary.jni;

import java.rmi.RemoteException;

import emissary.place.IServiceProviderPlace;

public interface IJniRepositoryPlace extends IServiceProviderPlace {

    public boolean nativeLibraryQuery(String query);

    public byte[] nativeLibraryDeliver(String query) throws RemoteException;

    public long lastModified(String query);
}
