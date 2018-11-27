package emissary.output.filter;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.IBaseDataObject;
import emissary.directory.DirectoryEntry;
import emissary.output.DropOffUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reusable routines for Filter classes
 */
public class FilterUtil {

    protected static final Logger LOG = LoggerFactory.getLogger(FilterUtil.class);

    /**
     * Start up the requested filters
     * 
     * @param filterClasses the name:class values of the configured filters for this drop off
     * @param configG configuration object
     * @return
     */
    public static List<IDropOffFilter> initializeFilters(final List<String> filterClasses, Configurator configG) {
        ArrayList<IDropOffFilter> filters = new ArrayList<>();
        for (final String entry : filterClasses) {
            final String name;
            final String clazz;
            Configurator filterConfig = null;
            final int colpos = entry.indexOf(':');
            if (colpos > -1) {
                name = entry.substring(0, colpos);
                clazz = entry.substring(colpos + 1);
                final String filterConfigName = configG.findStringEntry(name);
                if (filterConfigName != null) {
                    try {
                        filterConfig = ConfigUtil.getConfigInfo(filterConfigName);
                    } catch (IOException configError) {
                        LOG.warn("Specified filter configuration {} cannot be loaded", filterConfigName, configError);
                        continue;
                    }
                }
            } else {
                name = null;
                clazz = entry;
            }

            try {
                final Object filter = emissary.core.Factory.create(clazz);
                if (filter != null && filter instanceof IDropOffFilter) {
                    final IDropOffFilter f = (IDropOffFilter) filter;
                    f.initialize(configG, name, filterConfig);
                    filters.add(f);
                } else {
                    LOG.error("Misconfigured filter {} is not an IDropOffFilter instance, ignoring it", clazz);
                }
            } catch (Exception ex) {
                LOG.error("Unable to create or initialize {}", clazz, ex);
            }
        }

        return filters;
    }

    public static void processData(final IBaseDataObject tData, final Set<String> serviceProxies, final DirectoryEntry de) {

        final StringBuilder poppedForms = new StringBuilder();

        String prevBin = "";

        // Write out data for all the destinations we area proxy for, popping
        // them off the stack as they are handled.

        final Set<String> cfSet = new HashSet<>();
        for (int i = 0; i < tData.currentFormSize(); i++) {
            final String cf = tData.currentFormAt(i);

            if (serviceProxies.contains(cf) || serviceProxies.contains("*")) {
                // Record extra drop offs in the transform history since
                // we are taking precedence over the normal agent/directory mechanism
                // Do this before popping the extra destination so that it
                // is not just lost forever. Since the agent appends
                // one entry to the history when it sends the agent here,
                // that's the top current form and
                // we don't want to duplicate that one

                if (!prevBin.equals(cf) && (i > 0) && !cfSet.contains(cf) && !("UNKNOWN".equals(cf) || cf.endsWith("-PROCESSED"))) {
                    // e.g.: [dataType].DROP_OFF.IO.host.dom:port/DropOffPlace
                    de.setDataType("[" + cf + "]");
                    tData.appendTransformHistory(de.getKey());
                }

                // Accumulate forms we have handled in poppedForms
                if (poppedForms.length() > 0) {
                    poppedForms.append(" ");
                }
                poppedForms.append(cf);
                cfSet.add(cf);

                prevBin = cf;
            }
        }

        // Record the list of forms
        tData.setParameter("POPPED_FORMS", poppedForms.toString());
    }

    /**
     * Prepare a list of payload object to be filtered. Sorts payloads by ID and preps some metadata using DropOffUtil.
     * 
     * @param payloadList the list of items that were eligible for output
     * @return metadata needed for the output filter
     * @param dropOffUtil
     */
    public static Map<String, Object> preFilter(final List<IBaseDataObject> payloadList, final DropOffUtil dropOffUtil) {
        // Sort the list of records
        final Map<String, Object> filterParams = new HashMap<>();
        Collections.sort(payloadList, new emissary.util.ShortNameComparator());
        filterParams.put(IDropOffFilter.PRE_SORTED, Boolean.TRUE);
        filterParams.put(IDropOffFilter.TLD_PARAM, payloadList.get(0));

        // Prepare the metadata
        dropOffUtil.processMetadata(payloadList);
        return filterParams;
    }
}
