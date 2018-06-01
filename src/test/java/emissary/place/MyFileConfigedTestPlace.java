package emissary.place;

import java.io.IOException;
import java.io.InputStream;

import emissary.core.IBaseDataObject;

public class MyFileConfigedTestPlace extends ServiceProviderPlace {

    boolean finishedSuperConstructor = false;

    int processCounter = 0;

    public MyFileConfigedTestPlace(String placeLocation) throws IOException {
        super(placeLocation);
        finishedSuperConstructor = true;
    }

    public MyFileConfigedTestPlace(String configFile, String dir, String placeLocation) throws IOException {
        super(configFile, dir, placeLocation);
        finishedSuperConstructor = true;
    }

    public MyFileConfigedTestPlace(InputStream configStream, String dir, String placeLocation) throws IOException {
        super(configStream, dir, placeLocation);
        finishedSuperConstructor = true;
    }


    public boolean getFinishedSuperConstructor() {
        return finishedSuperConstructor;
    }

    @Override
    public void process(IBaseDataObject payload) {
        processCounter++;
    }
}
