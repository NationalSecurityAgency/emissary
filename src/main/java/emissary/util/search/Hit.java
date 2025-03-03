package emissary.util.search;

import jakarta.annotation.Nullable;

/**
 * Reportable result from MultiKeywordScanner
 */
public class Hit {
    public static final int OFFSET = 0;
    public static final int ID = 1;

    protected int[] hit = new int[2];

    public Hit() {}

    public Hit(final int offset, final int id) {
        this.hit[OFFSET] = offset;
        this.hit[ID] = id;
    }

    public Hit(@Nullable final int[] vals) {
        if ((vals != null) && (vals.length == 2)) {
            this.hit[OFFSET] = vals[OFFSET];
            this.hit[ID] = vals[ID];
        }
    }

    public void setOffset(final int i) {
        this.hit[OFFSET] = i;
    }

    public void setId(final int i) {
        this.hit[ID] = i;
    }

    public int getOffset() {
        return this.hit[OFFSET];
    }

    public int getId() {
        return this.hit[ID];
    }

    public int[] getRaw() {
        return this.hit;
    }
}
