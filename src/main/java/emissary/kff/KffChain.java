package emissary.kff;

import emissary.core.channels.SeekableByteChannelFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Keep a list of hash algorithm names and compute them and compare the results to the ordered chain of KFF filter when
 * data is submitted.
 */
public class KffChain {
    private static final Logger logger = LoggerFactory.getLogger(KffChain.class);

    protected List<KffFilter> list = new ArrayList<>();

    // Smaller than this and we don't report a hit
    protected static final int DEFAULT_KFF_MIN_DATA_SIZE = 0;
    protected int kffMinDataSize = DEFAULT_KFF_MIN_DATA_SIZE;

    // The algorithms to compute
    protected List<String> algorithms = new ArrayList<>();

    /**
     * Construct an empty KFF Chain
     */
    public KffChain() {}

    /**
     * Add a new filter to our list
     */
    public void addFilter(@Nullable KffFilter f) {
        if (f != null) {
            list.add(f);
        }
    }

    /**
     * Return the filter count we are chaining
     */
    public int size() {
        return list.size();
    }

    /**
     * Set the min data size to report hits for (checksums are still calculated for data smaller than this
     *
     * @param i the minimum number of bytes hits will be reported for
     */
    public void setMinDataSize(int i) {
        if (i < 0) {
            i = 0;
        }
        kffMinDataSize = i;
    }

    /**
     * Add the specified algorithms
     *
     * @param c string names of algorithms
     * @see java.security.MessageDigest#getInstance(String)
     */
    public void addAlgorithms(Collection<String> c) {
        algorithms.addAll(c);
    }

    /**
     * Set the specified algorithms
     *
     * @param c string names of algorithms
     * @see java.security.MessageDigest#getInstance(String)
     */
    public void setAlgorithms(Collection<String> c) {
        algorithms.clear();
        algorithms.addAll(c);
    }

    /**
     * Add an algorithm
     *
     * @param alg name of algorithm
     * @see java.security.MessageDigest#getInstance(String)
     */
    public void addAlgorithm(String alg) {
        algorithms.add(alg);
    }

    /**
     * Remove an algorithm from use
     *
     * @param alg name to remove
     * @return true if the list contained the item
     */
    public boolean removeAlgorithm(String alg) {
        return algorithms.remove(alg);
    }

    /**
     * Return the algorithms in use.
     *
     * @return list of string algorithm names
     * @see java.security.MessageDigest#getInstance(String)
     */
    public List<String> getAlgorithms() {
        List<String> list = new ArrayList<>(algorithms);
        return list;
    }

    /**
     * Check content on our chain in the order loaded Data smaller than minDataSize will get hashes computed but can never
     * be reported as KNOWN data.
     *
     * @return result of check
     */
    public KffResult check(final String itemName, final byte[] content) throws NoSuchAlgorithmException {
        final ChecksumResults sums = computeSums(content);
        KffResult answer = null;
        if (content.length < kffMinDataSize || list.isEmpty()) {
            answer = new KffResult(sums);
            answer.setItemName(itemName);
        } else {
            // Surround checkAgainst with a try/catch to handle the
            // case where one of the KffFilter throws an Exception.
            // Without the try/catch, the original checksums are lost
            // and nulled out in the output
            try {
                answer = checkAgainst(list, itemName, sums);
            } catch (final Exception e) {
                logger.debug("Problem running KffFilter list.  Using only Checksums", e);
                answer = new KffResult(sums);
                answer.setItemName(itemName);
            }
        }
        return answer;
    }

    /**
     * Check content on our chain in the order loaded Data smaller than minDataSize will get hashes computed but can never
     * be reported as KNOWN data.
     *
     * @return result of check
     * @throws NoSuchAlgorithmException if the checksum can't be calculated
     * @throws IOException if an error occurred reading the data
     */
    public KffResult check(final String itemName, final SeekableByteChannelFactory sbcf) throws NoSuchAlgorithmException, IOException {
        final ChecksumResults sums = computeSums(sbcf);
        KffResult answer = null;
        long sbcSize = 0;
        try (final SeekableByteChannel sbc = sbcf.create()) {
            sbcSize = sbc.size();
        }
        if (sbcSize < kffMinDataSize || list.isEmpty()) {
            answer = new KffResult(sums);
            answer.setItemName(itemName);
        } else {
            // Surround checkAgainst with a try/catch to handle the
            // case where one of the KffFilter throws an Exception.
            // Without the try/catch, the original checksums are lost
            // and nulled out in the output
            try {
                answer = checkAgainst(list, itemName, sums);
            } catch (final Exception e) {
                logger.debug("Problem running KffFilter list.  Using only Checksums", e);
                answer = new KffResult(sums);
                answer.setItemName(itemName);
            }
        }
        return answer;
    }

    /**
     * Check content against one of our lists. Stop when we get a hit
     *
     * @param l list of KffFilter objects to test against
     * @param itemName name of the current item, filled into the result
     * @param csum the precomputed hash sums for our content
     * @return results of testing
     */
    private static KffResult checkAgainst(List<KffFilter> l, String itemName, ChecksumResults csum) throws Exception {
        KffResult r = new KffResult(csum);
        r.setItemName(itemName);

        for (KffFilter k : l) {
            boolean hit = k.check(itemName, csum);
            if (hit) {
                r.setFilterName(k.getName());
                r.setHitAndType(k.getFilterType());
                break;
            }
        }
        return r;
    }

    /**
     * Compute the sums once for the whole chain
     *
     * @param fileContents data to hash
     * @return results of all requested computations
     */
    public ChecksumResults computeSums(byte[] fileContents) throws NoSuchAlgorithmException {
        final ChecksumCalculator calc = new ChecksumCalculator(algorithms);
        return calc.digest(fileContents);
    }

    /**
     * Compute the sums once for the whole chain
     *
     * @param sbcf data to hash
     * @return results of all requested computations
     */
    public ChecksumResults computeSums(final SeekableByteChannelFactory sbcf) throws NoSuchAlgorithmException {
        final ChecksumCalculator calc = new ChecksumCalculator(algorithms);
        return calc.digest(sbcf);
    }
}
