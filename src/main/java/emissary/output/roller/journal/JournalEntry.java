package emissary.output.roller.journal;

import jakarta.annotation.Nullable;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Journal Entry containing a value, generally a file name, and an offset.
 */
public final class JournalEntry {
    static final byte SEP = 0x00;
    final String val;
    long offset;

    public JournalEntry(final String val, final long offset) {
        if (val == null || val.length() > 512) {
            throw new IllegalArgumentException("Val must be present and cannot exceed 512 bytes");
        }
        this.val = val;
        this.offset = offset;
    }

    void serialize(final ByteBuffer b) {
        b.putInt(this.val.length());
        b.put(SEP);
        b.put(this.val.getBytes());
        b.put(SEP);
        b.putLong(this.offset);
    }

    @Override
    public String toString() {
        return "JournalEntry{" + "value=" + val + ", offset=" + offset + '}';
    }

    static JournalEntry deserialize(final ByteBuffer b) {
        final int keyLen = b.getInt();
        validateSep(b.get());
        final byte[] keyBytes = new byte[keyLen];
        b.get(keyBytes, 0, keyLen);
        validateSep(b.get());
        final long offset = b.getLong();
        return new JournalEntry(new String(keyBytes), offset);
    }

    static void validateSep(final byte b) {
        if (SEP != b) {
            throw new IllegalArgumentException("Null byte separator expected " + b);
        }
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + Objects.hashCode(this.val);
        return hash;
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final JournalEntry other = (JournalEntry) obj;
        if (!Objects.equals(this.val, other.val)) {
            return false;
        }
        return true;
    }

    public String getVal() {
        return this.val;
    }

    public long getOffset() {
        return this.offset;
    }
}
