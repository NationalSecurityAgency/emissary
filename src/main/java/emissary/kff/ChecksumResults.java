package emissary.kff;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * This class is a value object to store the results of both a CRC32 and a message digest computation.
 */
public class ChecksumResults implements Serializable {

    // serializable
    static final long serialVersionUID = -8187329704593435586L;

    private long crc = -1L;
    private String ssdeep = null;
    Map<String, byte[]> hashComp = new TreeMap<String, byte[]>();

    /**
     * Gets the value of crc
     *
     * @see #getHashString(String)
     * @return the value of crc
     */
    public long getCrc() {
        return this.crc;
    }

    /**
     * Sets the value of crc
     *
     * @param argCrc Value to assign to this.crc
     */
    public void setCrc(long argCrc) {
        this.crc = argCrc;
    }

    /**
     * Gets the value of ssdeep
     *
     * @see #getHashString(String)
     * @return the value of ssdeep
     */
    public String getSsdeep() {
        return this.ssdeep;
    }

    /**
     * Sets the value of ssdeep
     *
     * @param ssdeep Value to assign to this.ssdeep
     */
    public void setSsdeep(String ssdeep) {
        this.ssdeep = ssdeep;
    }

    /**
     * Gets the value of requested computation
     *
     * @param alg name of algorithm producing desired results
     * @return the value of hash in bytes
     */
    public byte[] getHash(String alg) {
        if ("CRC32".equals(alg) && crc > -1L) {
            return Long.toString(crc).getBytes();
        }
        if ("SSDEEP".equals(alg) && ssdeep != null) {
            return ssdeep.getBytes();
        }
        return hashComp.get(alg);
    }

    /**
     * Gets the formatted value of one of the hashes
     *
     * @param alg name of algorithm producing desired results
     * @return the formatted value of hash
     */
    public String getHashString(String alg) {
        byte[] comp = hashComp.get(alg);
        if (comp == null) {
            if ("CRC32".equals(alg) && crc > -1L) {
                return Long.toString(crc);
            }
            if ("SSDEEP".equals(alg) && ssdeep != null) {
                return ssdeep;
            }
            return null;
        }

        return emissary.util.Hexl.toUnformattedHexString(comp);
    }

    /**
     * Sets the value of hash
     *
     * @param alg the name of the algorithm producing the hash
     * @param argHash Value to assign to this.hash
     */
    public void setHash(String alg, byte[] argHash) {
        hashComp.put(alg, argHash);
    }

    /**
     * Get names of digest results present
     * 
     * @return Set of string algorithm names
     */
    public Set<String> getResultsPresent() {
        Set<String> set = new TreeSet<String>(hashComp.keySet());
        if (crc > -1L) {
            set.add("CRC32");
        }
        if (ssdeep != null) {
            set.add("SSDEEP");
        }
        return set;
    }
}
