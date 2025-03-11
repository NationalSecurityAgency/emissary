package emissary.core.sentinel.protocols.rules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Collection;

public abstract class Rule<T> {

    protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public Rule() {

    }

    /**
     * Check the rule conditions
     *
     * @param items collection of items to check
     * @return true if conditions are met, false otherwise
     */
    public abstract boolean condition(Collection<T> items);

}
