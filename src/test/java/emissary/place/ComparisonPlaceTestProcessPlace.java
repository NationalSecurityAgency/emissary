package emissary.place;

import emissary.core.IBaseDataObject;
import emissary.core.ResourceException;

import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;

public class ComparisonPlaceTestProcessPlace extends ServiceProviderPlace {
    private final Map<String, String> metadataKeyValueMap;

    public ComparisonPlaceTestProcessPlace(String configFile, String theDir, String thePlaceLocation) throws IOException {
        super(configFile, theDir, thePlaceLocation);

        metadataKeyValueMap = configG.findStringMatchMap("METADATA_");
    }

    @Override
    public void process(IBaseDataObject ibdo) throws ResourceException {
        for (Entry<String, String> entry : metadataKeyValueMap.entrySet()) {
            ibdo.appendParameter(entry.getKey(), entry.getValue());
        }
    }
}
