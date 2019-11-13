package emissary.util;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import emissary.core.IBaseDataObject;

/**
 * This class provides routines for approximating the RAM size of Emissary objects - primarily the
 * {@link IBaseDataObject}.
 */
public class SizeUtil {

    /**
     * A flag to indicate whether we are running 32 or 64 bit JVM. Note that this is the JVM bits and not the OS bits
     */
    private static boolean arch64 = false;

    /**
     * A reference/pointer size in a non-compressed Ops JVM. Default is 32-bit = 4 bytes
     */
    private static long refSize = 4L;

    /**
     * Object overhead
     */
    private static long objOverhead = 8L;

    static {
        if (System.getProperty("os.arch").contains("64")) {
            arch64 = true;
            refSize = 8L;
        }
    }

    /**
     * <i>Approximate</i> the amount of RAM consumed by a String.
     * 
     * See https://www.cs.virginia.edu/kim/publicity/pldi09tutorials/memory-efficient-java-tutorial.pdf Slide 26.
     * 
     * This calculation is affected by the JVM architecture and whether
     * https://wikis.oracle.com/display/HotSpotInternals/CompressedOops are enabled.
     * 
     * @param str - the String to approximate
     * @return The approximate size, in bytes, in RAM for str
     */
    public static long sizeof(String str) {
        if (str == null) {
            return 0L;
        }
        if (arch64) {
            return (str.length() * 2) + 52; // 2 bytes per UTF-16, 52 for JVM overhead & bookkeeping, 64-bit arch
        } else {
            return (str.length() * 2) + 48; // 2 bytes per UTF-16, 48 for JVM overhead & bookkeeping, 32-bit arch
        }
    }

    /**
     * Approximates the amount of RAM consumed by the "extracted records" in a single IBaseDataObject. This is typically the
     * case when the framework gets eventing datasets. These extracted records are usually treated specially and not run
     * through the processing pipelines proper, but on output do appear as proper child IBaseDataObjects. In the case of a
     * large dataset, these extracted records can consume huge amounts of RAM.
     * 
     * @param ibdo - The IBaseDataObject to approximate
     * @return - The approximate size, in bytes, in RAM for extracted records of an IBaseDataObject
     */
    public static long getExtractedRecordRAMSize(IBaseDataObject ibdo) {
        if (ibdo == null) {
            return 0L;
        }

        long totalSize = 0L;

        // Count up the size of any extracted children
        if (ibdo.hasExtractedRecords()) {
            List<IBaseDataObject> childObjList = ibdo.getExtractedRecords();
            if (childObjList != null) {
                for (IBaseDataObject child : childObjList) {
                    if (child != null) {
                        totalSize += sizeof(child);
                        totalSize += objOverhead + refSize; // For the pointer to child in childObjList (Object
                                                            // overhead)
                    }
                }
            }
        }

        return totalSize;
    }

    /**
     * Approximate the amount of RAM consumed by the various "payloads" of an IBaseDataObject. In this case, a payload
     * refers to the header, footer, data (primary view), and all the alternate views.
     * 
     * @param ibdo - The IBaseDataObject to approximate
     * @return - The approximate size, in bytes, in RAM for the IBaseDataObject
     */
    public static long getPayloadRAMSize(IBaseDataObject ibdo) {
        if (ibdo == null) {
            return 0L;
        }

        long totalSize = 0L;

        // We don't concern ourselves with object references here, because they are likely
        // tiny compared to the various "payloads" and altViews. A more accurate
        // version of this method would factor those in, but they are likely to be
        // negligible

        // Primary view (potentially gigantic)
        totalSize += ibdo.dataLength(); // This always returns an int

        // Header and footer size (probably not big)
        if (ibdo.footer() != null) {
            totalSize += ibdo.footer().length;
        }
        if (ibdo.header() != null) {
            totalSize += ibdo.header().length;
        }

        // Size up the alternative views
        for (Map.Entry<String, byte[]> altView : ibdo.getAlternateViews().entrySet()) {
            if (altView.getValue() != null) {
                totalSize += altView.getValue().length;
            }
        }

        return totalSize;
    }

    /**
     * Approximate the amount of RAM consumed by an individual {@link IBaseDataObject}. The purpose of this method is to
     * approximate the RAM that will be consumed by a corresponding serialized/deserialized object, hence not all aspects of
     * the {@link IBaseDataObject} are considered. Additionally, this method does not include all the "outputtable" logic
     * that may be present in output filter, which could make the actual size smaller than is reported by this method.
     * 
     * @param ibdo The IBaseDataObject to approximate the size of
     * @return The approximate size, in bytes, in RAM for the IBaseDataObject
     */
    public static long sizeof(IBaseDataObject ibdo) {
        if (ibdo == null) {
            return 0L;
        }

        long totalSize = 0L;

        // Primary view (potentially gigantic)
        totalSize += getPayloadRAMSize(ibdo);

        // Factor in the extracted records
        totalSize += getExtractedRecordRAMSize(ibdo);

        if (ibdo.getParameters() == null) {
            return totalSize;
        } // This should never be null, but still check

        // Finally, estimate the size of the metadata parameters, assuming the underlying Strings are UTF-16
        for (Map.Entry<String, Collection<Object>> entry : ibdo.getParameters().entrySet()) {

            // Note: The core multimap in Emissary has CharSequence as keys. This is to allow for more
            // flexibility in key design. Under the hood, they are still Strings so the following
            // routines are safe for the time-being, but will need to be changed if the keys change

            // Get the size of the key
            String k = entry.getKey();
            totalSize += sizeof(k);
            totalSize += refSize;

            // Similar to the above, the values in Emissary are generic Objects although almost all of them
            // are Strings. That is why the following check is made in the for loop.

            // Get the size of the List of values
            Collection<Object> values = entry.getValue();
            for (Object v : values) {
                if (v instanceof String) {
                    totalSize += sizeof((String) v);
                    totalSize += refSize;
                }
                // TODO: factor in non-String objects
                // but this is not that important since there
                // are not many of them and will change the overall
                // size minimally at this time
            }
        }

        return totalSize;
    }

    /**
     * Estimate the size, in bytes, of the RAM of an entire {@link IBaseDataObject} family tree. This is simply the sum of
     * the sizes of the individual members of the family tree.
     *
     * @param familyTree - {@code List<IBaseDataObject>} representing the family tree for a document object
     * @return - the approximate size, in bytes, in RAM for the familyTree
     */
    public static long sizeof(List<IBaseDataObject> familyTree) {
        if (familyTree == null) {
            return 0L;
        }

        long totalSize = 0L;
        for (IBaseDataObject ibdo : familyTree) {
            if (ibdo != null) {
                totalSize += sizeof(ibdo);
                totalSize += objOverhead + refSize; // For the pointer to ibdo in familyTree (Object overhead)
            }
        }
        return totalSize;
    }

    /** This class is not meant to be instantiated. */
    private SizeUtil() {}
}
