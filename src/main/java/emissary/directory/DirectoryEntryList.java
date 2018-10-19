package emissary.directory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.lang.StringUtils;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hold a set of Directory keys (four-tuples) in sorted order by expense, cheapest first, no duplicates
 */
public class DirectoryEntryList extends CopyOnWriteArrayList<DirectoryEntry> {

    private static final Logger logger = LoggerFactory.getLogger(DirectoryEntryList.class);

    // Serializable
    static final long serialVersionUID = -612273877522294443L;

    /** Xml value of entry list element */
    public static final String ENTRYLIST = "entryList";

    /** Value of DEEP_COPY flag */
    public static final boolean DEEP_COPY = true;

    /** Value of SHALLOW_COPY flag */
    public static final boolean SHALLOW_COPY = false;

    /** Value of PRESERVE_TIME flag */
    public static final boolean PRESERVE_TIME = true;

    /**
     *
     */
    public DirectoryEntryList() {
        super();
    }

    /**
     * Create one from an existing DirectoryEntryList Makes a shallow copy of the argument list
     * 
     * @param list the existing list to copy into this one
     */
    public DirectoryEntryList(final DirectoryEntryList list) {
        this(list, SHALLOW_COPY, !PRESERVE_TIME);
    }

    /**
     * Create one from an existing DirectoryEntryList
     * 
     * @param list the existing list to copy into this one
     * @param deepCopy true if should be a deep copy
     */
    public DirectoryEntryList(final DirectoryEntryList list, final boolean deepCopy) {
        this(list, deepCopy, !PRESERVE_TIME);
    }

    /**
     * Create one from an existing DirectoryEntryList
     * 
     * @param list the existing list to copy into this one
     * @param deepCopy true if should be a deep copy
     * @param preserveTime true if time should be preserved on entries
     */
    protected DirectoryEntryList(final DirectoryEntryList list, final boolean deepCopy, final boolean preserveTime) {
        super();
        if (list != null) {
            for (final DirectoryEntry d : list) {
                if (deepCopy) {
                    final DirectoryEntry copy = new DirectoryEntry(d);
                    if (preserveTime) {
                        copy.preserveCopyAge(d.getAge());
                    }
                    this.add(copy);
                } else {
                    this.add(d);
                }
            }
        }
    }

    /**
     * Add entries from a collection. Does not make a deep copy from collection, just adds them
     * 
     * @param c the collection to add from
     * @return true if the list is changed by this call
     */
    @Override
    public boolean addAll(final Collection<? extends DirectoryEntry> c) {
        boolean changed = false;

        for (final DirectoryEntry d : c) {
            final boolean bs = insert(d);
            changed = changed || bs;
        }
        return changed;
    }

    /**
     * Add a new DirectoryEntry
     * 
     * @param o the DirectoryEntry object to add
     * @return true as per general contract of Collection.add
     */
    @Override
    public boolean add(final DirectoryEntry o) {
        return insert(o);
    }

    /**
     * Keep the list in sorted order by expense cheapest first, no duplicates. If a duplicate key is found, the least
     * expensive copy will be kept. This keeps us out of trouble double adding the remote overhead when there is no reason
     * to do so.
     *
     * For example, if we have A and B on our peer list, A has already retrieved B's entries and added the remote overhead
     * when storing them in A. When we retrieve the same entry from A and B it looks cheaper coming from B since it is local
     * to B. Whichever one we get last, we want to keep the one coming from B. By the time they get here, the one from B
     * will have REMOTE_COST added once, and the one from A will have it added twice: once by A and once here.
     * 
     * @param newEntry the incoming entry for insertion
     * @return true as per general contract of Collection.add
     */
    private boolean insert(final DirectoryEntry newEntry) {
        // Calling .key() gets the key part without cost
        final String newKey = newEntry.getKey();

        // Find any duplicate keyed entry
        for (int i = 0; i < size(); i++) {
            final DirectoryEntry currEntry = getEntry(i);

            // Calling .key() gets the key part without cost
            if (newKey.equals(currEntry.getKey())) {
                if (newEntry.isBetterThan(currEntry)) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Duplicate keyed entry existing discarded " + currEntry.getKey() + "$" + currEntry.getExpense()
                                + " more costly than incoming " + newEntry.getExpense());
                    }
                    this.remove(i);
                    break;
                }
                // Current entry is better or just as good
                if (logger.isDebugEnabled()) {
                    logger.debug("Duplicate keyed incoming entry discarded " + newKey + "$" + newEntry.getExpense() + " is not as good as current "
                            + currEntry.getExpense());
                }
                return true;
            }
        }

        // Insert the new one in order of expense
        for (int i = 0; i < this.size(); i++) {
            final DirectoryEntry currEntry = getEntry(i);
            if (newEntry.isBetterThan(currEntry)) {
                super.add(i, newEntry);
                return true;
            } else if ((newEntry.getExpense() == currEntry.getExpense())) {
                // we could have an equal expense and if so, add lexicographically based on service name
                if (newEntry.getServiceName().compareTo(currEntry.getServiceName()) < 0) {
                    super.add(i, newEntry);
                    return true;
                }
            }
        }

        // Add it at the end
        return super.add(newEntry);
    }

    /**
     * Get entry at specified position
     * 
     * @param i the position
     * @return DirectoryEntry at position i
     */
    public DirectoryEntry getEntry(final int i) {
        return super.get(i);
    }

    /**
     * Sort the list when we have made external cost modifications
     */
    public void sort() {
        // CopyOnWriteArrayList does not work in Collections.sort
        // because it's iterator does not support Iterator.set !!
        // I think this is totally BOGUS.
        // Collections.sort(this,new DirectoryEntryComparator());
        final DirectoryEntry[] a = this.toArray(new DirectoryEntry[0]);
        Arrays.sort(a, new DirectoryEntryComparator());
        for (int j = 0; j < a.length; j++) {
            this.set(j, a[j]);
        }
    }

    /**
     * Pick one of any that are tied for expense at random
     * 
     * @param desiredExpense the expense we want
     */
    public DirectoryEntry pickOneOf(final int desiredExpense) {
        int min = 0;
        int max = 0;
        int pos = 0;

        while (pos < this.size()) {
            final DirectoryEntry e = getEntry(pos);
            if (e.getExpense() == desiredExpense) {
                min = pos;
                max = pos;
                break;
            }
            pos++;
        }

        while (pos < this.size()) {
            final DirectoryEntry e = getEntry(pos);
            if (e.getExpense() != desiredExpense) {
                break;
            }
            max = pos;
            pos++;
        }

        // if there's only one pick it
        if (min == max) {
            return getEntry(min);
        }

        // we have more than one (which we really should prevent at start-up)
        List<String> serviceList = new ArrayList<String>();
        for (DirectoryEntry entry : this) {
            serviceList.add(entry.getKey());
        }
        logger.error("There is a service cost/quality collision at {} for {}.", desiredExpense, StringUtils.join(serviceList, ","));
        // since the entries should be sorted, the first one should always be the same
        return getEntry(min);
    }

    /**
     * Build an entry list from the supplied xml fragment
     */
    public static DirectoryEntryList fromXML(final Element e) {
        final DirectoryEntryList d = new DirectoryEntryList();
        final List<Element> entryElements = e.getChildren(DirectoryEntry.ENTRY);
        for (Iterator<Element> i = entryElements.iterator(); i.hasNext();) {
            final Element entryElement = i.next();
            d.add(DirectoryEntry.fromXML(entryElement));
        }
        return d;
    }

    /**
     * Turn this list into an XML fragment
     */
    public Element getXML() {
        final Element root = new Element(ENTRYLIST);
        for (final DirectoryEntry entry : this) {
            root.addContent(entry.getXML());
        }
        return root;
    }

    /**
     * Utility method to make a deep copy of a List&lt;DirectoryEntry&gt;
     *
     * @param that the list to copy
     * @param sort true if returned list should be sorted
     * @return the new list
     */
    public static List<DirectoryEntry> deepCopy(final List<DirectoryEntry> that, final boolean sort) {
        final List<DirectoryEntry> copies = new ArrayList<DirectoryEntry>();
        if (that != null) {
            for (final DirectoryEntry e : that) {
                copies.add(new DirectoryEntry(e));
            }

            if (sort) {
                Collections.sort(copies, new DirectoryEntryComparator());
            }
        }
        return copies;
    }
}
