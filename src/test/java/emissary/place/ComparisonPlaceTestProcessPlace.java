package emissary.place;

import emissary.core.IBaseDataObject;
import emissary.core.ResourceException;

import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.util.Map.Entry;

public class ComparisonPlaceTestProcessPlace extends ServiceProviderPlace {
    private final ImmutableMap<String, String> metadataKeyValueMap;

    public ComparisonPlaceTestProcessPlace(String configFile, String theDir, String thePlaceLocation) throws IOException {
        super(configFile, theDir, thePlaceLocation);

        metadataKeyValueMap = ImmutableMap.copyOf(configG.findStringMatchMap("METADATA_"));
    }

    @Override
    public void process(IBaseDataObject ibdo) throws ResourceException {
        for (Entry<String, String> entry : metadataKeyValueMap.entrySet()) {
            ibdo.appendParameter(entry.getKey(), entry.getValue());
        }
    }
}
