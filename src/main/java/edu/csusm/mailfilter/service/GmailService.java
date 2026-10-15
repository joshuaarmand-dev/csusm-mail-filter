package edu.csusm.mailfilter.service;

import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.nio.file.Path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

public class GmailService {
    private static final String APPLICATION_NAME = "CSUSM Mail Filter (Gmail)";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private static final Path TOKENS_DIR_PATH =
            Path.of(System.getProperty("user.home"), ".csusm-filter", "tokens");

    /**
     * Read-only access is all the app needs. If you previously signed in when
     * the app also asked for GMAIL_MODIFY, delete ~/.csusm-filter/tokens so the
     * old, broader token is replaced on the next sign-in.
     */
    private static final List<String> SCOPES = List.of(GmailScopes.GMAIL_READONLY);

    /** The most ids Gmail returns from one messages.list call. */
    static final int MAX_PAGE_SIZE = 500;

    private Gmail gmail;
    private final Object gmailLock = new Object();

    public List<Message> fetchRecentInboxMessages(int maxMessages) throws Exception {
        return fetchRecentInboxMessages(maxMessages, (done, total) -> {
        });
    }

    /**
     * Fetches up to {@code maxMessages} of the newest inbox messages in full.
     *
     * @param progress called after each message is downloaded with (done, total)
     */
    public List<Message> fetchRecentInboxMessages(int maxMessages, BiConsumer<Integer, Integer> progress)
            throws Exception {
        Gmail gmailClient = ensureGmail();

        List<String> ids = collectIds(maxMessages, (pageToken, pageSize) -> {
            ListMessagesResponse response = gmailClient.users()
                    .messages()
                    .list("me")
                    .setLabelIds(Collections.singletonList("INBOX"))
                    .setMaxResults((long) pageSize)
                    .setPageToken(pageToken)
                    .execute();

            List<String> pageIds = new ArrayList<>();

            if (response.getMessages() != null) {
                for (Message message : response.getMessages()) {
                    pageIds.add(message.getId());
                }
            }

            return new Page(pageIds, response.getNextPageToken());
        });

        List<Message> messages = new ArrayList<>();

        for (String id : ids) {
            Message fullMessage = gmailClient.users()
                    .messages()
                    .get("me", id)
                    .setFormat("full")
                    .execute();

            messages.add(fullMessage);
            progress.accept(messages.size(), ids.size());
        }

        return messages;
    }

    /** One page of message ids from messages.list, plus the token for the next page. */
    record Page(List<String> ids, String nextPageToken) {
    }

    @FunctionalInterface
    interface PageFetcher {
        Page fetch(String pageToken, int pageSize) throws IOException;
    }

    /**
     * Follows nextPageToken until {@code maxMessages} ids are collected or the
     * inbox runs out. A single messages.list call returns at most 500 ids, so
     * asking for 1000 without paging silently returned only 500.
     */
    static List<String> collectIds(int maxMessages, PageFetcher fetcher) throws IOException {
        List<String> ids = new ArrayList<>();
        String pageToken = null;

        while (ids.size() < maxMessages) {
            int pageSize = Math.min(MAX_PAGE_SIZE, maxMessages - ids.size());
            Page page = fetcher.fetch(pageToken, pageSize);

            for (String id : page.ids()) {
                if (ids.size() < maxMessages) {
                    ids.add(id);
                }
            }

            pageToken = page.nextPageToken();

            if (pageToken == null || pageToken.isEmpty() || page.ids().isEmpty()) {
                break;
            }
        }

        return ids;
    }

    private Gmail ensureGmail() throws Exception {
        synchronized (gmailLock) {
            if (gmail != null) {
                return gmail;
            }

            InputStream inputStream = getClass().getResourceAsStream("/credentials.json");

            if (inputStream == null) {
                throw new FileNotFoundException("credentials.json not found in resources");
            }

            GoogleClientSecrets clientSecrets =
                    GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(inputStream));

            var httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            File tokenDirectory = TOKENS_DIR_PATH.toFile();

            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport,
                    JSON_FACTORY,
                    clientSecrets,
                    SCOPES
            )
                    .setDataStoreFactory(new FileDataStoreFactory(tokenDirectory))
                    .setAccessType("offline")
                    .build();

            LocalServerReceiver receiver =
                    new LocalServerReceiver.Builder().setPort(8888).build();

            var credential = new AuthorizationCodeInstalledApp(flow, receiver)
                    .authorize("user");

            gmail = new Gmail.Builder(httpTransport, JSON_FACTORY, credential)
                    .setApplicationName(APPLICATION_NAME)
                    .build();

            return gmail;
        }
    }
}