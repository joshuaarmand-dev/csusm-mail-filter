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
import java.io.InputStream;
import java.io.InputStreamReader;

import java.nio.file.Path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GmailService {
    private static final String APPLICATION_NAME = "CSUSM Mail Filter (Gmail)";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private static final Path TOKENS_DIR_PATH =
            Path.of(System.getProperty("user.home"), ".csusm-filter", "tokens");

    private static final List<String> SCOPES =
            List.of(GmailScopes.GMAIL_READONLY, GmailScopes.GMAIL_MODIFY);

    private Gmail gmail;
    private final Object gmailLock = new Object();

    public List<Message> fetchRecentInboxMessages(int maxMessages) throws Exception {
        Gmail gmailClient = ensureGmail();

        ListMessagesResponse response = gmailClient.users()
                .messages()
                .list("me")
                .setLabelIds(Collections.singletonList("INBOX"))
                .setMaxResults((long) maxMessages)
                .execute();

        List<Message> messages = new ArrayList<>();

        if (response.getMessages() != null) {
            for (Message message : response.getMessages()) {
                Message fullMessage = gmailClient.users()
                        .messages()
                        .get("me", message.getId())
                        .setFormat("full")
                        .execute();

                messages.add(fullMessage);
            }
        }

        return messages;
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