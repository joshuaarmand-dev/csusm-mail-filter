package edu.csusm.mailfilter.service;

import edu.csusm.mailfilter.model.Analysis;
import edu.csusm.mailfilter.model.Category;
import edu.csusm.mailfilter.model.LinkScanResult;
import edu.csusm.mailfilter.model.Msg;
import edu.csusm.mailfilter.store.CompaniesStore;
import edu.csusm.mailfilter.util.EmailUtils;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmailAnalyzer {
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private static final Pattern CHARSET =
            Pattern.compile("(?i)charset\\s*=\\s*\"?([^\";\\s]+)");

    private final LinkScanner linkScanner;

    public EmailAnalyzer(LinkScanner linkScanner) {
        this.linkScanner = linkScanner;
    }

    public Analysis analyzeMessages(List<Message> messages, CompaniesStore store) {
        Analysis out = new Analysis();

        for (Message message : messages) {
            String id = message.getId();
            String subject = "(no subject)";
            String fromHeader = "";
            String receivedAt = "";
            List<String> authResults = new ArrayList<>();

            try {
                List<MessagePartHeader> headers =
                        (message.getPayload() != null && message.getPayload().getHeaders() != null)
                                ? message.getPayload().getHeaders()
                                : List.of();

                for (MessagePartHeader header : headers) {
                    String name = (header.getName() == null ? "" : header.getName())
                            .toLowerCase(Locale.ROOT);

                    switch (name) {
                        case "subject" -> subject = header.getValue();
                        case "from" -> fromHeader = header.getValue();
                        case "authentication-results" -> authResults.add(header.getValue());
                        default -> {
                        }
                    }
                }

                Long internalDate = message.getInternalDate();

                if (internalDate != null) {
                    receivedAt = WHEN.format(Instant.ofEpochMilli(internalDate));
                }
            } catch (Exception ignored) {
            }

            String email = EmailUtils.extractEmail(fromHeader);
            Bodies bodies = extractBodies(message);
            String webLink = "https://mail.google.com/mail/u/0/#inbox/" + id;

            Msg row = new Msg(id, subject, email, receivedAt, webLink);

            boolean fromSchool = EmailUtils.isCsusm(email);
            String senderDomain = EmailUtils.senderDomain(email);
            boolean fromAllowed = store.isAllowed(senderDomain);
            boolean fromBlocked = store.isBlocked(senderDomain);
            boolean claimsTrustedSender = fromSchool || fromAllowed;

            SenderAuth auth = SenderAuth.evaluate(authResults, senderDomain);
            row.auth = auth.summary;

            boolean unsafe = false;
            List<String> reasons = new ArrayList<>();

            if (claimsTrustedSender && auth.status == SenderAuth.Status.FAIL) {
                // Claims to be CSUSM or an allowed partner, but Gmail says the
                // From domain was forged. A classic spoofing attempt.
                unsafe = true;
                reasons.add("Claims to be from " + senderDomain + " but failed authentication ("
                        + auth.summary + ")");
            }

            if (fromBlocked) {
                unsafe = true;
                reasons.add("Sender in block list: " + senderDomain);
            }

            LinkScanResult result = linkScanner.scan(bodies.html(), bodies.text(), store);
            row.links = result.links;

            if (result.suspicious) {
                unsafe = true;
                reasons.addAll(result.reasons);
            }

            // A trusted domain only counts once Gmail has verified the sender.
            boolean trustedSender = claimsTrustedSender && auth.status == SenderAuth.Status.PASS;

            row.category = classify(trustedSender, unsafe);
            row.reasons = reasons;

            out.add(row);
        }

        return out;
    }

    /**
     * Picks exactly one category. Risk always wins over trust, so a message
     * from a trusted sender that contains a risky link is still UNSAFE.
     */
    static Category classify(boolean trustedSender, boolean unsafe) {
        if (unsafe) {
            return Category.UNSAFE;
        }

        return trustedSender ? Category.TRUSTED : Category.NORMAL;
    }

    /** The readable bodies of a message. Either may be empty, never null. */
    record Bodies(String html, String text) {
    }

    /**
     * Collects every text/html and text/plain part of a message.
     *
     * <p>Most mail is multipart/alternative with the plain-text part first.
     * Returning only the first part with data (the old behavior) meant the
     * HTML, where the real links are, was never scanned.</p>
     */
    static Bodies extractBodies(Message message) {
        StringBuilder html = new StringBuilder();
        StringBuilder text = new StringBuilder();

        if (message.getPayload() != null) {
            collectBodies(message.getPayload(), html, text);
        }

        return new Bodies(html.toString(), text.toString());
    }

    private static void collectBodies(MessagePart part, StringBuilder html, StringBuilder text) {
        String mimeType = (part.getMimeType() == null)
                ? ""
                : part.getMimeType().toLowerCase(Locale.ROOT);

        if (part.getBody() != null && part.getBody().getData() != null) {
            String decoded = decode(part.getBody().getData(), charsetOf(part));

            if (mimeType.startsWith("text/html")) {
                html.append(decoded).append('\n');
            } else if (mimeType.startsWith("text/plain") || mimeType.isEmpty()) {
                text.append(decoded).append('\n');
            }
        }

        if (part.getParts() != null) {
            for (MessagePart childPart : part.getParts()) {
                collectBodies(childPart, html, text);
            }
        }
    }

    private static String decode(String base64Url, Charset charset) {
        try {
            return new String(Base64.getUrlDecoder().decode(base64Url), charset);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    /** Charset from the part's Content-Type header, defaulting to UTF-8. */
    private static Charset charsetOf(MessagePart part) {
        if (part.getHeaders() != null) {
            for (MessagePartHeader header : part.getHeaders()) {
                if (!"content-type".equalsIgnoreCase(header.getName()) || header.getValue() == null) {
                    continue;
                }

                Matcher matcher = CHARSET.matcher(header.getValue());

                if (matcher.find()) {
                    try {
                        return Charset.forName(matcher.group(1));
                    } catch (IllegalArgumentException e) {
                        // Unknown or misspelled charset; fall back to UTF-8.
                    }
                }
            }
        }

        return StandardCharsets.UTF_8;
    }
}