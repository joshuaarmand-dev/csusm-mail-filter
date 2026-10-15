package edu.csusm.mailfilter.service;

import edu.csusm.mailfilter.model.Analysis;
import edu.csusm.mailfilter.model.Category;
import edu.csusm.mailfilter.model.Msg;
import edu.csusm.mailfilter.store.CompaniesStore;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailAnalyzerTest {

    private final EmailAnalyzer analyzer = new EmailAnalyzer(new LinkScanner());

    @Test
    void cleanVerifiedCsusmMessageIsTrusted() {
        Analysis result = analyze(verified(htmlMessage("Advisor <advisor@csusm.edu>",
                "<a href=\"https://www.csusm.edu/advising\">Advising</a>"), "csusm.edu"));

        assertCategory(Category.TRUSTED, result);
        assertEquals("DMARC pass (csusm.edu)", result.filtered.get(0).auth);
    }

    @Test
    void unverifiedCsusmMessageIsNormalNotTrusted() {
        Analysis result = analyze(htmlMessage("Advisor <advisor@csusm.edu>", "Hello"));

        assertCategory(Category.NORMAL, result);
    }

    @Test
    void spoofedCsusmMessageIsUnsafe() {
        Message message = htmlMessage("IT Help <it@csusm.edu>", "Please confirm your account.");
        withHeader(message, "Authentication-Results",
                "mx.google.com; spf=fail smtp.mailfrom=evil.example; "
                        + "dmarc=fail (p=QUARANTINE dis=QUARANTINE) header.from=csusm.edu");

        Analysis result = analyze(message);

        assertCategory(Category.UNSAFE, result);
        assertTrue(result.unsafe.get(0).reasons.get(0).startsWith("Claims to be from csusm.edu"));
    }

    @Test
    void authResultsWrittenByAnotherServerAreIgnored() {
        // A spammer can add their own "dmarc=pass" header. Only Gmail's counts.
        Message message = htmlMessage("IT Help <it@csusm.edu>", "Hello");
        withHeader(message, "Authentication-Results", "mail.evil.example; dmarc=pass header.from=csusm.edu");

        assertCategory(Category.NORMAL, analyze(message));
    }

    @Test
    void cleanExternalMessageIsNormal() {
        Analysis result = analyze(htmlMessage("Store <news@example.com>",
                "<a href=\"https://example.com/sale\">Sale</a>"));

        assertCategory(Category.NORMAL, result);
    }

    @Test
    void riskyLinkFromCsusmSenderIsUnsafeOnly() {
        Analysis result = analyze(htmlMessage("IT Help <it@csusm.edu>",
                "<a href=\"https://bit.ly/reset-password\">Reset your password</a>"));

        assertCategory(Category.UNSAFE, result);
        assertTrue(result.filtered.isEmpty(), "unsafe message must not also appear as trusted");
    }

    @Test
    void blockedSenderIsUnsafeOnly() {
        CompaniesStore store = new CompaniesStore();
        store.addBlocked("spam.example");

        Analysis result = analyzer.analyzeMessages(
                List.of(htmlMessage("Spam <x@spam.example>", "hello")), store);

        assertCategory(Category.UNSAFE, result);
        assertTrue(result.unfiltered.isEmpty(), "unsafe message must not also appear as normal");
    }

    @Test
    void everyMessageLandsInExactlyOneTab() {
        Analysis result = analyze(
                verified(htmlMessage("A <a@csusm.edu>", "clean"), "csusm.edu"),
                htmlMessage("B <b@csusm.edu>", "<a href=\"https://evil.xyz\">x</a>"),
                htmlMessage("C <c@example.com>", "clean"),
                htmlMessage("D <d@example.com>", "<a href=\"https://t.co/abc\">x</a>"));

        assertEquals(1, result.filtered.size());
        assertEquals(1, result.unfiltered.size());
        assertEquals(2, result.unsafe.size());
    }

    @Test
    void htmlLinkIsScannedWhenPlainTextPartComesFirst() {
        // The usual shape of real mail. The old extractor stopped at the
        // text/plain part and never looked at the HTML.
        Message message = message(
                List.of(header("From", "IT <it@example.com>")),
                multipart("multipart/alternative",
                        part("text/plain", "Your password expires today. Click the button."),
                        part("text/html", "<a href=\"https://bit.ly/reset\">Reset password</a>")));

        Analysis result = analyze(message);

        assertCategory(Category.UNSAFE, result);
        assertTrue(result.unsafe.get(0).reasons.contains("Redirector: bit.ly"));
    }

    @Test
    void linkInPlainTextOnlyMessageIsScanned() {
        Message message = message(
                List.of(header("From", "x@example.com")),
                part("text/plain", "Claim your prize at https://prize.xyz/claim now!"));

        assertCategory(Category.UNSAFE, analyze(message));
    }

    @Test
    void angleBracketUrlInPlainTextIsScanned() {
        Message message = message(
                List.of(header("From", "x@example.com")),
                part("text/plain", "Log in here: <https://t.co/abc>"));

        assertCategory(Category.UNSAFE, analyze(message));
    }

    @Test
    void htmlInsideNestedMultipartIsScanned() {
        Message message = message(
                List.of(header("From", "x@example.com")),
                multipart("multipart/mixed",
                        multipart("multipart/alternative",
                                part("text/plain", "See attached."),
                                part("text/html", "<a href=\"https://evil.top/\">Invoice</a>")),
                        part("application/pdf", "%PDF-1.4")));

        assertCategory(Category.UNSAFE, analyze(message));
    }

    @Test
    void bodyIsDecodedWithDeclaredCharset() {
        MessagePart latin1 = new MessagePart()
                .setMimeType("text/plain")
                .setHeaders(List.of(header("Content-Type", "text/plain; charset=\"ISO-8859-1\"")))
                .setBody(new MessagePartBody().setData(Base64.getUrlEncoder()
                        .encodeToString("Caf\u00E9".getBytes(StandardCharsets.ISO_8859_1))));

        Message message = new Message().setId("1").setPayload(latin1);

        assertEquals("Caf\u00E9\n", EmailAnalyzer.extractBodies(message).text());
    }

    @Test
    void sameRiskyLinkTwiceGivesOneReason() {
        Analysis result = analyze(htmlMessage("x@example.com",
                "<a href=\"https://bit.ly/a\">One</a> <a href=\"https://bit.ly/b\">Two</a>"));

        assertEquals(List.of("Redirector: bit.ly"), result.unsafe.get(0).reasons);
    }

    // ---- helpers -----------------------------------------------------------

    private Analysis analyze(Message... messages) {
        CompaniesStore store = new CompaniesStore();
        store.addAllowed("csusm.edu");
        return analyzer.analyzeMessages(List.of(messages), store);
    }

    private static void assertCategory(Category expected, Analysis result) {
        List<Msg> all = new java.util.ArrayList<>();
        all.addAll(result.filtered);
        all.addAll(result.unfiltered);
        all.addAll(result.unsafe);

        assertEquals(1, all.size(), "expected exactly one message in exactly one tab");
        assertEquals(expected, all.get(0).category);
    }

    static Message htmlMessage(String from, String html) {
        return message(List.of(header("From", from), header("Subject", "Test")),
                part("text/html", html));
    }

    static Message message(List<MessagePartHeader> headers, MessagePart payload) {
        payload.setHeaders(new java.util.ArrayList<>(headers));
        return new Message().setId("id-" + System.nanoTime()).setPayload(payload);
    }

    /** Adds the Authentication-Results header Gmail writes for a passing message. */
    static Message verified(Message message, String domain) {
        return withHeader(message, "Authentication-Results",
                "mx.google.com; dkim=pass header.i=@" + domain + " header.s=s1 header.b=abc; "
                        + "spf=pass (google.com: domain of x@" + domain + " designates 1.2.3.4) "
                        + "smtp.mailfrom=x@" + domain + "; "
                        + "dmarc=pass (p=REJECT sp=REJECT dis=NONE) header.from=" + domain);
    }

    static Message withHeader(Message message, String name, String value) {
        message.getPayload().getHeaders().add(header(name, value));
        return message;
    }

    static MessagePartHeader header(String name, String value) {
        return new MessagePartHeader().setName(name).setValue(value);
    }

    static MessagePart part(String mimeType, String content) {
        String data = Base64.getUrlEncoder()
                .encodeToString(content.getBytes(StandardCharsets.UTF_8));

        return new MessagePart()
                .setMimeType(mimeType)
                .setBody(new MessagePartBody().setData(data));
    }

    static MessagePart multipart(String mimeType, MessagePart... children) {
        return new MessagePart()
                .setMimeType(mimeType)
                .setBody(new MessagePartBody())
                .setParts(List.of(children));
    }
}