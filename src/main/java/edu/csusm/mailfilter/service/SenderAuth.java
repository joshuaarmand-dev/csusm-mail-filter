package edu.csusm.mailfilter.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Whether Gmail could verify that a message really came from the domain in its
 * From header, based on the {@code Authentication-Results} header Gmail adds.
 *
 * <p>The From header is just text the sender typed, so anyone can claim to be
 * {@code it@csusm.edu}. DMARC and DKIM are the checks that tie a message to the
 * domain that actually sent it.</p>
 *
 * <p>Only results written by Gmail ({@code mx.google.com}) are trusted. A sender
 * can put their own fake {@code Authentication-Results: dmarc=pass} header in a
 * message, so headers from any other server are ignored.</p>
 */
public final class SenderAuth {

    public enum Status {
        /** DMARC passed, or DKIM passed for the sender's own domain. */
        PASS,

        /** DMARC explicitly failed: the From domain is very likely forged. */
        FAIL,

        /** No usable result, e.g. the domain publishes no DMARC policy. */
        UNKNOWN
    }

    static final String GMAIL_AUTHSERV_ID = "mx.google.com";

    private static final Pattern METHOD_RESULT =
            Pattern.compile("^\\s*(dmarc|dkim|spf)\\s*=\\s*([a-z]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern PROPERTY =
            Pattern.compile("\\b(header\\.from|header\\.d|header\\.i)\\s*=\\s*@?([^\\s;()]+)",
                    Pattern.CASE_INSENSITIVE);

    public final Status status;

    /** Short human-readable description, e.g. "DMARC pass (csusm.edu)". */
    public final String summary;

    private SenderAuth(Status status, String summary) {
        this.status = status;
        this.summary = summary;
    }

    /**
     * @param authResultsHeaders values of every Authentication-Results header, top first
     * @param senderDomain       domain of the From address, e.g. "csusm.edu"
     */
    public static SenderAuth evaluate(List<String> authResultsHeaders, String senderDomain) {
        String gmailResults = null;

        for (String value : authResultsHeaders) {
            if (value != null && authservId(value).equalsIgnoreCase(GMAIL_AUTHSERV_ID)) {
                // The topmost Gmail header is the one Gmail added on delivery.
                gmailResults = value;
                break;
            }
        }

        if (gmailResults == null) {
            return new SenderAuth(Status.UNKNOWN, "Not verified (no Gmail authentication results)");
        }

        String domain = senderDomain == null ? "" : senderDomain.toLowerCase(Locale.ROOT);
        String dmarc = null;
        String dmarcFrom = null;
        String alignedDkimDomain = null;

        // Clauses are separated by ';'. The first is the authserv-id itself.
        String[] clauses = gmailResults.split(";");

        for (int i = 1; i < clauses.length; i++) {
            Matcher method = METHOD_RESULT.matcher(clauses[i]);

            if (!method.find()) {
                continue;
            }

            String name = method.group(1).toLowerCase(Locale.ROOT);
            String result = method.group(2).toLowerCase(Locale.ROOT);

            if (name.equals("dmarc") && dmarc == null) {
                dmarc = result;
                dmarcFrom = property(clauses[i], "header.from");
            } else if (name.equals("dkim") && result.equals("pass") && alignedDkimDomain == null) {
                String signer = property(clauses[i], "header.d");

                if (signer == null) {
                    signer = property(clauses[i], "header.i");
                }

                if (signer != null && aligned(domain, signer)) {
                    alignedDkimDomain = signer;
                }
            }
        }

        if ("pass".equals(dmarc) && (dmarcFrom == null || aligned(domain, dmarcFrom))) {
            return new SenderAuth(Status.PASS, "DMARC pass (" + (dmarcFrom == null ? domain : dmarcFrom) + ")");
        }

        if ("fail".equals(dmarc)) {
            return new SenderAuth(Status.FAIL, "DMARC fail");
        }

        if (alignedDkimDomain != null) {
            return new SenderAuth(Status.PASS, "DKIM pass (" + alignedDkimDomain + ")");
        }

        String detail = (dmarc == null) ? "no DMARC result" : "DMARC " + dmarc;
        return new SenderAuth(Status.UNKNOWN, "Not verified (" + detail + ")");
    }

    private static String authservId(String headerValue) {
        int semicolon = headerValue.indexOf(';');
        String first = (semicolon >= 0) ? headerValue.substring(0, semicolon) : headerValue;
        String[] tokens = first.trim().split("\\s+");

        return tokens.length == 0 ? "" : tokens[0];
    }

    private static String property(String clause, String name) {
        Matcher matcher = PROPERTY.matcher(clause);

        while (matcher.find()) {
            if (matcher.group(1).equalsIgnoreCase(name)) {
                String value = matcher.group(2).toLowerCase(Locale.ROOT);
                int at = value.lastIndexOf('@');

                // header.i may be a full address like "it@csusm.edu"
                return (at >= 0) ? value.substring(at + 1) : value;
            }
        }

        return null;
    }

    /**
     * Relaxed alignment: the domains are equal, or one is a subdomain of the
     * other (mail.csusm.edu and csusm.edu align; evil-csusm.edu does not).
     */
    static boolean aligned(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return false;
        }

        a = a.toLowerCase(Locale.ROOT);
        b = b.toLowerCase(Locale.ROOT);

        return a.equals(b) || a.endsWith("." + b) || b.endsWith("." + a);
    }
}