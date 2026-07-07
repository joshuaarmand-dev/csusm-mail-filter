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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

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

    public LinkScanResult scan(String content, CompaniesStore store) {
        boolean suspicious = false;
        List<String> reasons = new ArrayList<>();

        if (content == null || content.isBlank()) {
            return new LinkScanResult(false, reasons);
        }

        Document doc = Jsoup.parse(content);

        for (Element linkElement : doc.select("a[href]")) {
            String href = linkElement.attr("href");
            String actualUrl = unwindSafeLinks(href);
            URI uri = safeUri(actualUrl);

            if (uri == null || uri.getHost() == null) {
                continue;
            }

            String host = uri.getHost().toLowerCase(Locale.ROOT);

            if (IDN.toASCII(host).contains("xn--")) {
                suspicious = true;
                reasons.add("Punycode link: " + host);
            }

            if (matchesAny(host, REDIRECTOR_BASES)) {
                suspicious = true;
                reasons.add("Redirector: " + host);
            }

            String tld = lastLabel(host);

            if (RISKY_TLDS.contains(tld)) {
                suspicious = true;
                reasons.add("Risky TLD: ." + tld);
            }

            if (store != null && store.isBlockedByHost(host)) {
                suspicious = true;
                reasons.add("Blocked link host: " + host);
            }
        }

        return new LinkScanResult(suspicious, reasons);
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

    private URI safeUri(String value) {
        try {
            return URI.create(value);
        } catch (Exception e) {
            return null;
        }
    }
}