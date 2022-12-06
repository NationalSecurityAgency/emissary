package emissary.util;

import emissary.core.IBaseDataObject;
import emissary.core.ResourceException;
import emissary.place.ServiceProviderPlace;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

public class PlaceComparisonHelperTestPlace extends ServiceProviderPlace {
    public PlaceComparisonHelperTestPlace(final InputStream inputStream) throws IOException {
        super(inputStream);
    }

    public PlaceComparisonHelperTestPlace(final String configuration) throws IOException {
        super(configuration);
    }

    @Override
    public List<IBaseDataObject> processHeavyDuty(IBaseDataObject payload) throws ResourceException {
        return Collections.emptyList();
    }
}
