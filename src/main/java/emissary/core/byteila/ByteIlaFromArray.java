package emissary.core.byteila;

import java.io.IOException;
import java.io.Serializable;

public class ByteIlaFromArray {
    private ByteIlaFromArray() {}

    public static ByteIla create(final byte[] array) {
        return new MyByteIla(array);
    }

    private static class MyByteIla implements ByteIla, Serializable {
        private static final long serialVersionUID = 1L;
        private final byte[] array;

        public MyByteIla(final byte[] array) {
            this.array = array;
        }

        @Override
        public long length() {
            return array.length;
        }

        @Override
        public void toArray(byte[] array, int offset, long start, int length) throws IOException {
            System.arraycopy(this.array, (int) start, array, offset, length);
        }

        // Here solely to make JavaBeans stuff happy.
        @SuppressWarnings("unused")
        public byte[] getArray() {
            return array;
        }
    }
}
