package emissary.kff;

import emissary.core.channels.SeekableByteChannelFactory;

import org.apache.commons.collections4.CollectionUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.zip.CRC32;
import javax.annotation.Nullable;

/**
 * ChecksumCalculator is a utility class which computes checksums and message digests.
 * 
 * @see java.util.zip.CRC32 java.util.zip.CRC32
 * @see java.security.MessageDigest java.security.MessageDigest
 */
public class ChecksumCalculator {
    /** Used for CRC32 calculations */
    @Nullable
    private CRC32 crc = null;
    /** Used for SSDEEP calculations */
    @Nullable
    private Ssdeep ssdeep = null;

    /** Used for hash calculations */
    private List<MessageDigest> digest = new ArrayList<>();

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
    public ChecksumCalculator(@Nullable String[] algs) throws NoSuchAlgorithmException {
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
    public ChecksumCalculator(@Nullable Collection<String> algs) throws NoSuchAlgorithmException {
        if (CollectionUtils.isNotEmpty(algs)) {
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

    /**
     * Calculates a CRC32 and a digest on a {@link java.nio.channels.SeekableByteChannel} of data.
     *
     * @param sbcf Provider of data to compute results for
     * @return results of computing the requested hashes on the data
     */
    public ChecksumResults digest(final SeekableByteChannelFactory sbcf) {
        final ChecksumResults res = new ChecksumResults();
        final byte[] b = new byte[1024];

        for (final MessageDigest d : digest) {
            try (final InputStream is = Channels.newInputStream(sbcf.create())) {
                d.reset();

                int bytesRead;
                while ((bytesRead = is.read(b)) != -1) {
                    d.update(b, 0, bytesRead);
                }

                res.setHash(d.getAlgorithm(), d.digest());
            } catch (final IOException ioe) {
                // Ignore
            }
        }

        if (crc != null) {
            try (final InputStream is = Channels.newInputStream(sbcf.create())) {
                crc.reset();

                int bytesRead;
                while ((bytesRead = is.read(b)) != -1) {
                    crc.update(b, 0, bytesRead);
                }

                res.setCrc(crc.getValue());
            } catch (final IOException ioe) {
                // Ignore
            }
        }

        if (ssdeep != null) {
            res.setSsdeep(ssdeep.fuzzy_hash(sbcf));
        }

        return res;
    }
}
