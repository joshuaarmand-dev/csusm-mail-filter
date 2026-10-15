package edu.csusm.mailfilter.ui;

import edu.csusm.mailfilter.model.Msg;

/**
 * Builds the text for the details panel: everything the scan found out about
 * one message, so the user can see exactly why it landed in its tab.
 */
public final class MessageDetails {

    /** Long marketing emails can contain hundreds of links. */
    static final int MAX_LINKS_SHOWN = 50;

    private MessageDetails() {
    }

    public static String describe(Msg msg) {
        if (msg == null) {
            return "Select a message to see why it was classified the way it was.";
        }

        StringBuilder out = new StringBuilder();

        out.append("Subject:   ").append(nullToEmpty(msg.subject)).append('\n');
        out.append("From:      ").append(nullToEmpty(msg.from)).append('\n');
        out.append("Received:  ").append(nullToEmpty(msg.received)).append('\n');
        out.append("Tab:       ").append(tabName(msg)).append('\n');
        out.append("Sender:    ").append(msg.auth == null || msg.auth.isBlank() ? "Not checked" : msg.auth)
                .append('\n');

        out.append('\n');

        if (msg.reasons == null || msg.reasons.isEmpty()) {
            out.append("No risk indicators found.\n");
        } else {
            out.append("Why it was flagged:\n");

            for (String reason : msg.reasons) {
                out.append("  \u2022 ").append(reason).append('\n');
            }
        }

        out.append('\n');

        int linkCount = (msg.links == null) ? 0 : msg.links.size();

        if (linkCount == 0) {
            out.append("No web links found.\n");
        } else {
            out.append("Links checked (").append(linkCount).append("):\n");

            for (int i = 0; i < Math.min(linkCount, MAX_LINKS_SHOWN); i++) {
                out.append("  ").append(msg.links.get(i)).append('\n');
            }

            if (linkCount > MAX_LINKS_SHOWN) {
                out.append("  \u2026 and ").append(linkCount - MAX_LINKS_SHOWN).append(" more\n");
            }
        }

        return out.toString();
    }

    static String tabName(Msg msg) {
        return switch (msg.category) {
            case TRUSTED -> "Filtered (trusted, verified sender)";
            case NORMAL -> "Unfiltered (no risk indicators)";
            case UNSAFE -> "Unsafe";
        };
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}