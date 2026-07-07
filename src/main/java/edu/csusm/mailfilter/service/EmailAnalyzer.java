package edu.csusm.mailfilter.service;

import edu.csusm.mailfilter.model.Analysis;
import edu.csusm.mailfilter.model.LinkScanResult;
import edu.csusm.mailfilter.model.Msg;
import edu.csusm.mailfilter.store.CompaniesStore;
import edu.csusm.mailfilter.util.EmailUtils;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;

import java.nio.charset.StandardCharsets;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

public class EmailAnalyzer {
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

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
            String body = extractBody(message);
            String webLink = "https://mail.google.com/mail/u/0/#inbox/" + id;

            Msg row = new Msg(id, subject, email, receivedAt, webLink);

            boolean fromSchool = EmailUtils.isCsusm(email);
            String senderDomain = EmailUtils.senderDomain(email);
            boolean fromAllowed = store.isAllowed(senderDomain);
            boolean fromBlocked = store.isBlocked(senderDomain);

            boolean unsafe = false;
            List<String> reasons = new ArrayList<>();

            if (fromBlocked) {
                unsafe = true;
                reasons.add("Sender in block list: " + senderDomain);
            }

            LinkScanResult result = linkScanner.scan(body, store);

            if (result.suspicious) {
                unsafe = true;
                reasons.addAll(result.reasons);
            }

            row.classFiltered = fromSchool || fromAllowed;
            row.classUnsafe = unsafe;
            row.reasons = reasons;

            if (row.classFiltered) {
                out.filtered.add(row);
            } else {
                out.unfiltered.add(row);
            }

            if (row.classUnsafe) {
                out.unsafe.add(row);
            }
        }

        return out;
    }

    private static String extractBody(Message message) {
        if (message.getPayload() == null) {
            return "";
        }

        return extractBodyFromPart(message.getPayload());
    }

    private static String extractBodyFromPart(MessagePart part) {
        if (part.getBody() != null && part.getBody().getData() != null) {
            byte[] data = Base64.getUrlDecoder().decode(part.getBody().getData());
            return new String(data, StandardCharsets.UTF_8);
        }

        if (part.getParts() != null) {
            for (MessagePart childPart : part.getParts()) {
                String text = extractBodyFromPart(childPart);

                if (!text.isEmpty()) {
                    return text;
                }
            }
        }

        return "";
    }
}