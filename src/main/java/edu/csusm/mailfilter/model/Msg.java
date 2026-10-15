package edu.csusm.mailfilter.model;

import java.util.List;

public class Msg {
    public final String id;
    public final String subject;
    public final String from;
    public final String received;
    public final String webLink;

    public List<String> reasons = List.of();
    public Category category = Category.NORMAL;

    /** Sender authentication result, e.g. "DMARC pass (csusm.edu)". */
    public String auth = "";

    /** Web links found in the message, as checked by LinkScanner. */
    public List<String> links = List.of();

    public Msg(String id, String subject, String from, String received, String webLink) {
        this.id = id;
        this.subject = subject;
        this.from = from;
        this.received = received;
        this.webLink = webLink;
    }
}