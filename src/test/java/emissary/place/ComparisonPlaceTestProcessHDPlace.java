package emissary.place;

import emissary.core.BaseDataObject;
import emissary.core.IBaseDataObject;
import emissary.core.ResourceException;

import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

public class ComparisonPlaceTestProcessHDPlace extends ServiceProviderPlace {
    private final ImmutableMap<String, String> metadataKeyValueMap;

    public ComparisonPlaceTestProcessHDPlace(String configFile, String theDir, String thePlaceLocation) throws IOException {
        super(configFile, theDir, thePlaceLocation);

        metadataKeyValueMap = ImmutableMap.copyOf(configG.findStringMatchMap("METADATA_"));
    }

    @Override
    public List<IBaseDataObject> processHeavyDuty(IBaseDataObject ibdo) throws ResourceException {
        final List<IBaseDataObject> attachments = new ArrayList<>();
        final IBaseDataObject childIbdo = new BaseDataObject();

        for (Entry<String, String> entry : metadataKeyValueMap.entrySet()) {
            ibdo.appendParameter(entry.getKey(), entry.getValue());
            childIbdo.appendParameter(entry.getKey(), entry.getValue());
        }

        attachments.add(childIbdo);

        return attachments;
    }
}
