package edu.csusm.mailfilter.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import java.lang.reflect.Type;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public class CompaniesStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Set<String> allow = new TreeSet<>();
    private Set<String> block = new TreeSet<>();

    public static CompaniesStore loadOrCreate(Path path) throws IOException {
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                Type type = new TypeToken<CompaniesStore>() {}.getType();
                CompaniesStore store = GSON.fromJson(reader, type);

                if (store != null) {
                    return store;
                }
            }
        }

        Files.createDirectories(path.getParent());

        CompaniesStore store = new CompaniesStore();
        store.allow.add("csusm.edu");
        store.save(path);

        return store;
    }

    public void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());

        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(this, writer);
        }
    }

    public boolean addAllowed(String domain) {
        return allow.add(normalize(domain));
    }

    public boolean addBlocked(String domain) {
        return block.add(normalize(domain));
    }

    public boolean removeAllowed(String domain) {
        return allow.remove(normalize(domain));
    }

    public boolean removeBlocked(String domain) {
        return block.remove(normalize(domain));
    }

    public boolean isAllowed(String senderDomain) {
        if (senderDomain == null) {
            return false;
        }

        String domain = normalize(senderDomain);

        for (String allowedDomain : allow) {
            if (domainMatches(domain, allowedDomain)) {
                return true;
            }
        }

        return false;
    }

    public boolean isBlocked(String senderDomain) {
        if (senderDomain == null) {
            return false;
        }

        String domain = normalize(senderDomain);

        for (String blockedDomain : block) {
            if (domainMatches(domain, blockedDomain)) {
                return true;
            }
        }

        return false;
    }

    public boolean isAllowedByHost(String host) {
        if (host == null) {
            return false;
        }

        String normalizedHost = normalize(host);

        for (String allowedDomain : allow) {
            if (domainMatches(normalizedHost, allowedDomain)) {
                return true;
            }
        }

        return false;
    }

    public boolean isBlockedByHost(String host) {
        if (host == null) {
            return false;
        }

        String normalizedHost = normalize(host);

        for (String blockedDomain : block) {
            if (domainMatches(normalizedHost, blockedDomain)) {
                return true;
            }
        }

        return false;
    }

    private boolean domainMatches(String hostOrDomain, String rule) {
        String normalizedRule = normalize(rule);

        return hostOrDomain.equals(normalizedRule)
                || hostOrDomain.endsWith("." + normalizedRule);
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT).trim();
    }
}