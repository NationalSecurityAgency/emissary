package emissary.output.formatter.filter.content;

import emissary.core.IBaseDataObject;
import emissary.output.DropOffUtil;
import emissary.output.formatter.filter.FilterConfigKeys;

/**
 * Allows only views matching the configured {@code ALLOWLIST} entries, rejecting everything else.
 */
public class ContentAllowFilter extends ContentFilter {

    public ContentAllowFilter() {
        super(FilterConfigKeys.ALLOWLIST);
    }

    @Override
    public boolean allows(final IBaseDataObject d, final String viewName) {
        return listContains(DropOffUtil.getFileType(d), viewName);
    }
}
