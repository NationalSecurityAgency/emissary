package emissary.kff;

import emissary.core.IBaseDataObject;
import emissary.core.IBaseDataObject.MergePolicy;
import emissary.core.channels.SeekableByteChannelFactory;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * A helpful class to set and evaluate the KFF details of a BaseDataObject
 */
public class KffDataObjectHandler {
    // Parameter names for data object param map
    public static final String KFF_PARAM_BASE = "CHECKSUM_";
    public static final String KFF_PARAM_CRC32 = KFF_PARAM_BASE + "CRC32";
    public static final String KFF_PARAM_MD5 = KFF_PARAM_BASE + "MD5";
    public static final String KFF_PARAM_SHA1 = KFF_PARAM_BASE + "SHA-1";
    public static final String KFF_PARAM_SHA256 = KFF_PARAM_BASE + "SHA-256";
    public static final String KFF_PARAM_SHA384 = KFF_PARAM_BASE + "SHA-384";
    public static final String KFF_PARAM_SHA512 = KFF_PARAM_BASE + "SHA-512";
    public static final String KFF_PARAM_SSDEEP = KFF_PARAM_BASE + "SSDEEP";
    public static final String KFF_PARAM_DUPE_HIT = KFF_PARAM_BASE + "KNOWN_FILE";
    public static final String KFF_PARAM_PARENT_HIT = KFF_PARAM_BASE + "PARENT_IS_KNOWN_FILE";
    public static final String KFF_PARAM_KNOWN_FILTER_NAME = KFF_PARAM_BASE + "FILTERED_BY";
    public static final String KFF_PARAM_DUPE_FILTER_NAME = KFF_PARAM_BASE + "KNOWN_BY";
    public static final String KFF_DUPE_CURRENT_FORM = "KNOWN_FILE";
    public static final String MD5_ORIGINAL = "MD5_ORIGINAL";

    // Our kff impl
    protected KffChain kff = KffChainLoader.getChainInstance();

    // Logger
    protected static final Logger logger = LoggerFactory.getLogger(KffDataObjectHandler.class);

    // Policy on dupes
    protected boolean truncateKnownData = true;
    protected boolean setFormOnKnownData = true;
    protected boolean setFileTypeOnKnown = true;

    // Policy values for constructor
    public static final boolean TRUNCATE_KNOWN_DATA = true;
    public static final boolean KEEP_KNOWN_DATA = false;

    public static final boolean SET_FORM_WHEN_KNOWN = true;
    public static final boolean NO_FORM_CHANGE_WHEN_KNOWN = false;

    public static final boolean SET_FILE_TYPE = true;
    public static final boolean NO_SET_FILE_TYPE = false;

    /**
     * Create with default policy
     */
    public KffDataObjectHandler() {}

    /**
     * Create with policy
     */
    public KffDataObjectHandler(boolean truncateKnownData, boolean setFormOnKnownData, boolean setFileTypeOnKnown) {
        this.truncateKnownData = truncateKnownData;
        this.setFormOnKnownData = setFormOnKnownData;
        this.setFileTypeOnKnown = setFileTypeOnKnown;
    }

    /**
     * Compute the configure hashes and return as a map Also include entries indicating the know file or duplicate file
     * status if so configured
     * 
     * @param data the bytes to hash
     * @param name th name of the data (for reporting)
     * @return parameter entries suitable for a BaseDataObject
     */
    public Map<String, String> hashData(byte[] data, String name) {
        return hashData(data, name, "");
    }

    /**
     * Compute the configure hashes and return as a map Also include entries indicating the know file or duplicate file
     * status if so configured
     * 
     * @param data the bytes to hash
     * @param name th name of the data (for reporting)
     * @param prefix prepended to hash name entries
     * @return parameter entries suitable for a BaseDataObject
     */
    public Map<String, String> hashData(@Nullable byte[] data, String name, @Nullable String prefix) {
        Map<String, String> results = new HashMap<>();

        if (prefix == null) {
            prefix = "";
        }

        if (data != null && data.length > 0) {
            try {
                KffResult kffCheck = kff.check(name, data);

                // Store all computed results in data object params
                for (String alg : kffCheck.getResultNames()) {
                    results.put(prefix + KFF_PARAM_BASE + alg, kffCheck.getResultString(alg));
                }

                // Set params if we have a hit
                if (kffCheck.isKnown()) {
                    results.put(prefix + KFF_PARAM_KNOWN_FILTER_NAME, kffCheck.getFilterName());
                }
                if (kffCheck.isDupe()) {
                    results.put(prefix + KFF_PARAM_DUPE_FILTER_NAME, kffCheck.getFilterName());
                }
            } catch (Exception kffex) {
                logger.warn("Unable to compute kff on " + name, kffex);
            }
        }
        return results;
    }

    /**
     * Compute the configure hashes and return as a map Also include entries indicating the know file or duplicate file
     * status if so configured
     * 
     * @param sbcf the data to hash
     * @param name th name of the data (for reporting)
     * @param prefix prepended to hash name entries
     * @return parameter entries suitable for a BaseDataObject
     * @throws IOException if the data can't be read
     * @throws NoSuchAlgorithmException if the checksum can't be computed
     */
    public Map<String, String> hashData(final SeekableByteChannelFactory sbcf, final String name, String prefix)
            throws IOException, NoSuchAlgorithmException {
        final Map<String, String> results = new HashMap<>();

        if (prefix == null) {
            prefix = "";
        }

        if (sbcf != null) {
            try (final SeekableByteChannel sbc = sbcf.create()) {
                if (sbc.size() > 0) {
                    final KffResult kffCheck = kff.check(name, sbcf);

                    // Store all computed results in data object params
                    for (String alg : kffCheck.getResultNames()) {
                        results.put(prefix + KFF_PARAM_BASE + alg, kffCheck.getResultString(alg));
                    }

                    // Set params if we have a hit
                    if (kffCheck.isKnown()) {
                        results.put(prefix + KFF_PARAM_KNOWN_FILTER_NAME, kffCheck.getFilterName());
                    }
                    if (kffCheck.isDupe()) {
                        results.put(prefix + KFF_PARAM_DUPE_FILTER_NAME, kffCheck.getFilterName());
                    }
                }
            }
        }

        return results;
    }

    /**
     * Compute the hash of a data object's data
     * 
     * @param d the data object
     */
    public void hash(@Nullable final IBaseDataObject d) {
        try {
            hash(d, false);
        } catch (NoSuchAlgorithmException | IOException e) {
            // Do nothing
        }
    }

    /**
     * Compute the hash of a data object's data
     * 
     * @param d the data object
     * @param useSbc use the {@link SeekableByteChannel} interface
     * @throws IOException if the data can't be read
     * @throws NoSuchAlgorithmException if the checksum can't be computed
     */
    public void hash(@Nullable final IBaseDataObject d, final boolean useSbc) throws NoSuchAlgorithmException, IOException {

        if (d == null) {
            return;
        }

        String originalMD5 = captureOriginalMD5BeforeRehashing(d);
        try {
            removeHash(d);

            // Compute and add the hashes
            if (useSbc && d.getChannelSize() > 0) {
                d.putParameters(hashData(d.getChannelFactory(), d.shortName(), ""), MergePolicy.DROP_EXISTING);
            } else if (!useSbc && d.dataLength() > 0) {
                d.putParameters(hashData(d.data(), d.shortName()), MergePolicy.DROP_EXISTING);
            } else {
                return; // NOSONAR
            }
        } finally {
            // preserve the original MD5 only if 1) we hadn't already done so and 2) rehashing produced a new MD5 value
            if (!d.hasParameter(MD5_ORIGINAL) && previouslyComputedMd5HasChanged(d, originalMD5)) {
                d.setParameter(MD5_ORIGINAL, originalMD5);
            }
        }

        // Set params if we have a hit
        if (d.hasParameter(KFF_PARAM_KNOWN_FILTER_NAME)) {
            if (setFileTypeOnKnown) {
                d.setFileType(KFF_DUPE_CURRENT_FORM);
            }
            if (setFormOnKnownData) {
                d.replaceCurrentForm(KFF_DUPE_CURRENT_FORM);
            }
            if (truncateKnownData) {
                d.setData(null);
            }
        }
    }

    /**
     * Capture the current CHECKSUM_MD5 parameter value, unless we've already preserved one in the MD5_ORIGINAL parameter
     *
     * @param d IBaseDataObject being processed
     */
    static String captureOriginalMD5BeforeRehashing(IBaseDataObject d) {
        // If the IBDO already has an MD5_ORIGINAL parameter, return null.
        if (d.hasParameter(MD5_ORIGINAL)) {
            return null;
        }

        if (d.hasParameter(KFF_PARAM_MD5)) {
            var paramValue = d.getParameter(KFF_PARAM_MD5);
            if (!paramValue.isEmpty() && paramValue.get(0) != null) {
                String originalMD5 = paramValue.get(0).toString();
                // only preserve the KFF_PARAM_MD5 value if it's not blank
                return StringUtils.trimToNull(originalMD5);
            }
        }
        return null;
    }

    /**
     * Returns true if the original MD5 is non-blank and is different from the current MD5 value
     * 
     * @param d IBaseDataObject being processed
     * @param originalMD5 previously computed MD5 checksum
     * @return true if the original MD5 is non-blank and is different from the current MD5 value
     */
    static boolean previouslyComputedMd5HasChanged(IBaseDataObject d, String originalMD5) {
        if (StringUtils.isNotBlank(originalMD5) && d.hasParameter(KFF_PARAM_MD5)) {
            var paramValue = d.getParameter(KFF_PARAM_MD5);
            if (!paramValue.isEmpty() && paramValue.get(0) != null) {
                String currentMD5 = paramValue.get(0).toString();
                return originalMD5.equals(currentMD5);
            }
        }
        return false;
    }

    /**
     * Parent info has been copied in and must be reset for the child context
     * 
     * @param d the data object
     */
    public static void parentToChild(IBaseDataObject d) {
        Object parentDupe = d.getParameter(KFF_PARAM_DUPE_HIT);
        if (parentDupe != null) {
            d.deleteParameter(KFF_PARAM_DUPE_HIT);
            d.putParameter(KFF_PARAM_PARENT_HIT, parentDupe);
        }
    }

    /**
     * Determine if a hash value is present
     * 
     * @param d the payload
     */
    public static boolean hashPresent(IBaseDataObject d) {
        return d.getParameter(KFF_PARAM_MD5) != null || d.getParameter(KFF_PARAM_SHA1) != null || d.getParameter(KFF_PARAM_SHA256) != null
                || d.getParameter(KFF_PARAM_SHA384) != null || d.getParameter(KFF_PARAM_SHA512) != null;
    }

    /**
     * Remove all hash params from the payload
     * 
     * @param d the payload
     */
    public static void removeHash(IBaseDataObject d) {
        d.deleteParameter(KFF_PARAM_CRC32);
        d.deleteParameter(KFF_PARAM_MD5);
        d.deleteParameter(KFF_PARAM_SHA1);
        d.deleteParameter(KFF_PARAM_SHA256);
        d.deleteParameter(KFF_PARAM_SHA384);
        d.deleteParameter(KFF_PARAM_SHA512);
        d.deleteParameter(KFF_PARAM_SSDEEP);
    }

    /**
     * Get the SHA-1 hash value. It's the default. The standard. Don't leave home without it.
     * 
     * @param d the payload
     */
    public static String getHashValue(IBaseDataObject d) {
        return getSha1Value(d);
    }

    /**
     * Set the supplied hash into the right hash slot We can determine the right slot based on the value of the hash
     * 
     * @param d the payload
     * @param hash the value
     */
    public static void setHashValue(IBaseDataObject d, @Nullable String hash) {
        int hl = hash != null ? hash.length() : -1;

        if (hash != null && hash.indexOf(":") > -1) {
            setSsdeepValue(d, hash);
            return;
        }

        switch (hl) {
            case 32:
                setMd5Value(d, hash);
                break;
            case 40:
                setSha1Value(d, hash);
                break;
            case 64:
                setSha256Value(d, hash);
                break;
            case 96:
                setSha384Value(d, hash);
                break;
            case 128:
                setSha512Value(d, hash);
                break;
            default:
                logger.warn("Hash value {} doesn't work here", hl);
        }
    }

    /**
     * Get the MD5 hash value
     * 
     * @param d the payload
     */
    public static String getMd5Value(IBaseDataObject d) {
        return d.getStringParameter(KFF_PARAM_MD5);
    }

    /**
     * Set the MD5 hash value
     * 
     * @param d the payload
     * @param hash the value
     */
    public static void setMd5Value(IBaseDataObject d, String hash) {
        d.setParameter(KFF_PARAM_MD5, hash);
    }

    /**
     * Get the SHA-1 hash value
     * 
     * @param d the payload
     */
    public static String getSha1Value(IBaseDataObject d) {
        return d.getStringParameter(KFF_PARAM_SHA1);
    }

    /**
     * Set the SHA-1 hash value
     * 
     * @param d the payload
     * @param hash the value
     */
    public static void setSha1Value(IBaseDataObject d, String hash) {
        d.setParameter(KFF_PARAM_SHA1, hash);
    }

    /**
     * Get the SHA-256 hash value
     * 
     * @param d the payload
     */
    public static String getSha256Value(IBaseDataObject d) {
        return d.getStringParameter(KFF_PARAM_SHA256);
    }

    /**
     * Set the SHA-256 hash value
     * 
     * @param d the payload
     * @param hash the value
     */
    public static void setSha256Value(IBaseDataObject d, String hash) {
        d.setParameter(KFF_PARAM_SHA256, hash);
    }

    /**
     * Get the SHA-384 hash value
     * 
     * @param d the payload
     */
    public static String getSha384Value(IBaseDataObject d) {
        return d.getStringParameter(KFF_PARAM_SHA384);
    }

    /**
     * Set the SHA-384 hash value
     * 
     * @param d the payload
     * @param hash the value
     */
    public static void setSha384Value(IBaseDataObject d, String hash) {
        d.setParameter(KFF_PARAM_SHA384, hash);
    }

    /**
     * Get the SHA-512 hash value
     * 
     * @param d the payload
     */
    public static String getSha512Value(IBaseDataObject d) {
        return d.getStringParameter(KFF_PARAM_SHA512);
    }

    /**
     * Set the SHA-512 hash value
     * 
     * @param d the payload
     * @param hash the value
     */
    public static void setSha512Value(IBaseDataObject d, String hash) {
        d.setParameter(KFF_PARAM_SHA512, hash);
    }

    /**
     * Get the SSDEEP hash value
     * 
     * @param d the payload
     */
    public static String getSsdeepValue(IBaseDataObject d) {
        return d.getStringParameter(KFF_PARAM_SSDEEP);
    }

    /**
     * Set the SSDEEP hash value
     * 
     * @param d the payload
     * @param hash the value
     */
    public static void setSsdeepValue(IBaseDataObject d, String hash) {
        d.setParameter(KFF_PARAM_SSDEEP, hash);
    }

    /**
     * Get the best of the available hashes, might be null of none are enabled
     * 
     * @param d the payload
     * @return the best hash value we have
     */
    public static String getBestAvailableHash(IBaseDataObject d) {
        if (d.getParameter(KFF_PARAM_SHA512) != null) {
            return d.getStringParameter(KFF_PARAM_SHA512);
        }
        if (d.getParameter(KFF_PARAM_SHA384) != null) {
            return d.getStringParameter(KFF_PARAM_SHA384);
        }
        if (d.getParameter(KFF_PARAM_SHA256) != null) {
            return d.getStringParameter(KFF_PARAM_SHA256);
        }
        if (d.getParameter(KFF_PARAM_SHA1) != null) {
            return d.getStringParameter(KFF_PARAM_SHA1);
        }
        return d.getStringParameter(KFF_PARAM_MD5);
    }

}
