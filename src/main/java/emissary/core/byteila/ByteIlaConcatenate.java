package emissary.core.byteila;

import java.io.IOException;

public final class ByteIlaConcatenate {
    public static ByteIla create(ByteIla leftIla, ByteIla rightIla) {
        return new MyByteIla(leftIla, rightIla);
    }

    private static class MyByteIla implements ByteIla {
        private final ByteIla leftIla;
        private final ByteIla rightIla;
        private final long leftIlaLength;

        public MyByteIla(ByteIla leftIla, ByteIla rightIla) {
            this.leftIla = leftIla;
            this.rightIla = rightIla;
            this.leftIlaLength = leftIla.length();
        }

        @Override
        public long length() {
            return leftIla.length() + rightIla.length();
        }

        @Override
        public void toArray(byte[] array, int offset, long start, int length) throws IOException {
            if (start + length <= leftIlaLength) {
                leftIla.toArray(array, offset, start, length);
            } else if (start >= leftIlaLength) {
                rightIla.toArray(array, offset, start - leftIlaLength, length);
            } else {
                final int leftAmount = (int) (leftIlaLength - start);
                leftIla.toArray(array, offset, start, leftAmount);
                rightIla.toArray(array, offset + leftAmount, 0, length - leftAmount);
            }
        }
    }
}
