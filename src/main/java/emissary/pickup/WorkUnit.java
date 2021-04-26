package emissary.pickup;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * A WorkUnit is a unit of work a worker will process. The idea is to replace fileNameList. Currently, WorkBundle is set
 * to only have one file, and so there will only be one WorkUnit.
 */
public final class WorkUnit {
    private String fileName;
    private String transactionId;
    // worker updates this boolean
    private boolean failedToParse = false;
    private boolean failedToProcess = false;

    /**
     * Constructs WorkUnit with specified filename
     * 
     * @param fileName the associated filename
     */
    WorkUnit(String fileName) {
        this.fileName = fileName;
    }


    /**
     * Constructs WorkUnit with specified filename, transactionId, failedToParse, failedToProcess
     *
     * @param fileName the associated filename
     * @param transactionId the associated transactionId
     * @param failedToParse the status of failed to parse
     * @param failedToProcess the status of failed to process
     */
    WorkUnit(String fileName, String transactionId, boolean failedToParse, boolean failedToProcess) {
        this.fileName = fileName;
        this.transactionId = transactionId;
        this.failedToParse = failedToParse;
        this.failedToProcess = failedToProcess;
    }

    public static WorkUnit readFromStream(DataInputStream in) throws IOException {
        final WorkUnit u = new WorkUnit(null);
        u.fileName = WorkBundle.readUTFOrNull(in);
        u.transactionId = WorkBundle.readUTFOrNull(in);
        u.failedToParse = in.readBoolean();
        u.failedToProcess = in.readBoolean();
        return u;
    }

    public void writeToStream(DataOutputStream out) throws IOException {
        WorkBundle.writeUTFOrNull(fileName, out);
        WorkBundle.writeUTFOrNull(transactionId, out);
        out.writeBoolean(failedToParse);
        out.writeBoolean(failedToProcess);
    }

    /**
     * Gets the filename for the WorkUnit
     * 
     * @return the filename
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Sets the filename for the WorkUnit
     * 
     * @param fileName the filename
     */
    public void setFilename(String fileName) {
        this.fileName = fileName;
    }

    /**
     * Gets the transaction Id of the WorkUnit
     * 
     * @return the transaction Id
     */
    public String getTransactionId() {
        return transactionId;
    }

    /**
     * Sets the transaction id of the WorkUnit
     * 
     * @param transactionId the transaction id to set
     */
    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    /**
     * Sets the WorkUnit as a failed to parse WorkUnit
     */
    public void setFailedToParse() {
        this.failedToParse = true;
    }

    /**
     * Gets the status of whether the WorkUnit failed to parse
     * 
     * @return the boolean status of failed to parse
     */
    public boolean failedToParse() {
        return failedToParse;
    }

    /**
     * Sets the status of whether the WorkUnit had an error in processing
     */
    public void setFailedToProcess() {
        this.failedToProcess = true;
    }

    /**
     * Gets the status of whether file had an error in processing.
     * 
     * @return the boolean status of file process
     */
    public boolean failedToProcess() {
        return failedToProcess;
    }
}
