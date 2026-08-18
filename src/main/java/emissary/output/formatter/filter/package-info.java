/**
 * Provides composable formatter-level filters that select which content (views) and metadata (parameters) of a payload
 * may be written to output. Filters are ANDed into a chain; the chain is empty (allow-all) when nothing is configured.
 * Additional filter classes can be layered onto the chains at runtime via the {@code CONTENT_FILTER} and
 * {@code METADATA_FILTER} configuration entries — each entry names a fully qualified filter class that Emissary
 * instantiates through {@link emissary.core.Factory}.
 */
package emissary.output.formatter.filter;
