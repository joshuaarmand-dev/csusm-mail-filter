package edu.csusm.mailfilter.model;

import java.util.List;

public class LinkScanResult {
    public final boolean suspicious;
    public final List<String> reasons;

    public LinkScanResult(boolean suspicious, List<String> reasons) {
        this.suspicious = suspicious;
        this.reasons = reasons;
    }
}