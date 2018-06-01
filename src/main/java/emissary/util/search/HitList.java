package emissary.util.search;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * List of reportable results from MultiKeywordScanner
 */
public class HitList extends ArrayList<Hit> implements List<Hit> {
    /**
     * provide uid for serialization
     */
    private static final long serialVersionUID = -7099799510085720979L;

    public HitList() {
        super();
    }

    public HitList(final Collection<Hit> c) {
        super(c);
    }
}
