package emissary.jni;

import emissary.place.IServiceProviderPlace;

import java.rmi.RemoteException;

public interface IJniRepositoryPlace extends IServiceProviderPlace {

    boolean nativeLibraryQuery(String query);

    byte[] nativeLibraryDeliver(String query) throws RemoteException;

    long lastModified(String query);
}
