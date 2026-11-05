/**
 * Filters selecting which views and parameters are written. ANDed in order, stopping at the first deny; declared via
 * {@code OUTPUT_FILTER}. Whole-payload eligibility is the sink's job ({@code PAYLOAD_FILTER}).
 */
package emissary.output.formatter.filter;
