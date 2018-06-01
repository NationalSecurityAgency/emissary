package emissary.util.search;

public interface IMultiKeywordScanner {

    void loadKeywords(String[] keywords);

    HitList findAll(byte[] data, int start, int stop);

    HitList findAll(byte[] data, int start);

    HitList findAll(byte[] data);

    HitList findNext(byte[] data, int start, int stop);

    HitList findNext(byte[] data, int start);

    HitList findNext(byte[] data);

    HitList findNext();
}
