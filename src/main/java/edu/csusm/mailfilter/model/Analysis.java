package edu.csusm.mailfilter.model;

import java.util.ArrayList;
import java.util.List;

public class Analysis {
    public final List<Msg> filtered = new ArrayList<>();
    public final List<Msg> unfiltered = new ArrayList<>();
    public final List<Msg> unsafe = new ArrayList<>();
}