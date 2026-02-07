package emissary.core.sentinel.protocols;

import emissary.config.Configurator;
import emissary.core.Factory;
import emissary.core.sentinel.protocols.actions.Action;
import emissary.core.sentinel.protocols.actions.Notify;
import emissary.core.sentinel.protocols.rules.Rule;

import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protocols are configured with a list of rules that trigger some action if all conditions are met.
 */
public abstract class Protocol<T> {

    protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    protected Configurator config;
    protected final Map<String, Rule<T>> rules = new ConcurrentHashMap<>();
    protected Action action;
    protected boolean enabled = false;

    protected Protocol() {}

    public Protocol(final Configurator config) {
        configure(config);
    }

    public abstract void run() throws IOException;

    protected abstract Rule<T> getRule(String ruleId) throws IOException;

    public boolean isEnabled() {
        return this.enabled && MapUtils.isNotEmpty(this.rules);
    }

    protected void configure(final Configurator config) {
        this.config = config;
        init();
    }

    /**
     * Initialize rule set and action
     */
    protected void init() {
        this.enabled = this.config.findBooleanEntry("ENABLED", false);
        this.action = (Action) Factory.create(this.config.findStringEntry("ACTION", Notify.class.getName()));

        logger.trace("Loading rules...");
        for (String ruleId : this.config.findEntries("RULE_ID")) {
            if (this.rules.containsKey(ruleId)) {
                logger.warn("Sentinel rule with ID[{}] already exists, this may result in unexpected behavior", ruleId);
            }
            try {
                final Rule<T> ruleImpl = getRule(ruleId);
                this.rules.put(ruleId, ruleImpl);
                logger.debug("Sentinel loaded rule[{}] - {}", ruleId, ruleImpl);
            } catch (Exception e) {
                logger.warn("Sentinel rule[{}] is invalid: {}", ruleId, e.getMessage());
            }
        }

        if (this.rules.isEmpty()) {
            this.enabled = false;
        }
    }

}
