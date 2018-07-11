package emissary.core.blob;

import java.io.File;

/**
 * Capable of providing direct access to the data in file form.
 * 
 * @author adyoun2
 *
 */
public interface IFileProvider {
    /**
     * <p>
     * Get direct access to the data in file form.
     * </p>
     * <p>
     * This method is provided to allow the client to interact directly with APIs that expect data to be provided in
     * File form, where the implementation is capable of providing a more efficient mechanism than the client writing a
     * temporary file itself.
     * </p>
     * <p>
     * <strong>This should only be used where appropriate.</strong>
     * </p>
     * 
     * @return the data in file form, or null if the operation is not possible.
     */
    File getFile();
}
