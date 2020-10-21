package emissary.core.byteila;

import java.io.IOException;

public interface ByteIla {
    public long length();

    public void toArray(byte[] array, int offset, long start, int length) throws IOException;
}
