package emissary.output.formatter;

import emissary.core.IBaseDataObject;
import emissary.output.formatter.filter.AbstractItemFilter;
import emissary.output.formatter.filter.OutputItem;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A permissive filter that counts every consult, for verifying short-circuit semantics.
 */
public class CountingOutputFilter extends AbstractItemFilter {

    public static final AtomicInteger calls = new AtomicInteger();

    @Override
    public boolean test(final IBaseDataObject d, final OutputItem item) {
        calls.incrementAndGet();
        return true;
    }
}
