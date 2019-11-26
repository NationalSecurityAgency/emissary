package emissary.kff;

/**
 * A java port of the ssdeep code for "fuzzy hashing". http://ssdeep.sourceforge.net There are a number of ports out
 * there that all look basically the same. This one is from
 * https://opensourceprojects.eu/p/triplecheck/code/23/tree/tool/src/ssdeep/
 * 
 * A new ssdeep hash gets calculated and saved at each level of unwrapping.
 */

public class SpamSumSignature {
    /*****************************************************
     * FIELDS
     *****************************************************/
    private/* uint */long blockSize;
    private byte[] hash1;
    private byte[] hash2;

    /*****************************************************
     * UTILS
     *****************************************************/
    /**
     * <p>
     * Change a string into an array of bytes
     */
    public static byte[] GetBytes(String str) {
        byte[] r = new byte[str.length()];
        for (int i = 0; i < r.length; i++) {
            r[i] = (byte) str.charAt(i);
        }
        return r;
    }

    /**
     * <p>
     * Change a string into an array of bytes
     */
    public static String GetString(byte[] hsh) {
        String r = "";
        for (int i = 0; i < hsh.length; i++) {
            r += (char) hsh[i];
        }
        return r;
    }

    /*****************************************************
     * CONSTRUCTOR
     *****************************************************/

    /**
     * <p>
     * Initializes a new instance of the {@code SpamSumSignature} class.
     * 
     * @param signature The signature.
     */
    public SpamSumSignature(String signature) {
        if (null == signature) {
            throw new IllegalArgumentException("Signature string cannot be null or empty." + "\r\nParameter name: " + "signature");
        }

        int idx1 = signature.indexOf(':');
        int idx2 = signature.indexOf(':', idx1 + 1);

        if (idx1 < 0) {
            throw new IllegalArgumentException("Signature is not valid." + "\r\nParameter name: " + "signature");
        }

        if (idx2 < 0) {
            throw new IllegalArgumentException("Signature is not valid." + "\r\nParameter name: " + "signature");
        }

        blockSize = Integer.parseInt(signature.substring((0), (0) + (idx1)));
        hash1 = GetBytes(signature.substring(idx1 + 1, idx1 + 1 + idx2 - idx1 - 1));
        hash2 = GetBytes(signature.substring(idx2 + 1));
    }

    public SpamSumSignature(long blockSize, byte[] hash1, byte[] hash2) {
        this.blockSize = blockSize;
        this.hash1 = hash1;
        this.hash2 = hash2;
    }

    /*****************************************************
     * METHODS
     *****************************************************/

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SpamSumSignature)) {
            return false;
        }

        return this.equals((SpamSumSignature) obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    public boolean equals(SpamSumSignature other) {
        if (this.blockSize != other.blockSize) {
            return false;
        }

        if (this.hash1.length != other.hash1.length) {
            return false;
        }

        if (this.hash2.length != other.hash2.length) {
            return false;
        }

        for (int idx = 0; idx < hash1.length; idx++) {
            if (this.hash1[idx] != other.hash1[idx]) {
                return false;
            }
        }

        for (int idx = 0; idx < hash2.length; idx++) {
            if (this.hash2[idx] != other.hash2[idx]) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String toString() {
        String hashText1 = GetString(hash1);
        String hashText2 = GetString(hash2);
        return blockSize + ":" + hashText1 + ":" + hashText2;
    }

    /*****************************************************
     * PROPERTIES
     *****************************************************/

    /**
     * <p>
     * Gets the size of the block. Value: The size of the block.
     */
    public/* uint */long getBlockSize() {
        return blockSize;
    }

    /**
     * <p>
     * Gets the first hash part. Value: The first hash part.
     */
    public byte[] getHashPart1() {
        return hash1;
    }

    /**
     * <p>
     * Gets the second hash part. Value: The second hash part.
     */
    public byte[] getHashPart2() {
        return hash2;
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: SpamSumSignature ssdeep1 ssdeep2\nUse the full ssdeep value (xx:yyy:zzz)."
                    + "\nReturns the score between the two values.");
            System.exit(1);
        }
        SpamSumSignature sss1 = new SpamSumSignature(args[0]);
        SpamSumSignature sss2 = new SpamSumSignature(args[1]);
        Ssdeep ssdeep = new Ssdeep();
        System.out.println("" + ssdeep.Compare(sss1, sss2));
    }
}
