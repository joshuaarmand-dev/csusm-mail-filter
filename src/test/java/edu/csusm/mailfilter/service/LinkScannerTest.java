package edu.csusm.mailfilter.service;

import edu.csusm.mailfilter.model.LinkScanResult;
import edu.csusm.mailfilter.store.CompaniesStore;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkScannerTest {

    private final LinkScanner scanner = new LinkScanner();

    @Test
    void emptyOrNullContentIsNotSuspicious() {
        assertFalse(scanner.scan(null, null).suspicious);
        assertFalse(scanner.scan("   ", null).suspicious);
    }

    @Test
    void ordinaryLinkIsNotSuspicious() {
        LinkScanResult result = scan("<a href=\"https://www.csusm.edu/library\">Library</a>");

        assertFalse(result.suspicious, "unexpected reasons: " + result.reasons);
    }

    @Test
    void nonWebLinksAreIgnored() {
        LinkScanResult result = scan(
                "<a href=\"mailto:help@csusm.edu\">Email us</a> <a href=\"#top\">Top</a>");

        assertFalse(result.suspicious);
    }

    @Test
    void urlShortenerIsFlagged() {
        LinkScanResult result = scan("<a href=\"https://bit.ly/abc123\">Click</a>");

        assertTrue(result.suspicious);
        assertReason(result, "Redirector: bit.ly");
    }

    @Test
    void redirectorSubdomainIsFlagged() {
        LinkScanResult result = scan("<a href=\"https://us21.list-manage.com/track\">Track</a>");

        assertReason(result, "Redirector: us21.list-manage.com");
    }

    @Test
    void lookalikeOfRedirectorIsNotFlaggedAsRedirector() {
        LinkScanResult result = scan("<a href=\"https://notbit.ly/abc\">Click</a>");

        assertFalse(result.reasons.stream().anyMatch(r -> r.startsWith("Redirector")),
                "notbit.ly is not bit.ly: " + result.reasons);
    }

    @Test
    void riskyTopLevelDomainIsFlagged() {
        LinkScanResult result = scan("<a href=\"https://prize-claim.xyz/win\">Claim</a>");

        assertReason(result, "Risky TLD: .xyz");
    }

    @Test
    void punycodeHostIsFlagged() {
        LinkScanResult result = scan("<a href=\"https://xn--pple-43d.com/login\">Apple</a>");

        assertReason(result, "Punycode link");
    }

    @Test
    void unicodeLookalikeHostIsFlagged() {
        // The host starts with a Cyrillic letter that looks like "a". java.net.URI reports no host
        // for it, so before the hostOf() fallback this link was silently skipped.
        LinkScanResult result = scan("<a href=\"https://\u0430pple.com/login\">Apple</a>");

        assertTrue(result.suspicious, "homograph link must not be skipped");
        assertReason(result, "Punycode link");
    }

    @Test
    void blockedLinkHostIsFlagged() {
        CompaniesStore store = new CompaniesStore();
        store.addBlocked("tracker.example");

        LinkScanResult result = scanner.scan(
                "<a href=\"https://cdn.tracker.example/p.gif\">x</a>", store);

        assertReason(result, "Blocked link host: cdn.tracker.example");
    }

    @Test
    void safeLinksWrapperIsUnwrappedToRealDestination() {
        String wrapped = "https://nam12.safelinks.protection.outlook.com/"
                + "?url=https%3A%2F%2Flogin-verify.xyz%2Faccount&amp;data=abc&amp;reserved=0";

        LinkScanResult result = scan("<a href=\"" + wrapped + "\">Verify</a>");

        assertReason(result, "Risky TLD: .xyz");
        assertFalse(result.reasons.stream().anyMatch(r -> r.contains("safelinks")),
                "should report the real destination, not the wrapper: " + result.reasons);
    }

    @Test
    void userinfoTrickReportsRealHost() {
        // Browsers go to evil.top here, not paypal.com.
        LinkScanResult result = scan("<a href=\"https://paypal.com@evil.top/login\">PayPal</a>");

        assertReason(result, "Risky TLD: .top");
    }

    @Test
    void overlongHostDoesNotCrashTheScan() {
        String host = "a".repeat(70) + ".com";

        LinkScanResult result = scan("<a href=\"https://" + host + "/\">x</a>");

        assertReason(result, "Malformed link host");
    }

    @Test
    void bareUrlsAreFoundAndTrailingPunctuationTrimmed() {
        List<String> urls = LinkScanner.findBareUrls(
                "Go to https://bit.ly/x. Or (www.example.com), or <https://a.top/b>!");

        assertEquals(List.of("https://bit.ly/x", "https://www.example.com", "https://a.top/b"), urls);
    }

    @Test
    void urlWrittenAsTextInsideHtmlIsScanned() {
        LinkScanResult result = scan("<p>Copy this into your browser: https://login.xyz/verify</p>");

        assertReason(result, "Risky TLD: .xyz");
    }

    @Test
    void plainTextBodyIsScanned() {
        LinkScanResult result = scanner.scan(null, "Track it: https://bit.ly/pkg", null);

        assertReason(result, "Redirector: bit.ly");
    }

    @Test
    void checkedLinksAreReportedAfterUnwrapping() {
        LinkScanResult result = scan("<a href=\"https://www.csusm.edu/\">Home</a> "
                + "<a href=\"https://nam12.safelinks.protection.outlook.com/?url=https%3A%2F%2Fexample.com%2Fa\">A</a> "
                + "<a href=\"mailto:x@csusm.edu\">Mail</a>");

        assertEquals(List.of("https://www.csusm.edu/", "https://example.com/a"), result.links);
    }

    // ---- helpers -----------------------------------------------------------

    private LinkScanResult scan(String html) {
        return scanner.scan(html, new CompaniesStore());
    }

    private static void assertReason(LinkScanResult result, String expectedPrefix) {
        assertTrue(result.suspicious, "expected suspicious, reasons: " + result.reasons);
        assertTrue(result.reasons.stream().anyMatch(r -> r.startsWith(expectedPrefix)),
                "expected a reason starting with \"" + expectedPrefix + "\" but got " + result.reasons);
    }
}