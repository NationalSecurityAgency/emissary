package emissary.core;

import java.util.List;

public interface IBaseDataObject extends IBaseRecord {

    /**
     * Support deep copy via clone
     */
    @Deprecated
    IBaseDataObject clone() throws CloneNotSupportedException;

    /**
     * Get the List of extracted records
     */
    List<IBaseDataObject> getExtractedRecords();

    /**
     * Set/replace the list of extracted children
     *
     * @param records the list of extracted children
     */
    void setExtractedRecords(List<? extends IBaseDataObject> records);

    /**
     * Add an extracted child
     *
     * @param record the extracted child to add
     */
    void addExtractedRecord(IBaseDataObject record);

    /**
     * Add extracted children
     *
     * @param records the extracted children to add
     */
    void addExtractedRecords(List<? extends IBaseDataObject> records);

    /**
     * Determine if this object has extracted records.
     * 
     * @return true if this object has extracted records.
     */
    boolean hasExtractedRecords();

    /**
     * Clear the list of extracted records.
     */
    void clearExtractedRecords();

    /**
     * Get count of extracted records
     */
    int getExtractedRecordCount();

}
