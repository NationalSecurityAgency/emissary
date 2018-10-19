package emissary.command.converter;

import java.util.Comparator;

import com.beust.jcommander.IStringConverter;
import emissary.pickup.WorkBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkspaceSortModeConverter implements IStringConverter<Comparator<WorkBundle>> {
    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceSortModeConverter.class);
    public static final String YOUNGEST_FIRST = "yf";
    public static final String OLDEST_FIRST = "of";
    public static final String SMALLEST_FIRST = "sf";
    public static final String LARGEST_FIRST = "lf";

    @Override
    public Comparator<WorkBundle> convert(String value) {
        Comparator<WorkBundle> outbound = null;
        switch (value) {
            case YOUNGEST_FIRST:
                // The 11 is just the default initial capacity copied over in this case
                // where the caller askes for a particular sort order in the queue
                outbound = new YoungestFirstComparator();
                LOG.info("Using youngest first feeder queue");
                break;
            case OLDEST_FIRST:
                outbound = new OldestFirstComparator();
                LOG.info("Using oldest first feeder queue");
                break;
            case SMALLEST_FIRST:
                outbound = new SmallestFirstComparator();
                LOG.info("Using smallest first feeder queue");
                break;
            case LARGEST_FIRST:
                outbound = new LargestFirstComparator();
                LOG.info("Using largest first feeder queue");
                break;
            default:
                LOG.warn("Unknown sort order: {}. Using priority-based sort (if priorities are specified in the directory names)", value);
                break;
        }
        return outbound;
    }

    /**
     * Order the queue by the oldest file modification time in the bundles. "Oldest" is relative to "now".
     *
     * However, if the priority of the bundles differ, the priority overrules the times
     */
    public static final class OldestFirstComparator implements Comparator<WorkBundle> {
        @Override
        public int compare(final WorkBundle wb1, final WorkBundle wb2) {
            // First check the priority and use that before anything else
            if (wb1.getPriority() < wb2.getPriority()) {
                return -1;
            } else if (wb1.getPriority() > wb2.getPriority()) {
                return 1;
            }

            // If even priority, defer to the file modification time
            if (wb1.getOldestFileModificationTime() < wb2.getOldestFileModificationTime()) {
                return -1;
            } else if (wb1.getOldestFileModificationTime() > wb2.getOldestFileModificationTime()) {
                return 1;
            }
            return 0;
        }
    }

    /**
     * Order the queue by the youngest/most-recent file modification time in the bundles. "Youngest" is relative to "now".
     *
     * However, if the priority of the bundles differ, the priority overrules the times
     */
    public static final class YoungestFirstComparator implements Comparator<WorkBundle> {
        @Override
        public int compare(final WorkBundle wb1, final WorkBundle wb2) {
            // First check the priority and use that before anything else
            if (wb1.getPriority() < wb2.getPriority()) {
                return -1;
            } else if (wb1.getPriority() > wb2.getPriority()) {
                return 1;
            }

            // If even priority, defer to the file modification time
            if (wb1.getYoungestFileModificationTime() > wb2.getYoungestFileModificationTime()) {
                return -1;
            } else if (wb1.getYoungestFileModificationTime() < wb2.getYoungestFileModificationTime()) {
                return 1;
            }
            return 0;
        }
    }

    /**
     * Order the queue by the largest work bundle (in aggregate file size).
     *
     * However, if the priority of the bundles differ, the priority overrules the sizes
     */
    public static final class LargestFirstComparator implements Comparator<WorkBundle> {
        @Override
        public int compare(final WorkBundle wb1, final WorkBundle wb2) {
            // First check the priority and use that before anything else
            if (wb1.getPriority() < wb2.getPriority()) {
                return -1;
            } else if (wb1.getPriority() > wb2.getPriority()) {
                return 1;
            }

            // If even priority, defer to the file size
            if (wb1.getTotalFileSize() > wb2.getTotalFileSize()) {
                return -1;
            } else if (wb1.getTotalFileSize() < wb2.getTotalFileSize()) {
                return 1;
            }
            return 0;
        }
    }

    /**
     * Order the queue by the smallest work bundle (in aggregate file size).
     *
     * However, if the priority of the bundles differ, the priority overrules the sizes
     */
    public static final class SmallestFirstComparator implements Comparator<WorkBundle> {
        @Override
        public int compare(final WorkBundle wb1, final WorkBundle wb2) {
            // First check the priority and use that before anything else
            if (wb1.getPriority() < wb2.getPriority()) {
                return -1;
            } else if (wb1.getPriority() > wb2.getPriority()) {
                return 1;
            }

            // If even priority, defer to the file size
            if (wb1.getTotalFileSize() < wb2.getTotalFileSize()) {
                return -1;
            } else if (wb1.getTotalFileSize() > wb2.getTotalFileSize()) {
                return 1;
            }
            return 0;
        }
    }
}
