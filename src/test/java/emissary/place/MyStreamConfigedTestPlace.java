package emissary.place;

import emissary.core.IBaseDataObject;

import java.io.IOException;
import java.io.InputStream;

public class MyStreamConfigedTestPlace extends ServiceProviderPlace {

    boolean finishedSuperConstructor = false;

    int processCounter = 0;

    public MyStreamConfigedTestPlace(String placeLocation) throws IOException {
        super(placeLocation);
        finishedSuperConstructor = true;
    }

    public MyStreamConfigedTestPlace(String configFile, String dir, String placeLocation) throws IOException {
        super(configFile, dir, placeLocation);
        finishedSuperConstructor = true;
    }

    public MyStreamConfigedTestPlace(InputStream configStream, String dir, String placeLocation) throws IOException {
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
