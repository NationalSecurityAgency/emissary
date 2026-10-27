package emissary.output.policy.rule;

import emissary.output.policy.Decision;
import emissary.output.policy.Decision.Outcome;
import emissary.output.policy.OutputFilter;
import emissary.output.policy.request.ParamRequest;
import emissary.output.policy.request.PayloadRequest;
import emissary.output.policy.request.ViewRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Evaluates declarative {@link Rule}s in declared order. The first matching rule decides; no match abstains.
 *
 * <p>
 * All rules in one stage share a stage name ({@code rules}); each {@link Decision} names the individual rule.
 */
public final class RuleStage implements OutputFilter {

    private final List<Rule> payloadRules = new ArrayList<>();
    private final List<Rule> viewRules = new ArrayList<>();
    private final List<Rule> paramRules = new ArrayList<>();
    private final List<Rule> valueRules = new ArrayList<>();
    private final List<Rule> allRules;

    /**
     * Create a stage from ordered rules.
     *
     * @param rules the rules, in declared order
     */
    public RuleStage(final List<Rule> rules) {
        this.allRules = Collections.unmodifiableList(new ArrayList<>(rules));
        for (final Rule rule : rules) {
            switch (rule.dimension()) {
                case PAYLOAD:
                    this.payloadRules.add(rule);
                    break;
                case VIEW:
                    this.viewRules.add(rule);
                    break;
                case PARAM:
                    this.paramRules.add(rule);
                    break;
                case PARAM_VALUE:
                    this.valueRules.add(rule);
                    break;
            }
        }
    }

    @Override
    public String name() {
        return this.allRules.size() == 1 ? this.allRules.get(0).stage() : "rules";
    }

    @Override
    public Set<String> requiredContext() {
        return Collections.emptySet();
    }

    @Override
    public Decision decide(final PayloadRequest request) {
        for (final Rule rule : this.payloadRules) {
            if (rule.matches(request)) {
                return decisionFor(rule);
            }
        }
        return noMatch(this.payloadRules);
    }

    @Override
    public Decision decide(final ViewRequest request) {
        for (final Rule rule : this.viewRules) {
            if (rule.matches(request)) {
                return decisionFor(rule);
            }
        }
        return noMatch(this.viewRules);
    }

    @Override
    public Decision decide(final ParamRequest request) {
        final List<Rule> rules = request.isKeyDecision() ? this.paramRules : this.valueRules;
        for (final Rule rule : rules) {
            if (rule.matches(request)) {
                return decisionFor(rule);
            }
        }
        return noMatch(rules);
    }

    private Decision noMatch(final List<Rule> applicable) {
        final String detail = this.allRules.size() == 1 ? this.allRules.get(0).text() : describe();
        return Decision.abstain(name(), detail + (applicable.isEmpty() ? " [dimension not applicable]" : " [no match]"));
    }

    private static Decision decisionFor(final Rule rule) {
        if (rule.action() == Outcome.ALLOW) {
            return Decision.allow(rule.stage(), rule.text());
        }
        return Decision.deny(rule.stage(), rule.text());
    }

    @Override
    public String describe() {
        if (this.allRules.isEmpty()) {
            return "rules (none)";
        }
        final StringBuilder sb = new StringBuilder("rules: ");
        boolean first = true;
        for (final Rule rule : this.allRules) {
            if (!first) {
                sb.append("; ");
            }
            sb.append(rule.index()).append(". ").append(rule.text());
            first = false;
        }
        return sb.toString();
    }

    public List<Rule> rules() {
        return this.allRules;
    }
}
