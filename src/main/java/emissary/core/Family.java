package emissary.core;

/**
 * Family holds constants and methods related to the association between BaseDataObjects. It will be used in the future
 * to assist with naming new attachments The non-static methods on this class are not MT safe
 */
public class Family {
    /** The family separator string {@value} */
    public static final String SEP = "-att-";

    /** Attachments start from this index counter */
    public static final int FIRST_ATTACHMENT = 1;

    /** Our family name */
    protected String basename;

    /** Next attachment number to be used */
    protected int num = FIRST_ATTACHMENT;

    /**
     * Create an attachment namer for the basename specified
     * 
     * @param basename parent name
     */
    public Family(final String basename) {
        this.basename = basename;
    }

    /**
     * Create an attachment namer for the basename specified
     * 
     * @param basename parent name
     * @param firstNum number to start with
     */
    public Family(final String basename, final int firstNum) {
        this.basename = basename;
        this.num = firstNum;
    }

    /**
     * Get the next attachment name
     */
    public String next() {
        return this.basename + SEP + this.num++;
    }

    /**
     * Get the current attachment number i.e. next to be used
     */
    public int getNextNumber() {
        return this.num;
    }

    /**
     * Start a family nested below the current item
     * 
     * @return a Family namer positioned one level deeper than this one
     */
    public Family child() {
        return new Family(this.basename + SEP + this.num);
    }

    /**
     * Return the separator string from a method
     */
    public static String sep() {
        return SEP;
    }

    /**
     * Return the separator string for the specified birthorder e.g. sep(5) ==&gt; "-att-5"
     * 
     * @param birthorder the number for the attachment to name
     */
    public static String sep(final int birthorder) {
        return SEP + birthorder;
    }

    /**
     * Encode the logic that attachments start from 1
     */
    public static String initial() {
        return SEP + FIRST_ATTACHMENT;
    }
}
