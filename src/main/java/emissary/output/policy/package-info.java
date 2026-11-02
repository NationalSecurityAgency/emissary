/**
 * The one place that decides what output is written.
 *
 * <p>
 * A policy covers payloads, alternate views, parameter keys, and parameter values. Rules run in configured order and
 * the first decision wins; when no rule decides, {@code POLICY_DEFAULT} applies. Every decision names the rule or
 * filter that made it, so output can be explained from the log.
 *
 * <h2>Rule grammar</h2>
 *
 * <pre>
 * rule &lt;allow|deny&gt; &lt;payload|view|param|value&gt; &lt;selector&gt;
 *
 * payload  filetype=PDF,EXE | form=UNKNOWN | outputable=false | any
 * view     JSON_ML | FTYPE.TYPE.VIEW | JSON_* | * or ALL
 * param    SECRET | PRIVATE_* | * or ALL
 * value    EMAIL=*@example.com
 * </pre>
 *
 * <p>
 * Payload clauses are combined; every configured clause must match. View, parameter, and value selectors accept
 * comma-separated entries. Value patterns support {@code *} and {@code ?} wildcards. {@code POLICY_STAGE} entries are
 * evaluated in file order, and custom filters use {@code filter <class>}.
 *
 * <h2>Emission</h2>
 *
 * <p>
 * {@code EMIT} controls output shape: {@code both} (default), {@code metadata} (no payload or views), or
 * {@code content} (no parameters). Payload eligibility applies atomically to the supplied family, including nested
 * extracted records. View and parameter traces include family eligibility and all parent gates. Later rules are
 * recorded as not evaluated after a decisive result. A parameter value must pass both its key gate and its value gate.
 */
package emissary.output.policy;
