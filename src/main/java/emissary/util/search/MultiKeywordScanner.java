package emissary.util.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiKeywordScanner implements IMultiKeywordScanner {

    private static final Logger logger = LoggerFactory.getLogger(MultiKeywordScanner.class);

    private byte[] data = null;
    private int[] skipTable = new int[256];
    private int standardSkip = 0;
    private BackwardsTreeScanner treeScanner;
    private String[] keywords;
    private int lastPosition = -1;

    public MultiKeywordScanner() {}

    /**
     * Case insensitive was never implemented
     */
    @Deprecated
    public MultiKeywordScanner(final boolean caseSensitive) {
        if (!caseSensitive) {
            logger.error("Case-insensitive MultiKeywordScanner is not implemented");
        }
    }

    @Override
    public void loadKeywords(final String[] keywordsArg) {
        this.keywords = keywordsArg;

        try {
            if (null == this.treeScanner) {
                this.treeScanner = new BackwardsTreeScanner(keywordsArg);
            } else {
                this.treeScanner.resetKeywords(keywordsArg);
            }
        } catch (Exception e) {
            logger.error("Could not create BackwardsTreeScanner", e);
        }

        buildSkipTable();
    }

    private void buildSkipTable() {
        final int numKeywords = this.keywords.length;

        for (int i = 0; i < numKeywords; i++) {
            final byte[] keyword = this.keywords[i].getBytes();
            final int keywordLength = keyword.length;
            if (i == 0) {
                this.standardSkip = keywordLength;
            } else if (this.standardSkip > keywordLength) {
                this.standardSkip = keywordLength;
            }
        }

        for (int i = 0; i < 256; i++) {
            this.skipTable[i] = this.standardSkip;
        }

        for (int i = 0; i < numKeywords; i++) {
            final byte[] keyword = this.keywords[i].getBytes();
            final int keywordLength = keyword.length;
            for (int j = 0; j < (keywordLength - 1); j++) {
                final int byteValue = get256Value(keyword[j]);
                final int skip = keywordLength - (j + 1);
                if (skip < this.skipTable[byteValue]) {
                    this.skipTable[byteValue] = skip;
                }
            }
        }
    }

    private static int get256Value(final byte b) {
        return ((int) b) & 0xff;
    }

    @Override
    public HitList findAll(final byte[] dataArg) {
        if (dataArg != null) {
            return this.findAll(dataArg, 0, dataArg.length);
        }
        return new HitList();
    }

    @Override
    public HitList findAll(final byte[] dataArg, final int start) {
        if (dataArg != null) {
            return this.findAll(dataArg, start, dataArg.length);
        }
        return new HitList();
    }

    @Override
    public HitList findAll(final byte[] dataArg, final int start, final int stop) {
        this.data = dataArg;
        int position;
        final HitList hits = new HitList();

        for (position = start + this.standardSkip - 1; position < stop; position += this.skipTable[get256Value(dataArg[position])]) {
            try {
                this.treeScanner.scan(dataArg, position, hits);
            } catch (Exception e) {
                logger.error("Error scanning keywords in the BackwardsTreeScanner.", e);
                break;
            }
        }

        this.lastPosition = position;

        return hits;
    }

    @Override
    public HitList findNext() {
        if (this.data != null) {
            return this.findNext(this.data, this.lastPosition + 1, this.data.length);
        }
        return new HitList();
    }

    @Override
    public HitList findNext(final byte[] dataArg) {
        if (dataArg != null) {
            return this.findNext(dataArg, this.lastPosition + 1, dataArg.length);
        }
        return new HitList();
    }

    @Override
    public HitList findNext(final byte[] dataArg, final int start) {
        if (dataArg != null) {
            return this.findNext(dataArg, start, dataArg.length);
        }
        return new HitList();
    }

    @Override
    public HitList findNext(final byte[] dataArg, final int start, final int stop) {
        this.data = dataArg;
        int position;
        final HitList hits = new HitList();

        for (position = start + this.standardSkip - 1; position < stop; position += this.skipTable[get256Value(dataArg[position])]) {
            try {
                this.treeScanner.scan(dataArg, position, hits);
                if (hits.size() > 0) {
                    break;
                }
            } catch (Exception e) {
                logger.error("Trouble scanning for keywords in BackwardsTreeScanner", e);
                break;
            }
        }

        this.lastPosition = position;

        return hits;
    }
}
