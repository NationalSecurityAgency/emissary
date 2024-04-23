package emissary.util.shell;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class ReadBinaryOutputBuffer extends ProcessReader {

    private final InputStream inputStream;
    private final ByteArrayOutputStream baos;

    public ReadBinaryOutputBuffer(final InputStream is, ByteArrayOutputStream baos) {
        this.inputStream = is;
        this.baos = baos;
    }

    @Override
    void runImpl() {

        try {
            IOUtils.copy(inputStream, baos);
        } catch (Exception e) {
            //
        }
    }

    @Override
    public void finish() {}

    public byte[] getBytes() {
        return baos.toByteArray();
    }

    public ByteArrayOutputStream getByteStream() {
        return baos;
    }
}
