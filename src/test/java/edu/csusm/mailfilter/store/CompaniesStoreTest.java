package edu.csusm.mailfilter.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompaniesStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void exactDomainMatches() {
        CompaniesStore store = new CompaniesStore();
        store.addAllowed("csusm.edu");

        assertTrue(store.isAllowed("csusm.edu"));
    }

    @Test
    void subdomainMatches() {
        CompaniesStore store = new CompaniesStore();
        store.addAllowed("csusm.edu");

        assertTrue(store.isAllowed("mail.csusm.edu"));
        assertTrue(store.isAllowed("a.b.csusm.edu"));
    }

    @Test
    void lookalikeDomainsDoNotMatch() {
        CompaniesStore store = new CompaniesStore();
        store.addAllowed("csusm.edu");

        assertFalse(store.isAllowed("evil-csusm.edu"));
        assertFalse(store.isAllowed("csusm.edu.evil.com"));
        assertFalse(store.isAllowed("notcsusm.edu"));
    }

    @Test
    void matchingIgnoresCaseAndWhitespace() {
        CompaniesStore store = new CompaniesStore();
        store.addBlocked("  Spam.Example ");

        assertTrue(store.isBlocked("SPAM.example"));
        assertTrue(store.isBlockedByHost("www.spam.example"));
    }

    @Test
    void nullDomainIsNeverAllowedOrBlocked() {
        CompaniesStore store = new CompaniesStore();
        store.addAllowed("csusm.edu");
        store.addBlocked("spam.example");

        assertFalse(store.isAllowed(null));
        assertFalse(store.isBlocked(null));
        assertFalse(store.isBlockedByHost(null));
    }

    @Test
    void addAndRemoveReportWhetherAnythingChanged() {
        CompaniesStore store = new CompaniesStore();

        assertTrue(store.addBlocked("spam.example"));
        assertFalse(store.addBlocked("SPAM.EXAMPLE"), "duplicate add should report no change");
        assertTrue(store.removeBlocked("spam.example"));
        assertFalse(store.removeBlocked("spam.example"), "second remove should report no change");
        assertFalse(store.isBlocked("spam.example"));
    }

    @Test
    void newStoreIsCreatedWithCsusmAllowed() throws IOException {
        Path path = tempDir.resolve("sub").resolve("companies.json");

        CompaniesStore store = CompaniesStore.loadOrCreate(path);

        assertTrue(Files.exists(path), "loadOrCreate should write the default file");
        assertTrue(store.isAllowed("csusm.edu"));
    }

    @Test
    void savedListsSurviveReload() throws IOException {
        Path path = tempDir.resolve("companies.json");

        CompaniesStore store = CompaniesStore.loadOrCreate(path);
        store.addAllowed("partner.org");
        store.addBlocked("spam.example");
        store.save(path);

        CompaniesStore reloaded = CompaniesStore.loadOrCreate(path);

        assertTrue(reloaded.isAllowed("partner.org"));
        assertTrue(reloaded.isAllowed("csusm.edu"));
        assertTrue(reloaded.isBlocked("spam.example"));
    }

    @Test
    void nullListInFileIsTreatedAsEmpty() throws IOException {
        Path path = tempDir.resolve("companies.json");
        Files.writeString(path, "{\"allow\": [\"csusm.edu\"], \"block\": null}");

        CompaniesStore store = CompaniesStore.loadOrCreate(path);

        assertFalse(store.isBlocked("anything.com"));
        assertTrue(store.addBlocked("spam.example"));
    }

    @Test
    void invalidJsonIsReportedAsIOExceptionAndFileIsKept() throws IOException {
        Path path = tempDir.resolve("companies.json");
        String garbage = "this is not json";
        Files.writeString(path, garbage);

        boolean threw = false;

        try {
            CompaniesStore.loadOrCreate(path);
        } catch (IOException expected) {
            threw = true;
        }

        assertTrue(threw, "bad JSON should surface as IOException, not crash the app");
        assertEquals(garbage, Files.readString(path), "the user's file must not be overwritten");
    }
}