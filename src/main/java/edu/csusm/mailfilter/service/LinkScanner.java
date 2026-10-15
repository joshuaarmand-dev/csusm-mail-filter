package edu.csusm.mailfilter.service;

import edu.csusm.mailfilter.model.LinkScanResult;
import edu.csusm.mailfilter.store.CompaniesStore;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.IDN;
import java.net.URI;
import java.net.URLDecoder;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LinkScanner {

    private static final Set<String> REDIRECTOR_BASES = Set.of(
            "bit.ly", "t.co", "tinyurl.com", "rb.gy", "lnkd.in", "goo.gl", "ow.ly", "buff.ly",
            "cutt.ly", "is.gd", "mailchi.mp", "mandrillapp.com", "list-manage.com",
            "l.facebook.com", "lm.facebook.com", "urldefense.proofpoint.com", "safelinks.protection.outlook.com"
    );

    private static final Set<String> RISKY_TLDS = Set.of(
            "xyz", "top", "click", "work", "link", "country", "ru", "su", "cn", "tk",
            "pw", "info", "club", "rest", "surf", "zip", "mov"
    );

    private static final Pattern BARE_URL =
            Pattern.compile("(?i)\\b(?:https?://|www\\.)[^\\s<>\"']+");

    /** Scans links in an HTML body. Same as {@code scan(html, null, store)}. */
    public LinkScanResult scan(String html, CompaniesStore store) {
        return scan(html, null, store);
    }

    /**
     * Scans every link found in a message.
     *
     * @param html      the text/html body, or null. Links come from {@code <a href>}
     *                  tags and from URLs written out in the visible text.
     * @param plainText the text/plain body, or null. Links are found by pattern,
     *                  since plain text has no tags.
     */
    public LinkScanResult scan(String html, String plainText, CompaniesStore store) {
        Set<String> urls = new LinkedHashSet<>();

        if (html != null && !html.isBlank()) {
            Document doc = Jsoup.parse(html);

            for (Element linkElement : doc.select("a[href]")) {
                urls.add(linkElement.attr("href"));
            }

            urls.addAll(findBareUrls(doc.text()));
        }

        if (plainText != null && !plainText.isBlank()) {
            urls.addAll(findBareUrls(plainText));
        }

        // A LinkedHashSet so ten links to the same host give one reason, not ten.
        Set<String> reasons = new LinkedHashSet<>();
        Set<String> checked = new LinkedHashSet<>();

        for (String url : urls) {
            checkUrl(url, store, reasons, checked);
        }

        return new LinkScanResult(!reasons.isEmpty(), new ArrayList<>(reasons), new ArrayList<>(checked));
    }

    private void checkUrl(String href, CompaniesStore store, Set<String> reasons, Set<String> checked) {
        String actualUrl = unwindSafeLinks(href);
        URI uri = safeUri(actualUrl);

        String host = (uri == null) ? null : hostOf(uri);

        if (host == null) {
            return;
        }

        checked.add(actualUrl);

        String asciiHost = toAscii(host);

        if (asciiHost == null) {
            reasons.add("Malformed link host: " + host);
            return;
        }

        if (asciiHost.contains("xn--")) {
            reasons.add("Punycode link: " + host);
        }

        host = asciiHost;

        if (matchesAny(host, REDIRECTOR_BASES)) {
            reasons.add("Redirector: " + host);
        }

        String tld = lastLabel(host);

        if (RISKY_TLDS.contains(tld)) {
            reasons.add("Risky TLD: ." + tld);
        }

        if (store != null && store.isBlockedByHost(host)) {
            reasons.add("Blocked link host: " + host);
        }
    }

    /**
     * Finds URLs written out as text, such as "Visit https://bit.ly/x" or
     * "<www.example.com>". Trailing punctuation is trimmed, and "www." links
     * get an https:// prefix so they can be parsed.
     */
    static List<String> findBareUrls(String text) {
        List<String> found = new ArrayList<>();
        Matcher matcher = BARE_URL.matcher(text);

        while (matcher.find()) {
            String url = matcher.group().replaceAll("[.,;:!?)\\]'\"]+$", "");

            if (url.regionMatches(true, 0, "www.", 0, 4)) {
                url = "https://" + url;
            }

            found.add(url);
        }

        return found;
    }

    private String unwindSafeLinks(String href) {
        try {
            URI uri = new URI(href);
            String query = Optional.ofNullable(uri.getRawQuery()).orElse("");

            for (String part : query.split("&")) {
                int equalsIndex = part.indexOf('=');

                if (equalsIndex > 0 && part.substring(0, equalsIndex).equalsIgnoreCase("url")) {
                    return URLDecoder.decode(
                            part.substring(equalsIndex + 1),
                            StandardCharsets.UTF_8
                    );
                }
            }
        } catch (Exception ignored) {
        }

        return href;
    }

    private boolean matchesAny(String host, Set<String> bases) {
        for (String base : bases) {
            if (host.equals(base) || host.endsWith("." + base)) {
                return true;
            }
        }

        return false;
    }

    private String lastLabel(String host) {
        int dot = host.lastIndexOf('.');

        return dot >= 0
                ? host.substring(dot + 1)
                : host;
    }

    /**
     * Returns the lowercase host of a link, or null if it has none (mailto:, relative links).
     *
     * <p>{@link URI#getHost()} returns null for hosts containing non-ASCII letters
     * (such as a Cyrillic letter that looks like "a" in "apple.com") or underscores. Those are exactly the
     * lookalike hosts this scanner needs to see, so fall back to parsing the
     * authority by hand.</p>
     */
    static String hostOf(URI uri) {
        String host = uri.getHost();

        if (host == null) {
            String authority = uri.getAuthority();

            if (authority == null) {
                return null;
            }

            int at = authority.lastIndexOf('@');

            if (at >= 0) {
                authority = authority.substring(at + 1);
            }

            int colon = authority.lastIndexOf(':');

            if (colon >= 0 && authority.substring(colon + 1).matches("\\d*")) {
                authority = authority.substring(0, colon);
            }

            host = authority;
        }

        host = host.toLowerCase(Locale.ROOT);

        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }

        return host.isEmpty() ? null : host;
    }

    /** ASCII (punycode) form of the host, or null if it is not a valid host name. */
    private static String toAscii(String host) {
        try {
            return IDN.toASCII(host).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private URI safeUri(String value) {
        try {
            return URI.create(value);
        } catch (Exception e) {
            return null;
        }
    }
}