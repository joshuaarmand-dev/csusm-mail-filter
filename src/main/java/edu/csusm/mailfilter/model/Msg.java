package edu.csusm.mailfilter.model;

import java.util.List;

public class Msg {
    public final String id;
    public final String subject;
    public final String from;
    public final String received;
    public final String webLink;

    public List<String> reasons = List.of();
    public boolean classFiltered;
    public boolean classUnsafe;

    public Msg(String id, String subject, String from, String received, String webLink) {
        this.id = id;
        this.subject = subject;
        this.from = from;
        this.received = received;
        this.webLink = webLink;
    }
}