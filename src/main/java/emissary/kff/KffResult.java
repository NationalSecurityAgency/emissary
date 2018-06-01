package emissary.kff;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import emissary.kff.KffFilter.FilterType;

/**
 * Provide results of a KFF check including the details of the hash or cryptographic sum or sums that were used.
 */
public class KffResult implements Serializable {

    // Serializable
    static final long serialVersionUID = -5338535050376705502L;

    String itemName = null;

    String filterName = null;

    boolean hit = false;

    FilterType filterType = null;

    long crc32 = -1L;
    String ssdeep = null;

    Map<String, byte[]> hashComp = new TreeMap<String, byte[]>();

    /**
     * Create an empty result object
     */
    public KffResult() {}

    /**
     * Create a result object using all of the hashes stored in the ChecksumResults
     * 
     * @param csum the computed hashes
     */
    public KffResult(ChecksumResults csum) {
        this.setCrc32(csum.getCrc());
        this.setSsdeep(csum.getSsdeep());
        for (String alg : csum.getResultsPresent()) {
            hashComp.put(alg, csum.getHash(alg));
        }
    }

    /**
     * Create a result indicating the result known/dupe status
     * 
     * @param isHit true if this result is a known/dupe file
     */
    public KffResult(boolean isHit) {
        this.hit = isHit;
    }

    /**
     * Gets the value of hit
     *
     * @return the value of hit
     */
    public boolean isHit() {
        return this.hit;
    }

    /**
     * Sets the value of known
     *
     * @param argHit Value to assign to this.known
     */
    public void setHit(boolean argHit) {
        this.hit = argHit;
    }

    /**
     * Set the hit status to true and copy in the filter type from the argument
     * 
     * @param ft the filter type
     */
    public void setHitAndType(FilterType ft) {
        this.hit = true;
        this.filterType = ft;
    }

    /**
     * Return the hit type DUPE or KNOWN
     */
    public FilterType getHitType() {
        return filterType;
    }

    /**
     * It is a dupe if the hit indicator is on and the reporting filter is a DUPE type filter
     */
    public boolean isDupe() {
        return isHit() && getHitType() != null && getHitType() == FilterType.Duplicate;
    }

    /**
     * It is a known file if the hit indicator is on and the reporting filter is a IGNORE type filter
     */
    public boolean isKnown() {
        return isHit() && getHitType() != null && getHitType() == FilterType.Ignore;
    }

    /**
     * Gets the value of crc32
     *
     * @return the value of crc32
     */
    public long getCrc32() {
        return this.crc32;
    }

    /**
     * Sets the value of crc32
     *
     * @param argCrc32 Value to assign to this.crc32
     */
    public void setCrc32(long argCrc32) {
        this.crc32 = argCrc32;
    }

    /**
     * Gets the value of ssdeep
     *
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
     * Gets the value of sha[]
     *
     * @return the value of sha[]
     */
    public byte[] getSha() {
        return getResult("SHA-1");
    }

    /**
     * Get SHA1 result as a string
     */
    public String getShaString() {
        return getResultString("SHA-1");
    }

    /**
     * Get MD5 result as a string
     */
    public String getMd5String() {
        return getResultString("MD5");
    }

    /**
     * Sets the value of sha[]
     *
     * @param argSha Value to assign to this.sha[]
     */
    public void setSha(byte[] argSha) {
        setResult("SHA-1", argSha);
    }

    /**
     * Gets the value of md5[]
     *
     * @return the value of md5[]
     */
    public byte[] getMd5() {
        return getResult("MD5");
    }

    /**
     * Sets the value of md5[]
     *
     * @param argMd5 Value to assign to this.md5[]
     */
    public void setMd5(byte[] argMd5) {
        setResult("MD5", argMd5);
    }

    /**
     * Get any result
     * 
     * @param alg algorithm name
     */
    public byte[] getResult(String alg) {
        return hashComp.get(alg);
    }

    /**
     * Store any result
     * 
     * @param alg name of algorithm
     * @param digest the hash computation
     */
    public void setResult(String alg, byte[] digest) {
        hashComp.put(alg, digest);
    }

    /**
     * Get any result in string form
     * 
     * @param alg the name of the algorithm
     */
    public String getResultString(String alg) {
        if (alg.equals("SSDEEP")) {
            return getSsdeep();
        }

        byte[] digest = hashComp.get(alg);
        if (digest == null) {
            return null;
        }

        return emissary.util.Hexl.toUnformattedHexString(digest);
    }

    /**
     * Get names of all algorithm results present
     */
    public Set<String> getResultNames() {
        return new TreeSet<String>(hashComp.keySet());
    }

    /**
     * Record the name of the item being tracked by this object
     * 
     * @param argItemName the name
     */
    public void setItemName(String argItemName) {
        this.itemName = argItemName;
    }

    /**
     * Get the item name
     */
    public String getItemName() {
        return this.itemName;
    }

    /**
     * Set the name of the reporting filter
     * 
     * @param argFilterName the filter name
     */
    public void setFilterName(String argFilterName) {
        this.filterName = argFilterName;
    }

    /**
     * Get the name of the reporting filter
     */
    public String getFilterName() {
        return this.filterName;
    }
}
