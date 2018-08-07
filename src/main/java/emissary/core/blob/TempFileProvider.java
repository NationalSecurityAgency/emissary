package emissary.core.blob;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;

import org.apache.commons.io.IOUtils;

class TempFileProvider implements IFileProvider {

    private File f;
    private final IDataContainer container;
    private long creationDate;

    public TempFileProvider(IDataContainer container) {
        this.container = container;
    }

    @Override
    public void close() throws Exception {
        if (f.lastModified() > creationDate) {
            try (FileInputStream fis = new FileInputStream(f); OutputStream os = Channels.newOutputStream(container.newChannel(f.length()))) {
                IOUtils.copyLarge(fis, os);
            }
        }
        f.delete();
    }

    @Override
    public File getFile() throws IOException {
        if (f == null) {
            f = File.createTempFile("emissaryTemp", ".tmpIFP");
            try (FileOutputStream fos = new FileOutputStream(f); InputStream is = Channels.newInputStream(container.channel())) {
                IOUtils.copyLarge(is, fos);
            }
            // Annoyingly hard to detect file modification, as it can take < 1 ms
            this.creationDate = f.lastModified() - 1;
        }
        return f;
    }

}