package emissary.output.formatter.filter.content;

import emissary.core.IBaseDataObject;
import emissary.output.DropOffUtil;
import emissary.output.formatter.filter.FilterConfigKeys;

/**
 * Rejects views matching the configured {@code DENYLIST} entries, accepting everything else.
 */
public class ContentDenyFilter extends ContentFilter {

    public ContentDenyFilter() {
        super(FilterConfigKeys.DENYLIST);
    }

    @Override
    public boolean allows(final IBaseDataObject d, final String viewName) {
        return !listContains(DropOffUtil.getFileType(d), viewName);
    }
}
