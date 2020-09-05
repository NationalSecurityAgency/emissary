package emissary.core.blob;

import java.io.File;
import java.io.IOException;

/**
 * Capable of providing direct access to the data in file form.
 *
 */
public interface IFileProvider extends AutoCloseable {

    File getFile() throws IOException;

    static IFileProvider tempFileProvider(IDataContainer container) {
        return new TempFileProvider(container);
    }
}
