package emissary.output.sink;

import emissary.output.policy.Decision;
import emissary.output.policy.OutputPolicy;
import emissary.output.policy.request.PayloadRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Applies one output policy to views and parameters and logs every denial. */
final class PolicyGate {

    private final Logger logger;
    private final OutputPolicy policy;

    PolicyGate(final Class<?> owner, final OutputPolicy policy) {
        this.logger = LoggerFactory.getLogger(owner);
        this.policy = policy;
    }

    boolean allowsView(final PayloadRequest payload, final String viewName) {
        return allowed("view", viewName, this.policy.decideView(payload, viewName), false);
    }

    boolean allowsParam(final PayloadRequest payload, final String key) {
        return allowed("param", key, this.policy.decideParam(payload, key, null), false);
    }

    boolean allowsValue(final PayloadRequest payload, final String key, final Object value) {
        return allowed("value", key, this.policy.decideValue(payload, key, value), true);
    }

    private boolean allowed(final String kind, final String name, final Decision decision, final boolean perValue) {
        if (decision.isAllowed()) {
            return true;
        }
        // Individual value denials can be numerous, so keep them at TRACE; keys and views are DEBUG.
        if (perValue) {
            this.logger.trace("Denied {} '{}': {}", kind, name, decision);
        } else {
            this.logger.debug("Denied {} '{}': {}", kind, name, decision);
        }
        return false;
    }
}
