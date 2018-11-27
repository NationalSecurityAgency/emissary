package emissary.output.filter;

import emissary.config.Configurator;
import emissary.core.EmissaryException;
import emissary.util.io.FileNameGenerator;

import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Interface for drop off filter.
 */
public interface IDropOffFilter {

    /** Successful filter return value {@value} */
    int STATUS_FAILURE = -1;

    /** Failed writing to filter's target output stream {@value} */
    int STATUS_OUTPUT_STREAM_FAILURE = -2;

    /** Failed filter return value {@value} */
    int STATUS_SUCCESS = 1;

    /** The required output config directory for all filters */
    String OUTPUT_PATH = "OUTPUT_PATH";

    /**
     * Used in params when filter should understand that the List of incoming records is presorted value is {@value}
     */
    String PRE_SORTED = "PRE_SORTED_RECORDS";

    /**
     * Used to param the TLD to the filter that don't get the whole list
     */
    String TLD_PARAM = "TLD";


    /**
     * Return the path of this filter
     */
    Path getOutputPath();

    /**
     * Return the file extension for this filter
     */
    String getFileExtension();

    /**
     * Return the name of this filter
     */
    String getFilterName();

    /**
     * Set the name of the filter
     */
    void setFilterName(String s);

    /**
     * Return a filename generator for this filter
     */
    boolean hasFileNameGenerator();

    /**
     * Returns a Filenamegenerator for the filter. Warning, this method can return null. hasFileNameGenerator should
     * proceed this method.
     */
    FileNameGenerator getFileNameGenerator();

    void setFileNameGenerator(FileNameGenerator fng);

    /**
     * Initialization phase hook for the filter using default preferences for the runtime filter configuration
     * 
     * @param configG passed in configuration object, usually DropOff's
     * @param filterName the configured name of this filter or null for the default
     */
    void initialize(Configurator configG, String filterName) throws EmissaryException;

    /**
     * Initialization phase hook for the filter
     * 
     * @param configG passed in configuration object, usually DropOff's
     * @param filterName the configured name of this filter or null for the default
     * @param filterConfig configuration for specific runtime filter
     */
    void initialize(Configurator configG, String filterName, Configurator filterConfig) throws EmissaryException;

    /**
     * Run the filter for a document
     * 
     * @param d the document
     * @param params map of params
     * @return status value
     */
    int filter(emissary.core.IBaseDataObject d, Map<String, Object> params);

    /**
     * Run the filter for a set of documents
     * 
     * @param list collection of IBaseDataObject to run the filter on
     * @param params map of params
     * @return status value
     */
    int filter(List<emissary.core.IBaseDataObject> list, Map<String, Object> params);

    /**
     * Run the filter for a document
     * 
     * @param d the document
     * @param params map of params
     * @param output the output stream to log the data onto
     * @return status value
     */
    int filter(emissary.core.IBaseDataObject d, Map<String, Object> params, OutputStream output);

    /**
     * Run the filter for a set of documents
     * 
     * @param list collection of IBaseDataObject to run the filter on
     * @param params map of params
     * @param output the output stream to log the data onto
     * @return status value
     */
    int filter(List<emissary.core.IBaseDataObject> list, Map<String, Object> params, OutputStream output);

    /**
     * Determine if the payload is outputtable by the filter
     * 
     * @param d the document
     * @param params map of params
     * @return true if the filter wants a crack at outputting this payload
     */
    boolean isOutputtable(emissary.core.IBaseDataObject d, Map<String, Object> params);

    /**
     * Determine if the payload list is outputtable by the filter
     * 
     * @param list collection of IBaseDataObject to check for outputtability
     * @param params map of params
     * @return true if the filter wants a crack at outputting this payload
     */
    boolean isOutputtable(List<emissary.core.IBaseDataObject> list, Map<String, Object> params);

    /**
     * Determine if the payload is outputtable by the filter
     * 
     * @param d the IBaseDataObject to check for outputtability
     * @return true if the filter will attempt to output this payload
     */
    @Deprecated
    boolean isOutputtable(emissary.core.IBaseDataObject d);

    /**
     * Determine if the payload list is outputtable by the filter
     * 
     * @param list collection of IBaseDataObject to check for outputtability
     * @return true if the filter will attempt to output these payload
     */
    boolean isOutputtable(List<emissary.core.IBaseDataObject> list);

    /**
     * Close the filter
     */
    void close();

    /**
     * Get the output spec as built
     */
    String getOutputSpec();

    /**
     * Get the error spec as built
     */
    String getErrorSpec();

    /**
     * Get the set of configured output types
     */
    Collection<String> getOutputTypes();
}
