package emissary.directory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handle the details of a wildcard directory entry including iterating through the possible directory match strings A
 * dataId will be wildcarded like this, based on dash and paren markings in the dataType.
 * <ul>
 * <li>FOO-BAR(ASCII-TRANSLIT)-BAZ(SHAZAM)-BAM_MESSAGE</li>
 * <li>FOO-BAR(ASCII-TRANSLIT)-BAZ(*)-BAM_MESSAGE</li>
 * <li>FOO-BAR(*)-BAZ(*)-BAM_MESSAGE</li>
 * <li>FOO-BAR(*)-BAZ(*)-*</li>
 * <li>FOO-BAR(*)-*</li>
 * <li>FOO-*</li>
 * <li></li>
 * </ul>
 *
 * <ol>
 * <li>The complete unchanged entry is returned first, so an exact match will work like it always has worked
 * before.</li>
 * <li>Next the parenthetical expressions are wildcarded out starting from the right and moving left until all have been
 * changed to wildcards. Each step wildcards another parenthetical expression without reverting the previous one. So you
 * currently cannot get a wildcard match on the non-rightmost expression without every one to the right of it also being
 * wildcarded.</li>
 * <li>Finally, each dash delimited expression is wildcarded, then eliminated starting from the right until there is
 * nothing left but the pure wildcard entry &quot;*&quot;. The wildcarded parenthetical expressions are left wildcarded
 * for this stage, so as things are dropped off, you can only match wildcarded parentheticals that are still remaining
 * to the left.</li>
 * </ol>
 */
public class WildcardEntry {

    private static final Logger logger = LoggerFactory.getLogger(WildcardEntry.class);

    String dataType;
    String serviceType;
    List<String> wc = null;

    private static final char DASH = '-';
    private static final char OPEN = '(';
    private static final char CLOS = ')';
    private static final String KSEP = KeyManipulator.DATAIDSEPARATOR;
    private static final int KSPL = KSEP.length();

    private static final String DASH_WC = "-*"; // DASH Wildcard
    private static final String PAREN_WC = "(*)"; // PAREN Wildcard
    private static final String PURE_WC = "*"; // PURE Wildcard

    public WildcardEntry(final String s) {
        parseEntry(s);
    }

    /**
     * Parse the entry into the ordered list of wildcard entries
     */
    private synchronized void parseEntry(final String entry) {
        final int pos = entry.indexOf(KSEP);
        if (pos > 0) {
            this.dataType = entry.substring(0, pos);
            this.serviceType = entry.substring(pos + KSPL);
        } else {
            this.dataType = entry;
            this.serviceType = null;
        }
    }

    /**
     * Iterator over the wilcard entries in order
     */
    public Iterator<String> iterator() {
        load();
        return this.wc.iterator();
    }

    /**
     * Get the results of parsing as a set
     */
    public Set<String> asSet() {
        load();
        final Set<String> set = new HashSet<String>();
        set.addAll(this.wc);
        return set;
    }

    /**
     * Lazy load of the list containing wildcarded parts
     */
    private synchronized void load() {
        if (this.wc == null) {
            this.wc = new ArrayList<String>();

            // Put the original on the list first
            if (this.serviceType != null) {
                this.wc.add(this.dataType + KSEP + this.serviceType);
            } else {
                this.wc.add(this.dataType);
            }

            // Replace paren parts one at a time from the right
            String dt = this.dataType;
            int lastOpen = this.dataType.length();
            boolean done = false;
            while (!done) {
                for (int i = lastOpen - 1; i >= 0; i--) {
                    if (dt.charAt(i) == OPEN) {
                        final int clos = dt.indexOf(CLOS, i);
                        if (clos > i) {
                            dt = dt.substring(0, i) + PAREN_WC + dt.substring(clos + 1);
                            if (this.serviceType != null) {
                                this.wc.add(dt + KSEP + this.serviceType);
                            } else {
                                this.wc.add(dt);
                            }
                            lastOpen = i;
                            break;
                        }
                        done = true;
                        break;
                    }
                    if (i == 0) {
                        done = true;
                    }
                }
            }

            // Now working with the totally replaced paren string
            // handle the dashed parts

            for (int i = dt.length() - 1; i >= 0; i--) {
                if (dt.charAt(i) == DASH) {
                    if (this.serviceType != null) {
                        this.wc.add(dt.substring(0, i) + DASH_WC + KSEP + this.serviceType);
                    } else {
                        this.wc.add(dt.substring(0, i) + DASH_WC);
                    }
                }
            }
            if (this.serviceType != null) {
                this.wc.add(PURE_WC + KSEP + this.serviceType);
            } else {
                this.wc.add(PURE_WC);
            }
        }
    }

    /**
     * Select an entry from the map
     *
     * @param dataID the string to wildcard
     * @param map the map to choose from
     * @return the found entry
     */
    public static DirectoryEntryList getWildcardedEntry(final String dataID, final DirectoryEntryMap map) {

        final DirectoryEntryList matches = new DirectoryEntryList();

        final WildcardEntry we = new WildcardEntry(dataID);
        logger.debug("Got a set of size " + we.size() + " from " + dataID);

        for (final String w : we.asSet()) {
            final DirectoryEntryList found = map.get(w);
            if (found != null) {
                logger.debug("Found a wildcard match on " + w + " size=" + found.size());
                matches.addAll(found);
            } else {
                logger.debug("SKipping  " + w + " nothing in map");
            }
        }
        return matches;
    }

    /**
     * Size of this wildcard entry set
     */
    public int size() {
        load();
        return this.wc.size();
    }
}
