package emissary.output.filter;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.IBaseDataObject;
import emissary.output.DropOffPlace;

/**
 * Filter that writes unadorned data as raw bytes
 */
public class DataFilter extends AbstractFilter {

    /**
     * Initialize reads the configuration items for this filter
     *
     * @param configG the configurator to read from
     * @param filterName the configured name of this filter or null for the default
     * @param filterConfig the configuration for the specific filter
     */
    @Override
    public void initialize(final Configurator configG, final String filterName, final Configurator filterConfig) {
        if (filterName == null) {
            setFilterName("DATA");
        }
        super.initialize(configG, filterName, filterConfig);
    }

    /**
     * Output one payload item
     *
     * @param d the payload
     * @param params the map of configuration items
     */
    @Override
    public int filter(final IBaseDataObject d, final Map<String, Object> params) {
        final Set<String> checkTypes = getTypesToCheck(d);
        final IBaseDataObject tld = (IBaseDataObject) params.get(IDropOffFilter.TLD_PARAM);
        if (!isOutputtable(checkTypes)) {
            logger.debug("Skipping " + d.shortName() + " is not outputtable by this filter on types " + checkTypes);
            return IDropOffFilter.STATUS_SUCCESS;
        }


        // Set up base location from configured spec
        final String baseFileName = dropOffUtil.getPathFromSpec(outputSpec, d, tld);
        final String fileType = dropOffUtil.getFileType(d.getCookedParameters());
        final String currentForm = d.currentForm();
        getCharset(d, "UTF-8");
        final String lang = dropOffUtil.getLanguage(d);

        int writeCount = 0;

        if (isPrimaryViewOutputtable(lang, fileType, currentForm)) {
            final boolean status = writeDataFile(d, tld, baseFileName, () -> Channels.newInputStream(d.getDataContainer().channel()), null);
            writeCount += (status ? 1 : -1);
        }

        // Check each alt view
        for (final String viewName : d.getAlternateViewNames()) {
            if (isViewOutputtable(lang, fileType, currentForm, viewName)) {
                final String fixedViewName = viewName.replace(" ", "_");
                final boolean status =
                        writeDataFile(d, tld, baseFileName, () -> new ByteArrayInputStream(d.getAlternateView(viewName)), fixedViewName);
                writeCount += (status ? 1 : -1);
            }
        }

        return (writeCount >= 0) ? IDropOffFilter.STATUS_SUCCESS : IDropOffFilter.STATUS_FAILURE;
    }

    /**
     * Output one payload item to the provided output stream
     *
     * @param d the payload
     * @param params the map of configuration items
     * @param output the output stream to write to
     */
    @Override
    public int filter(final IBaseDataObject d, final Map<String, Object> params, final OutputStream output) {
        final Set<String> checkTypes = getTypesToCheck(d);
        final IBaseDataObject tld = (IBaseDataObject) params.get(IDropOffFilter.TLD_PARAM);
        if (!isOutputtable(checkTypes)) {
            logger.debug("Skipping " + d.shortName() + " is not outputtable by this filter on types " + checkTypes);
            return IDropOffFilter.STATUS_SUCCESS;
        }

        // Set up base location from configured spec
        final String fileType = dropOffUtil.getFileType(d.getCookedParameters());
        final String currentForm = d.currentForm();
        final String lang = dropOffUtil.getLanguage(d);

        int writeCount = 0;

        if (isPrimaryViewOutputtable(lang, fileType, currentForm)) {
            final boolean status = writeDataStream(d, tld, output, () -> Channels.newInputStream(d.getDataContainer().channel()), null);
            writeCount += (status ? 1 : -1);
        }

        // Check each alt view
        for (final String viewName : d.getAlternateViewNames()) {
            if (isViewOutputtable(lang, fileType, currentForm, viewName)) {
                final String fixedViewName = viewName.replace(" ", "_");
                final boolean status = writeDataStream(d, tld, output, () -> new ByteArrayInputStream(d.getAlternateView(viewName)), fixedViewName);
                writeCount += (status ? 1 : -1);
            }
        }

        return writeCount >= 0 ? IDropOffFilter.STATUS_SUCCESS : IDropOffFilter.STATUS_FAILURE;
    }

    protected boolean isPrimaryViewOutputtable(final String lang, final String fileType, final String currentForm) {
        return isOutputtable(lang + AbstractFilter.LANGUAGE_VIEW) || isOutputtable(lang + AbstractFilter.LANGUAGE_VIEW + AbstractFilter.PRIMARY_VIEW)
                || isOutputtable(fileType) || isOutputtable(fileType + AbstractFilter.PRIMARY_VIEW)
                || (isOutputtable(AbstractFilter.ALL_LANGUAGE_VIEWS) && !"NONE".equals(lang)) || isOutputtable(AbstractFilter.ALL_PRIMARY_VIEWS)
                || isOutputtable(currentForm) || isOutputtable(currentForm + AbstractFilter.PRIMARY_VIEW) || isOutputtable(lang + "." + fileType)
                || isOutputtable(lang + "." + fileType + AbstractFilter.PRIMARY_VIEW) || isOutputtable(lang + "." + currentForm)
                || isOutputtable(lang + "." + currentForm + AbstractFilter.PRIMARY_VIEW);
    }

    protected boolean isViewOutputtable(final String lang, final String fileType, final String currentForm, final String viewName) {
        return isOutputtable(lang + AbstractFilter.LANGUAGE_VIEW) || isOutputtable(lang + AbstractFilter.LANGUAGE_VIEW + "." + viewName)
                || isOutputtable(fileType) || isOutputtable(fileType + "." + viewName)
                || (isOutputtable(AbstractFilter.ALL_LANGUAGE_VIEWS) && !"NONE".equals(lang)) || isOutputtable(AbstractFilter.ALL_ALT_VIEWS)
                || isOutputtable(currentForm) || isOutputtable(currentForm + "." + viewName) || isOutputtable(lang + "." + fileType)
                || isOutputtable(lang + "." + fileType + "." + viewName) || isOutputtable(lang + "." + currentForm)
                || isOutputtable(lang + "." + currentForm + "." + viewName);
    }

    /**
     * Write a file, either the primary view or an alt view
     *
     * @param d the DataObject to output
     * @param tld the TLD Object to extract metadata (if applicable)
     * @param baseFileName the base file name
     * @param data the bytes to write
     * @param type of data
     */
    protected boolean writeDataFile(final IBaseDataObject d, final IBaseDataObject tld, final String baseFileName, InputStreamSupplier data,
            final String type) {
        String fileName = baseFileName;
        if (type != null) {
            fileName += "." + type;
        }

        if (!dropOffUtil.setupPath(fileName)) {
            logger.error("Cannot setup path for " + fileName);
            return false;
        }

        if (!dropOffUtil.removeExistingFile(fileName)) {
            logger.error("Cannot remove existing file at " + fileName);
            return false;
        }

        // Write it out
        try (FileOutputStream fos = new FileOutputStream(fileName);
                InputStream is = data.get()) {
            IOUtils.copyLarge(is, fos);
        } catch (IOException ex) {
            logger.error("Cannot write output to " + fileName, ex);
            return false;
        }

        return true;
    }

    protected boolean writeDataStream(final IBaseDataObject d, final IBaseDataObject tld, final OutputStream output, InputStreamSupplier data,
            final String type) {
        try (InputStream is = data.get()) {
            IOUtils.copyLarge(is, output);
        } catch (IOException ex) {
            logger.error("Cannot write output", ex);
            return false;
        }
        return true;
    }

    /**
     * Main to test output types
     */
    public static void main(final String[] args) throws IOException {
        final String name = args.length > 0 ? args[0] : null;

        final DataFilter filter = new DataFilter();
        try {
            final Configurator config = ConfigUtil.getConfigInfo(DropOffPlace.class);
            filter.initialize(config, name);
            System.out.println("Output types " + filter.outputTypes);
        } catch (Exception ex) {
            System.err.println("Cannot configure filter: " + ex.getMessage());
        }
    }

    @FunctionalInterface
    private interface InputStreamSupplier {
        InputStream get() throws IOException;
    }
}
