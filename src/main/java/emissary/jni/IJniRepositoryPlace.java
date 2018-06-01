package emissary.jni;

import java.rmi.RemoteException;

public interface IJniRepositoryPlace extends emissary.place.IServiceProviderPlace {

    public boolean nativeLibraryQuery(String query);

    public byte[] nativeLibraryDeliver(String query) throws RemoteException;

    public long lastModified(String query);
}
