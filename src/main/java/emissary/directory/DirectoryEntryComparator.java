package emissary.directory;

import java.io.Serializable;
import java.util.Comparator;

/**
 * Comparator class for DirectoryEntry objects by expense Note: this comparator imposes orderings that are inconsistent
 * with equals.
 */
public class DirectoryEntryComparator implements Comparator<DirectoryEntry>, Serializable {

    // Serializable
    static final long serialVersionUID = -4275631999901887834L;

    @Override
    public int compare(final DirectoryEntry d1, final DirectoryEntry d2) {
        return d1.getExpense() - d2.getExpense();
    }
}
