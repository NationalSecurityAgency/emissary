package emissary.output.sink;

/** Outcome of a sink write. Denials are reported, never disguised as success. */
public enum WriteStatus {
    /** Bytes were written. */
    SUCCESS,
    /** Nothing written: payload filters denied it. Continues the chain. */
    SKIPPED,
    /** Write failed. Breaks the chain per failure policy. */
    FAILURE
}
