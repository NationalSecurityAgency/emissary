package emissary.kff;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.zip.CRC32;

/**
 * ChecksumCalculator is a utility class which computes checksums and message digests.
 * 
 * @see java.util.zip.CRC32 java.util.zip.CRC32
 * @see java.security.MessageDigest java.security.MessageDigest
 */
public class ChecksumCalculator {
    /** Used for CRC32 calculations */
    private CRC32 crc = null;
    /** Used for SSDEEP calculations */
    private Ssdeep ssdeep = null;

    /** Used for hash calculations */
    private List<MessageDigest> digest = new ArrayList<MessageDigest>();

    /**
     * Constructor initializes SHA-1 generator and turns on the CRC32 processing as well
     * 
     * @throws NoSuchAlgorithmException if the SHA algorithm isn't available
     */
    public ChecksumCalculator() throws NoSuchAlgorithmException {
        this("SHA-1", true);
    }

    /**
     * Constructor initializes specified algorithm
     * 
     * @param alg string name of algorightm, e.g. SHA
     * @param useCRC true if CRC32 should be calculated
     * @throws NoSuchAlgorithmException if the algorithm isn't available
     */
    public ChecksumCalculator(String alg, boolean useCRC) throws NoSuchAlgorithmException {
        this(new String[] {alg});
        setUseCRC(useCRC);
    }

    /**
     * Constructor initializes specified set of algorithms
     * 
     * @param algs array of String algorithm names, put CRC32 on list to enable
     * @throws NoSuchAlgorithmException if an algorithm isn't available
     */
    public ChecksumCalculator(String[] algs) throws NoSuchAlgorithmException {
        if (algs != null && algs.length > 0) {
            for (String alg : algs) {
                if (alg.equals("CRC32")) {
                    setUseCRC(true);
                } else if (alg.equals("SSDEEP")) {
                    setUseSsdeep(true);
                } else {
                    digest.add(MessageDigest.getInstance(alg));
                }
            }
        }
    }

    /**
     * Constructor initializes specified set of algorithms
     * 
     * @param algs Collection of String algorithm names, put CRC32 on list to enable
     * @throws NoSuchAlgorithmException if an algorithm isn't available
     */
    public ChecksumCalculator(Collection<String> algs) throws NoSuchAlgorithmException {
        if (algs != null && algs.size() > 0) {
            for (String alg : algs) {
                if (alg.equals("CRC32")) {
                    setUseCRC(true);
                } else if (alg.equals("SSDEEP")) {
                    setUseSsdeep(true);
                } else {
                    digest.add(MessageDigest.getInstance(alg));
                }
            }
        }
    }


    /**
     * Determine if we are using CRC summing
     */
    public boolean getUseCRC() {
        return (crc != null);
    }

    /**
     * Turn on or off CRC processing
     * 
     * @param use true if CRC processing is desired
     */
    public void setUseCRC(boolean use) {
        if (use) {
            crc = new CRC32();
        } else {
            crc = null;
        }
    }

    /**
     * Determine if we are using ssdeep summing
     */
    public boolean getUseSsdeep() {
        return (ssdeep != null);
    }

    /**
     * Turn on or off CRC processing
     * 
     * @param use true if CRC processing is desired
     */
    public void setUseSsdeep(boolean use) {
        if (use) {
            ssdeep = new Ssdeep();
        } else {
            ssdeep = null;
        }
    }

    /**
     * Calculates a CRC32 and a digest on a byte array.
     * 
     * @param buffer Data to compute results for
     * @return results of computing the requested hashes on the data
     */
    public ChecksumResults digest(byte[] buffer) {
        // return object to hold results
        ChecksumResults res = new ChecksumResults();

        // Reset and compute
        for (MessageDigest d : digest) {
            d.reset();
            d.update(buffer, 0, buffer.length);
            res.setHash(d.getAlgorithm(), d.digest());
        }

        // Only use CRC if non-null
        if (crc != null) {
            crc.reset();
            crc.update(buffer, 0, buffer.length);
            res.setCrc(crc.getValue());
        }

        // Only use ssdeep if non-null
        if (ssdeep != null) {
            res.setSsdeep(ssdeep.fuzzy_hash(buffer));
        }

        return res;
    }
}
