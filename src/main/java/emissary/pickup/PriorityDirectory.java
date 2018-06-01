package emissary.pickup;

/**
 * Hold the name of a directory path along with the priority at which things should be processed from that source
 */
public class PriorityDirectory implements Comparable<PriorityDirectory> {
    protected String directoryName;
    protected int priority = Priority.DEFAULT;

    public PriorityDirectory() {}

    public PriorityDirectory(String directoryName, int priority) {
        this.directoryName = directoryName;
        this.priority = priority;
    }

    public void setPriority(int val) {
        this.priority = val;
    }

    public int getPriority() {
        return priority;
    }

    public void setDirectoryName(String val) {
        this.directoryName = val;
    }

    public String getDirectoryName() {
        return directoryName;
    }

    /**
     * Compare in priority order, lower numbers mean high priority data Note: this comparator imposes ordering that is
     * inconsistent with equals
     */
    @Override
    public int compareTo(PriorityDirectory that) {
        if (this.getPriority() < that.getPriority()) {
            return -1;
        } else if (that.getPriority() < this.getPriority()) {
            return 1;
        } else {
            return 0;
        }
    }

    @Override
    public String toString() {
        return directoryName + ":" + priority;
    }
}
