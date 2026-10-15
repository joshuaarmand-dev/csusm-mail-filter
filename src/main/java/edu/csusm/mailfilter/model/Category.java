package edu.csusm.mailfilter.model;

/**
 * The single tab a scanned message belongs to.
 *
 * <p>Categories are mutually exclusive. When a message matches more than one,
 * {@link #UNSAFE} wins: a risky message from a trusted sender is still risky.</p>
 */
public enum Category {
    /** From CSUSM or an allowed domain, with no risk indicators. Shown in "Filtered". */
    TRUSTED,

    /** Not from a trusted sender and no risk indicators. Shown in "Unfiltered". */
    NORMAL,

    /** Matched at least one risk rule. Shown in "Unsafe". */
    UNSAFE
}