package emissary.pickup;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import emissary.util.xml.JDOMUtil;
import org.jdom2.Document;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used to communicate between the TreePickUpPlace and TreeSpace about a set of files to process.
 * 
 * Two times are tracked for the files in each work bundle - the "youngest" modification time and the "oldest"
 * modification time, both initially entered as time from the epoch. However, the concept of "youngest" and "oldest" is
 * relative to the construction time, so that:
 * <p>
 * getOldestFileModificationTime() &lt;= getYoungestFileModificationTime()
 */
public final class WorkBundle implements Comparable<WorkBundle> {

    private static final Logger logger = LoggerFactory.getLogger(WorkBundle.class);

    static final int MAX_UNITS = 1024;

    // Unique ID for this work bundle
    String bundleId;

    // Configured output root for finding data on remote side
    String outputRoot;

    // Configuration passed to remote side for producing output name
    String eatPrefix;

    // Database case id for the work in this bundle
    String caseId = null;

    // Priority of this work bundle
    int priority = Priority.DEFAULT;

    // Flag to note if the bundle is in simple mode
    boolean simpleMode = false;

    List<WorkUnit> workUnitList = new ArrayList<>();

    // Where being processed
    String sentTo;

    // Cumulative errors in processing tries
    int errorCount = 0;

    /**
     * The oldest file in the bundle in millis since epoch
     */
    long oldestFileModificationTime = Long.MAX_VALUE;

    /**
     * The youngest file in the bundle in millis since epoch
     */
    long youngestFileModificationTime = Long.MIN_VALUE;

    // Aggregate file size
    long totalFileSize = 0L;

    /**
     * Public default constructor
     */
    public WorkBundle() {
        bundleId = generateId();
    }

    /**
     * Public constructor with args
     * 
     * @param outputRoot root directory for files
     * @param eatPrefix used when constructing output name
     */
    public WorkBundle(String outputRoot, String eatPrefix) {
        bundleId = generateId();
        this.outputRoot = outputRoot;
        this.eatPrefix = eatPrefix;
    }

    /**
     * Build one as a copy of another, generating a new unique id for the copy. Transient fields sentTo and errorCount are
     * not copied by this constructor
     * 
     * @param that the work bundle to copy
     */
    public WorkBundle(WorkBundle that) {
        this.bundleId = that.bundleId;
        this.outputRoot = that.getOutputRoot();
        this.eatPrefix = that.getEatPrefix();
        this.caseId = that.getCaseId();
        this.sentTo = that.sentTo;
        this.errorCount = that.errorCount;
        this.priority = that.getPriority();
        this.simpleMode = that.getSimpleMode();
        this.oldestFileModificationTime = that.oldestFileModificationTime;
        this.youngestFileModificationTime = that.youngestFileModificationTime;
        this.totalFileSize = that.totalFileSize;
        if (that.getWorkUnitList().size() > 0) {
            this.addWorkUnits(that.getWorkUnitList());
        }
        resetBundleId();
    }

    /**
     * Deserialize a WorkBundle from a DataInputStream
     *
     * @param in the stream to read from
     * @return the deserialized WorkBundle
     * @throws IOException if there is a problem reading the stream or it contains more than <code>MAX_UNITS</code> work
     *         units.
     */
    public static WorkBundle readFromStream(DataInputStream in) throws IOException {
        WorkBundle wb = new WorkBundle();
        wb.bundleId = readUTFOrNull(in);
        wb.outputRoot = readUTFOrNull(in);
        wb.eatPrefix = readUTFOrNull(in);
        wb.caseId = readUTFOrNull(in);
        wb.sentTo = readUTFOrNull(in);
        wb.errorCount = in.readInt();
        wb.priority = in.readInt();
        wb.simpleMode = in.readBoolean();
        wb.oldestFileModificationTime = in.readLong();
        wb.youngestFileModificationTime = in.readLong();
        wb.totalFileSize = in.readLong();
        int workUnitSize = in.readInt();
        if (workUnitSize > MAX_UNITS) {
            throw new IOException(
                    "Exception when reading: WorkBundle may not contain more then " + MAX_UNITS + " WorkUnits (saw: " + workUnitSize + ").");
        }
        for (int i = 0; i < workUnitSize; i++) {
            wb.addWorkUnit(WorkUnit.readFromStream(in));
        }
        return wb;
    }

    /**
     * Serialize this WorkBundle to a DataOutputStream
     *
     * @param out the stream to write to.
     * @throws IOException if there is a problem writing to the stream.
     */
    public void writeToStream(DataOutputStream out) throws IOException {
        writeUTFOrNull(bundleId, out);
        writeUTFOrNull(outputRoot, out);
        writeUTFOrNull(eatPrefix, out);
        writeUTFOrNull(caseId, out);
        writeUTFOrNull(sentTo, out);
        out.writeInt(errorCount);
        out.writeInt(priority);
        out.writeBoolean(simpleMode);
        out.writeLong(oldestFileModificationTime);
        out.writeLong(youngestFileModificationTime);
        out.writeLong(totalFileSize);
        out.writeInt(workUnitList.size());
        if (workUnitList.size() > MAX_UNITS) {
            throw new IOException(
                    "Exception when writing: WorkBundle may not contain more then " + MAX_UNITS + " WorkUnits (saw: " + workUnitList.size() + ").");
        }
        for (WorkUnit u : workUnitList) {
            u.writeToStream(out);
        }
    }

    static String readUTFOrNull(DataInputStream in) throws IOException {
        if (in.readBoolean()) {
            return in.readUTF();
        }
        return null;
    }

    static void writeUTFOrNull(String s, DataOutputStream out) throws IOException {
        out.writeBoolean(s != null);
        if (s != null) {
            out.writeUTF(s);
        }
    }

    /**
     * Set the work bundle id
     * 
     * @param val the new value to set as bundle id
     */
    public void setBundleId(String val) {
        this.bundleId = val;
    }

    /**
     * Reset the unique id
     * 
     * @return a copy of the new id
     */
    public String resetBundleId() {
        bundleId = generateId();
        return bundleId;
    }

    /**
     * Get the work bundle id
     */
    public String getBundleId() {
        return bundleId;
    }

    /**
     * Generate a new unique id
     * 
     * @return the new id value
     */
    protected static String generateId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Gets the value of outputRoot
     * 
     * @return the value of outputRoot
     */
    public String getOutputRoot() {
        return this.outputRoot;
    }

    /**
     * Sets the value of outputRoot
     * 
     * @param argOutputRoot Value to assign to this.outputRoot
     */
    public void setOutputRoot(String argOutputRoot) {
        this.outputRoot = argOutputRoot;
    }

    /**
     * Gets the value of eatPrefix
     * 
     * @return the value of eatPrefix
     */
    public String getEatPrefix() {
        return this.eatPrefix;
    }

    /**
     * Sets the value of eatPrefix
     * 
     * @param argEatPrefix Value to assign to this.eatPrefix
     */
    public void setEatPrefix(String argEatPrefix) {
        this.eatPrefix = argEatPrefix;
    }

    /**
     * Gets the list of WorkUnits in bundle
     * 
     * @return the list of WorkUnits
     */
    public List<WorkUnit> getWorkUnitList() {
        return new ArrayList<>(workUnitList);
    }

    /**
     * Gets an iterator over work units
     * 
     * @return iterator of WorkUnit
     */
    public Iterator<WorkUnit> getWorkUnitIterator() {
        return workUnitList.iterator();
    }

    /**
     * Add a workUnit to the list.
     *
     * @param workUnit the workUnit to add
     * @return number of WorkUnits in list after add
     * @throws IllegalStateException if adding the unit would cause the bundle to contain more than <code>MAX_UNITS</code>
     *         work units
     */
    public int addWorkUnit(WorkUnit workUnit) {
        if (workUnitList.size() >= MAX_UNITS) {
            throw new IllegalStateException("WorkBundle may not contain more than " + MAX_UNITS + " WorkUnits.");
        }
        workUnitList.add(workUnit);
        return size();
    }

    /**
     * Add a workunit to the list
     * 
     * @param workUnit the workUnit to add
     * @param fileModificationTimeInMillis the file modification time in milliseconds since epoch
     * @param fileSize the size of the file added.
     * @throws IllegalStateException if adding the unit would cause the bundle to contain more than <code>MAX_UNITS</code>
     *         work units
     * @return number of files in this set after update
     */
    public int addWorkUnit(WorkUnit workUnit, long fileModificationTimeInMillis, long fileSize) {
        addWorkUnit(workUnit);

        if (fileModificationTimeInMillis < oldestFileModificationTime) {
            oldestFileModificationTime = fileModificationTimeInMillis;
        }
        if (fileModificationTimeInMillis > youngestFileModificationTime) {
            youngestFileModificationTime = fileModificationTimeInMillis;
        }
        totalFileSize += fileSize;
        return size();
    }

    /**
     * Add from a list, without adjusting file modification time tracking.
     * 
     * @param list a list of WorkUnits to add to this bundle
     * @return the total size of WorkUnits in this bundle
     * @throws IllegalStateException if adding the units would cause the bundle to contain more than <code>MAX_UNITS</code>
     *         work units
     */
    protected int addWorkUnits(List<WorkUnit> list) { // This appears to only be used by unit tests and the copy constructor
        if (workUnitList.size() + list.size() > MAX_UNITS) {
            throw new IllegalStateException("WorkBundle may not contain more than " + MAX_UNITS + " WorkUnits.");
        }
        workUnitList.addAll(list);
        return workUnitList.size();
    }

    /**
     * Gets the list of file names
     * 
     * @return the string values of filenames
     */
    public List<String> getFileNameList() {
        ArrayList<String> fileNameList = new ArrayList<>(workUnitList.size());
        for (WorkUnit workUnit : workUnitList) {
            fileNameList.add(workUnit.getFileName());
        }

        return fileNameList;
    }

    /**
     * Gets an iterator over file names
     * 
     * @return iterator of String filename values
     */
    public Iterator<String> getFileNameIterator() {
        return getFileNameList().iterator();
    }

    /**
     * Add a file to the list, without adjusting file modification time tracking.
     * 
     * @param file string file name consistent with outputRoot
     * @return number of files in this set after update
     * @throws IllegalStateException if adding the file would cause the bundle to contain more than <code>MAX_UNITS</code>
     *         work units
     */
    public int addFileName(String file) {
        return addWorkUnit(new WorkUnit(file));
    }

    /**
     * Add a file to the list
     * 
     * @param file string file name consistent with outputRoot
     * @param fileModificationTimeInMillis the file modification time in milliseconds since epoch
     * @param fileSize the size of the file being added
     * @return number of files in this set after update
     * @throws IllegalStateException if adding the file would cause the bundle to contain more than <code>MAX_UNITS</code>
     *         work units
     */
    public int addFileName(String file, long fileModificationTimeInMillis, long fileSize) {
        return addWorkUnit(new WorkUnit(file), fileModificationTimeInMillis, fileSize);
    }

    /**
     * Add files to the list, without adjusting file modification time tracking.
     * 
     * @param file string file names consistent with outputRoot
     * @return number of files in this set after update
     * @throws IllegalStateException if adding the files would cause the bundle to contain more than <code>MAX_UNITS</code>
     *         work units
     */
    protected int addFileNames(String[] file) { // This appears to only be used by unit tests
        for (String f : file) {
            addWorkUnit(new WorkUnit(f));
        }
        return size();
    }

    /**
     * Add from a list, without adjusting file modification time tracking.
     * 
     * @param list the list of files to add
     * @throws IllegalStateException if adding the files would cause the bundle to contain more than <code>MAX_UNITS</code>
     *         work units
     */
    protected int addFileNames(List<String> list) { // This appears to only be used by unit tests and the copy
                                                    // constructor
        for (String file : list) {
            addWorkUnit(new WorkUnit(file));
        }
        return size();
    }

    /**
     * Get the number of files contained
     */
    public int size() {
        return workUnitList.size();
    }

    /**
     * Clear the files from the list
     */
    protected void clearFiles() {
        // This is only used for testing
        workUnitList.clear();
        oldestFileModificationTime = Long.MAX_VALUE;
        youngestFileModificationTime = Long.MIN_VALUE;
        totalFileSize = 0L;
    }

    /**
     * Gets the value of caseId
     * 
     * @return the value of caseId
     */
    public String getCaseId() {
        return this.caseId;
    }

    /**
     * Sets the value of caseId
     * 
     * @param argCaseId Value to assign to this.caseId
     */
    public void setCaseId(String argCaseId) {
        this.caseId = argCaseId;
    }

    /**
     * Set the transient sentTo indicating inprogress work
     */
    public void setSentTo(String place) {
        this.sentTo = place;
    }

    /**
     * Get the transient sentTo
     */
    public String getSentTo() {
        return sentTo;
    }

    /**
     * Get the transient error count
     */
    public int getErrorCount() {
        return errorCount;
    }

    /**
     * Increment the error count
     * 
     * @return the new value
     */
    public int incrementErrorCount() {
        return ++errorCount;
    }

    /**
     * Set a new value for the error count
     */
    public void setErrorCount(int val) {
        errorCount = val;
    }

    /**
     * Set a new priority
     */
    public void setPriority(int val) {
        priority = val;
    }

    /**
     * Get the priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Set the value for the simple flag
     * 
     * @param val the new value for the flag
     */
    public void setSimpleMode(boolean val) {
        simpleMode = val;
    }

    /**
     * Get the value for the simple mode flag
     */
    public boolean getSimpleMode() {
        return simpleMode;
    }

    public long getOldestFileModificationTime() {
        return oldestFileModificationTime;
    }

    public void setOldestFileModificationTime(long oldestFileModificationTime) {
        this.oldestFileModificationTime = oldestFileModificationTime;
    }

    public long getYoungestFileModificationTime() {
        return youngestFileModificationTime;
    }

    public void setYoungestFileModificationTime(long youngestFileModificationTime) {
        this.youngestFileModificationTime = youngestFileModificationTime;
    }

    public long getTotalFileSize() {
        return totalFileSize;
    }

    public void setTotalFileSize(long totalFileSize) {
        this.totalFileSize = totalFileSize;
    }

    /**
     * Compare in priority order, lower numbers mean high priority data Note: this comparator imposes ordering that is
     * inconsistent with equals
     */
    @Override
    public int compareTo(WorkBundle that) {
        if (this.getPriority() < that.getPriority()) {
            return -1;
        } else if (that.getPriority() < this.getPriority()) {
            return 1;
        } else {
            return 0;
        }
    }

    /**
     * Provide string version
     */
    @Override
    public String toString() {
        return "WorkBundle[id=" + getBundleId() + ", pri=" + getPriority() + ", files=" + getFileNameList().toString() + ", eatPrefix="
                + getEatPrefix()
                + ", outputRoot=" + getOutputRoot() + ", sentTo=" + getSentTo() + ", errorCount=" + getErrorCount() + ", totalFileSize="
                + getTotalFileSize() + ", oldestModTime=" + getOldestFileModificationTime() + ", youngModTime=" + getYoungestFileModificationTime()
                + ", simple=" + getSimpleMode() + ", caseId=" + getCaseId() + ", size=" + size() + "]";
    }

    public String toXml() {
        Element root = new Element("workBundle");
        root.addContent(JDOMUtil.simpleElement("bundleId", getBundleId()));
        root.addContent(JDOMUtil.simpleElement("outputRoot", getOutputRoot()));
        root.addContent(JDOMUtil.simpleElement("eatPrefix", getEatPrefix()));
        root.addContent(JDOMUtil.simpleElement("caseId", getCaseId()));
        root.addContent(JDOMUtil.simpleElement("sentTo", getSentTo()));
        root.addContent(JDOMUtil.simpleElement("errorCount", getErrorCount()));
        root.addContent(JDOMUtil.simpleElement("priority", getPriority()));
        root.addContent(JDOMUtil.simpleElement("simpleMode", getSimpleMode()));
        root.addContent(JDOMUtil.simpleElement("oldestFileModificationTime", getOldestFileModificationTime()));
        root.addContent(JDOMUtil.simpleElement("youngestFileModificationTime", getYoungestFileModificationTime()));
        root.addContent(JDOMUtil.simpleElement("totalFileSize", getTotalFileSize()));

        for (WorkUnit wu : workUnitList) {
            Element workunit = new Element("workUnit");
            workunit.addContent(JDOMUtil.simpleElement("workFileName", wu.getFileName()));
            if (wu.getTransactionId() != null) {
                workunit.addContent(JDOMUtil.simpleElement("transactionId", wu.getTransactionId()));
            }
            workunit.addContent(JDOMUtil.simpleElement("failedToParse", wu.failedToParse()));
            workunit.addContent(JDOMUtil.simpleElement("failedToProcess", wu.failedToProcess()));

            root.addContent(workunit);
        }

        Document jdom = new Document(root);
        return JDOMUtil.toString(jdom);
    }

    /**
     * Build a WorkBundle object from xml
     * 
     * @param xml the xml string representing a WorkBundle
     * @return the constructed WorkBundle or null on error
     */
    public static WorkBundle buildWorkBundle(String xml) {
        Document jdoc;
        try {
            jdoc = JDOMUtil.createDocument(xml, false);
            return buildWorkBundle(jdoc);
        } catch (Exception ex) {
            logger.error("Cannot make WorkBundle from " + xml, ex);
            return null;
        }
    }

    /**
     * Build a WorkBundle object from a jdom document
     * 
     * @param jdom the jdom document representing a work bundle object
     * @return the constructed WorkBundle or null on error
     */
    private static WorkBundle buildWorkBundle(Document jdom) {
        Element root = jdom.getRootElement();
        if (root == null) {
            logger.error("Document does not have a root element!");
            return null;
        }

        WorkBundle wb = new WorkBundle();
        wb.setBundleId(root.getChildTextTrim("bundleId"));
        String s = root.getChildTextTrim("outputRoot");
        if (s != null && s.length() > 0) {
            wb.setOutputRoot(s);
        } else {
            wb.setOutputRoot(null);
        }

        s = root.getChildTextTrim("eatPrefix");
        if (s != null && s.length() > 0) {
            wb.setEatPrefix(s);
        } else {
            wb.setEatPrefix(null);
        }

        s = root.getChildTextTrim("caseId");
        if (s != null && s.length() > 0) {
            wb.setCaseId(s);
        } else {
            wb.setCaseId(null);
        }

        s = root.getChildTextTrim("sentTo");
        if (s != null && s.length() > 0) {
            wb.setSentTo(s);
        } else {
            wb.setSentTo(null);
        }

        wb.setPriority(JDOMUtil.getChildIntValue(root, "priority"));
        wb.setSimpleMode(JDOMUtil.getChildBooleanValue(root, "simpleMode"));
        wb.setOldestFileModificationTime(JDOMUtil.getChildLongValue(root, "oldestFileModificationTime"));
        wb.setYoungestFileModificationTime(JDOMUtil.getChildLongValue(root, "youngestFileModificationTime"));
        wb.setTotalFileSize(JDOMUtil.getChildLongValue(root, "totalFileSize"));
        String serr = root.getChildTextTrim("errorCount");
        if (serr != null && serr.length() > 0) {
            wb.setErrorCount(Integer.parseInt(serr));
        }

        for (Element wu : root.getChildren("workUnit")) {
            String filename = wu.getChildTextTrim("workFileName");
            String transactionId = wu.getChildTextTrim("transactionId");
            boolean failedToParse = Boolean.valueOf(wu.getChildTextTrim("failedToParse"));
            boolean failedToProcess = Boolean.valueOf(wu.getChildTextTrim("failedToProcess"));
            wb.addWorkUnit(new WorkUnit(filename, transactionId, failedToParse, failedToProcess));
        }

        return wb;
    }
}
