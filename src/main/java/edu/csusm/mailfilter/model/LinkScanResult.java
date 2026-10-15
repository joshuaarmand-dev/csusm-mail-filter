package edu.csusm.mailfilter.model;

import java.util.List;

public class LinkScanResult {
    public final boolean suspicious;
    public final List<String> reasons;

    /** Every web link that was checked, after unwrapping SafeLinks-style wrappers. */
    public final List<String> links;

    public LinkScanResult(boolean suspicious, List<String> reasons) {
        this(suspicious, reasons, List.of());
    }

    public LinkScanResult(boolean suspicious, List<String> reasons, List<String> links) {
        this.suspicious = suspicious;
        this.reasons = reasons;
        this.links = links;
    }
}