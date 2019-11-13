package emissary.kff;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * KffFile provides access to the known file filter data. The NIST/NSRL data is a CSV file with other information. It
 * must be preprocessed in order for this class to access it. The input file for this class must consist of a sorted
 * list of known values, where a known value is the CRC32 appended to the SHA-1. This file must be a binary file, so
 * each record will be 24 bytes long (20-byte SHA + 4-byte CRC). The CRC should be big endian.
 *
 * Implementation notes: The binary input file is too big to read into memory, so we implement a binary search on the
 * file itself. This is why the records must be sorted, and it will improve performance if only unique records are
 * generated as well. This class assumes JDK1.4+ and memory maps the file. For earlier versions of the JDK, we can seek
 * through the RandomAccessFile instead but performance isn't as good. The file cannot be larger than 2^31 (2 GB)
 * because that is the maximum length of the mapped ByteBuffer.
 *
 * Update: 11/27/2006 Having out-of-memory issues with all the memory mapping we're trying to do, (multiple KFFs, plus
 * HotSpot), so try using just RandomAccessFile instead.
 */
public class KffFile implements KffFilter {
    private Logger logger;

    /** File containing SHA-1/CRC32 results of known files */
    protected RandomAccessFile knownFile;

    /** Byte buffer that is mapped to the above file */
    protected ByteBuffer mappedBuf;

    /** Initial value of high index for binary search */
    private int bSearchInitHigh;

    protected int RECORD_LENGTH = 24;
    protected int recordLength = RECORD_LENGTH;

    /** String logical name for this filter */
    protected String filterName = "UNKNOWN";

    protected FilterType ftype = FilterType.Unknown;

    protected String myPreferredAlgorithm = "SHA-1";

    /**
     * Creates a new instance of KffFile
     *
     * @param filename Name of binary file containing sorted RECORD_LENGTH records that are the hash codes possibly
     *        concatenated with the CRC-32
     * @param filterName the name of this filter
     * @param ftype type of this filter
     * @throws IOException if thrown by file I/O
     */
    public KffFile(String filename, String filterName, FilterType ftype) throws IOException {
        this(filename, filterName, ftype, 24);
    }


    /**
     * Creates a new instance of KffFile
     *
     * @param filename Name of binary file containing sorted RECORD_LENGTH records that are the hash codes possibly
     *        concatenated with the CRC-32
     * @param filterName the name of this filter
     * @param ftype type of this filter
     * @param recordLength fixed record length in file
     * @throws IOException if thrown by file I/O
     */
    public KffFile(String filename, String filterName, FilterType ftype, int recordLength) throws IOException {
        this.ftype = ftype;
        this.filterName = filterName;
        this.recordLength = recordLength;

        // Set logger to run time class
        logger = LoggerFactory.getLogger(this.getClass());

        // Open file in read-only mode
        knownFile = new RandomAccessFile(filename, "r");

        // Map the entire file in read-only mode
        // FileChannel channel = knownFile.getChannel();
        // mappedBuf = channel.map(FileChannel.MapMode.READ_ONLY,
        // 0, knownFile.length());

        // Initial high value for binary search is largest index
        bSearchInitHigh = ((int) knownFile.length() / recordLength) - 1;

        logger.debug("KFF File " + filename + " has " + (bSearchInitHigh + 1) + " records");
    }

    /**
     * Return the filter name
     */
    @Override
    public String getName() {
        return filterName;
    }


    /**
     * Return the filter type
     */
    @Override
    public FilterType getFilterType() {
        return ftype;
    }

    /**
     * Set the filter type
     *
     * @param f the new type
     */
    public void setFilterType(FilterType f) {
        ftype = f;
    }

    /**
     * Set the preferred algorithm to match what is in the mmaped file
     *
     * @param alg the new algorithm to use
     */
    public void setPreferredAlgorithm(String alg) {
        myPreferredAlgorithm = alg;
    }

    /**
     * Return the algorigthm being used
     */
    public String getPreferredAlgorithm() {
        return myPreferredAlgorithm;
    }

    /**
     * Performs a binary search on the file to see if a given HASH/CRC is in the list.
     *
     * @param hash Result of HASH calculation
     * @param crc Result of CRC calculation
     * @return true if the record is in the list, false if it isn't
     */
    private boolean binaryFileSearch(byte[] hash, long crc) {
        if (hash == null && myPreferredAlgorithm != null) {
            logger.error("Unable to get digest computation for " + myPreferredAlgorithm);
            return false;
        }

        // Initialize indexes for binary search
        int low = 0;
        int high = bSearchInitHigh;

        /* Buffer to hold a record */
        byte[] record = new byte[recordLength];

        // Search until the indexes cross
        while (low <= high) {
            // Calculate the midpoint
            int mid = (low + high) >> 1;

            // Multiply the index by the record length to get the buffer
            // position, and read the record
            // mappedBuf.position(record.length * mid);
            // mappedBuf.get(record);
            try {
                knownFile.seek(record.length * mid);
                int count = knownFile.read(record);
                if (count != record.length) {
                    logger.warn("Short read on KffFile at " + (record.length * mid) + " read " + count + " expected " + record.length);
                    return false;
                }
            } catch (IOException x) {
                logger.warn("Exception reading KffFile: " + x);
                return false;
            }

            // Compare the record with the target. Adjust the indexes accordingly.
            int c = compare(record, hash, crc);
            if (c < 0) {
                high = mid - 1;
            } else if (c > 0) {
                low = mid + 1;
            } else {
                return true;
            }
        }

        // not found
        return false;
    }

    /**
     * Compares the given hash/crc to the one in the record.
     *
     * @param record bytes from the kff binary file, one record long
     * @param hash HASH to compare to record
     * @param crc CRC to compare to record
     * @return &lt;0 if given value is less than record, &gt;0 if given value is greater than record, 0 if they match
     */
    private int compare(byte[] record, byte[] hash, long crc) {
        int i;

        // logger.debug("record.length " + record.length +
        // / " hash.length " + hash.length);

        // Compare the HASHs first. We can't compare the bytes directly
        // because a Java byte is signed and may generate the wrong
        // result. We must convert to integers and then mask off the
        // sign bits to get proper results.
        for (i = 0; i < hash.length; i++) {
            int ihash = hash[i] & 0xff;
            int irec = record[i] & 0xff;
            if (ihash < irec) {
                return -1;
            } else if (ihash > irec) {
                return 1;
            }
        }

        // If the HASHs match, check the CRCs.
        if (crc != -1L) {
            for (int j = 24; i < record.length; i++, j -= 8) {
                int icrc = ((int) crc >> j) & 0xff;
                int irec = record[i] & 0xff;
                if (icrc < irec) {
                    return -1;
                } else if (icrc > irec) {
                    return 1;
                }
            }
        }
        return 0;
    }

    @Override
    public boolean check(String fname, ChecksumResults csum) throws Exception {
        byte[] hash = csum.getHash(myPreferredAlgorithm);
        if (hash == null) {
            logger.warn("Filter cannot be used, " + myPreferredAlgorithm + " not computed on " + fname);
            return false;
        }
        return binaryFileSearch(csum.getHash(myPreferredAlgorithm), csum.getCrc());
    }

    public static void main(String[] args) throws Exception {
        KffChain kff = new KffChain();
        KffFile kfile = new KffFile(args[0], "TEST", FilterType.Ignore);
        kfile.setPreferredAlgorithm("SHA-1");
        kff.addFilter(kfile);
        kff.addAlgorithm("CRC32");
        kff.addAlgorithm("SSDEEP");
        kff.addAlgorithm("MD5");
        kff.addAlgorithm("SHA-1");
        kff.addAlgorithm("SHA-256");

        for (int i = 1; i < args.length; i++) {
            FileInputStream is = new FileInputStream(args[i]);
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();

            KffResult r = kff.check(args[i], buffer);
            System.out.println(args[i] + ": " + r.isKnown() + " - " + r.getShaString() + " - " + r.getCrc32());
        }
    }
}
