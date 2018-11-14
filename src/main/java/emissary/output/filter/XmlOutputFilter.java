package emissary.output.filter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.IBaseDataObject;
import emissary.output.DropOffPlace;
import emissary.util.PayloadUtil;

/**
 * Filter that writes unadorned data as utf-8.
 */
public class XmlOutputFilter extends AbstractRollableFilter {

    /**
     * Initialize reads the configuration items for this filter
     * 
     * @param configG the configurator to read from
     * @param filterName the configured name of this filter or null for the default
     * @param filterConfig the configuration for the specific filter
     */
    @Override
    public void initialize(Configurator configG, String filterName, Configurator filterConfig) {
        if (filterName == null) {
            setFilterName("XML");
        }
        super.initialize(configG, filterName, filterConfig);
        this.appendNewLine = false;
    }

    @Override
    public byte[] convert(final List<IBaseDataObject> list, final Map<String, Object> params) throws IOException {
        return PayloadUtil.toXmlString(list).getBytes();
    }

    /**
     * Main to test output types
     */
    public static void main(String[] args) throws IOException {
        String name = args.length > 0 ? args[0] : null;

        XmlOutputFilter filter = new XmlOutputFilter();
        try {
            Configurator config = ConfigUtil.getConfigInfo(DropOffPlace.class);
            filter.initialize(config, name);
            System.out.println("Output types " + filter.outputTypes);
        } catch (Exception ex) {
            System.err.println("Cannot configure filter: " + ex.getMessage());
        }
    }
}
