package emissary.place;

import emissary.core.IBaseDataObject;

import java.io.IOException;
import java.io.InputStream;

public class OOMPlace extends ServiceProviderPlace {
    public OOMPlace(String configInfo, String dir, String placeLoc) throws IOException {
        super(configInfo, dir, placeLoc);
    }

    @SuppressWarnings("PMD.UnusedFormalParameter")
    public OOMPlace(InputStream configInfo, String dir, String placeLoc) throws IOException {
        super((String) null, dir, placeLoc);
    }

    public OOMPlace(String configInfo) throws IOException {
        super(configInfo, "OOMPlace.www.example.com:8001");
    }

    @Override
    public void process(IBaseDataObject theDataObject) {
        throw new OutOfMemoryError("This is a fake out of memory error");
    }
}
