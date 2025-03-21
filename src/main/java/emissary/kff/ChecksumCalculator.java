package emissary.kff;

import emissary.core.channels.SeekableByteChannelFactory;

import jakarta.annotation.Nullable;
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

/**
 * <p>
 * ChecksumCalculator is a utility class which computes SHA-1 and CRC32 checksums for use with a {@link KffChain} to
 * support document similarity analysis. It can also be configured to compute various other message digests or SSDEEP
 * hashes over the same data.
 * </p>
 * <p>
 * NOTE: This class is to support Kff ONLY. It is not meant to provide cryptographically secure hashes or checksums.
 * </p>
 * 
 * @see KffFile
 * @see java.util.zip.CRC32 java.util.zip.CRC32
 * @see java.security.MessageDigest java.security.MessageDigest
 */
class ChecksumCalculator {
    /** Used for CRC32 calculations */
    @Nullable
    private CRC32 crc = null;
    /** Used for SSDEEP calculations */
    @Nullable
    private Ssdeep ssdeep = null;

    /** Used for hash calculations */
    private final List<MessageDigest> digest = new ArrayList<>();

    /**
     * Constructor initializes SHA-1 generator and turns on the CRC32 processing as well
     * 
     * @throws NoSuchAlgorithmException if the SHA algorithm isn't available
     */
    ChecksumCalculator() throws NoSuchAlgorithmException {
        this("SHA-1", true);
    }

    /**
     * Constructor initializes specified algorithm
     * 
     * @param alg string name of algorithm, e.g. SHA
     * @param useCrc true if CRC32 should be calculated
     * @throws NoSuchAlgorithmException if the algorithm isn't available
     */
    ChecksumCalculator(String alg, boolean useCrc) throws NoSuchAlgorithmException {
        this(List.of(alg));
        setUseCrc(useCrc);
    }

    /**
     * Constructor initializes specified set of algorithms
     * 
     * @param algs array of String algorithm names, put CRC32 on list to enable
     * @throws NoSuchAlgorithmException if an algorithm isn't available
     */
    @Deprecated
    @SuppressWarnings("AvoidObjectArrays")
    ChecksumCalculator(@Nullable String[] algs) throws NoSuchAlgorithmException {
        if (algs != null && algs.length > 0) {
            for (String alg : algs) {
                if (alg.equals("CRC32")) {
                    setUseCrc(true);
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
    ChecksumCalculator(@Nullable Collection<String> algs) throws NoSuchAlgorithmException {
        if (CollectionUtils.isNotEmpty(algs)) {
            for (String alg : algs) {
                if (alg.equals("CRC32")) {
                    setUseCrc(true);
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
    public boolean getUseCrc() {
        return crc != null;
    }

    /**
     * Turn on or off CRC processing
     * 
     * @param use true if CRC processing is desired
     */
    public void setUseCrc(boolean use) {
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
        return ssdeep != null;
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
            res.setSsdeep(ssdeep.fuzzyHash(buffer));
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
            try (InputStream is = Channels.newInputStream(sbcf.create())) {
                d.reset();

                int bytesRead;
                while ((bytesRead = is.read(b)) != -1) {
                    d.update(b, 0, bytesRead);
                }

                res.setHash(d.getAlgorithm(), d.digest());
            } catch (final IOException ignored) {
                // Ignore
            }
        }

        if (crc != null) {
            try (InputStream is = Channels.newInputStream(sbcf.create())) {
                crc.reset();

                int bytesRead;
                while ((bytesRead = is.read(b)) != -1) {
                    crc.update(b, 0, bytesRead);
                }

                res.setCrc(crc.getValue());
            } catch (final IOException ignored) {
                // Ignore
            }
        }

        if (ssdeep != null) {
            res.setSsdeep(ssdeep.fuzzyHash(sbcf));
        }

        return res;
    }
}
