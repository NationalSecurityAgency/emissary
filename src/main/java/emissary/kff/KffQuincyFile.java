package emissary.kff;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * KffQuincyFile compares files against the Quincy KFF dataset. The dataset only contains MD5 sums to that's our
 * preferred algorithm, and gives a record length of 16 bytes with no CRC
 */
public class KffQuincyFile extends KffFile {
    /**
     * Create the Quincy Filter
     * 
     * @param filename name of fixed record length file to mmap
     * @param filterName name for this filter to report hits
     * @param ftype type of filter
     */
    public KffQuincyFile(String filename, String filterName, FilterType ftype) throws IOException {
        super(filename, filterName, ftype, 16);
        super.myPreferredAlgorithm = "MD5";
    }

    public static void main(String[] args) throws Exception {
        KffChain kff = new KffChain();
        KffFile kfile = new KffQuincyFile(args[0], "QUINCYTEST", FilterType.Ignore);
        kff.addFilter(kfile);
        kff.addAlgorithm("CRC32");
        kff.addAlgorithm("MD5");
        kff.addAlgorithm("SHA-1");
        kff.addAlgorithm("SHA-256");

        for (int i = 1; i < args.length; i++) {
            FileInputStream is = new FileInputStream(args[i]);
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();

            KffResult r = kff.check(args[i], buffer);
            System.out.println(args[i] + ": " + r.isKnown() + " - " + r.getMd5String());
        }
    }
}
