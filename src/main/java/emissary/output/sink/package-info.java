/**
 * Drop-off sinks: the configurable output unit.
 *
 * <p>
 * A sink compiles one {@link emissary.output.policy.OutputPolicy} from its configuration, decides whole-payload
 * eligibility, and writes JSON or XML using that same policy for views and parameters. Serialization helpers are
 * internal to this package.
 *
 * <p>
 * Legacy drop-off filters remain supported for compatibility; new output configuration uses sinks.
 */
package emissary.output.sink;
