package edu.csusm.mailfilter.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of a scan. Every message is in exactly one of the three lists,
 * chosen by its {@link Category}.
 */
public class Analysis {
    /** {@link Category#TRUSTED} messages ("Filtered" tab). */
    public final List<Msg> filtered = new ArrayList<>();

    /** {@link Category#NORMAL} messages ("Unfiltered" tab). */
    public final List<Msg> unfiltered = new ArrayList<>();

    /** {@link Category#UNSAFE} messages ("Unsafe" tab). */
    public final List<Msg> unsafe = new ArrayList<>();

    public void add(Msg msg) {
        switch (msg.category) {
            case TRUSTED -> filtered.add(msg);
            case NORMAL -> unfiltered.add(msg);
            case UNSAFE -> unsafe.add(msg);
        }
    }
}