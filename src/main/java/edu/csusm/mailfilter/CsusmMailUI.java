package edu.csusm.mailfilter;

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
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;

import java.net.IDN;
import java.net.URI;
import java.net.URLDecoder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public class CsusmMailUI extends JFrame {

    private static final String APPLICATION_NAME = "CSUSM Mail Filter (Gmail)";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private static final Path TOKENS_DIR_PATH =
            Path.of(System.getProperty("user.home"), ".csusm-filter", "tokens");
    private static final Path STORE_PATH =
            Path.of(System.getProperty("user.home"), ".csusm-filter", "companies.json");

    // Scopes: read + modify (so we COULD label later if we want)
    private static final List<String> SCOPES =
            List.of(GmailScopes.GMAIL_READONLY, GmailScopes.GMAIL_MODIFY);

    private static final Set<String> REDIRECTOR_BASES = Set.of(
            "bit.ly", "t.co", "tinyurl.com", "rb.gy", "lnkd.in", "goo.gl", "ow.ly", "buff.ly",
            "cutt.ly", "is.gd", "mailchi.mp", "mandrillapp.com", "list-manage.com",
            "l.facebook.com", "lm.facebook.com", "urldefense.proofpoint.com", "safelinks.protection.outlook.com"
    );

    private static final Set<String> RISKY_TLDS = Set.of(
            "xyz", "top", "click", "work", "link", "country", "ru", "su", "cn", "tk",
            "pw", "info", "club", "rest", "surf", "zip", "mov"
    );

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Gmail gmail;
    private final Object gmailLock = new Object();

    private final JTable tblFiltered = new JTable();
    private final JTable tblUnfiltered = new JTable();
    private final JTable tblUnsafe = new JTable();
    private final JLabel status = new JLabel("Ready");
    private final JSpinner spTop =
            new JSpinner(new SpinnerNumberModel(100, 25, 1000, 25));

    private final JButton btnScan = new JButton("Scan");
    private final JButton btnOpen = new JButton("Open message");
    private final JButton btnAllow = new JButton("Allow domain");
    private final JButton btnBlock = new JButton("Block domain");
    private final JButton btnRemoveAllow = new JButton("Remove from Allow");
    private final JButton btnRemoveBlock = new JButton("Remove from Block");

    private final MailTableModel modelFiltered = new MailTableModel();
    private final MailTableModel modelUnfiltered = new MailTableModel();
    private final MailTableModel modelUnsafe = new MailTableModel();

    private CompaniesStore store;

    private final JTabbedPane tabs = new JTabbedPane();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            setSystemLAF();
            new CsusmMailUI().setVisible(true);
        });
    }

    public CsusmMailUI() {
        super("CSUSM Mail Filter — Gmail Edition");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);

        try {
            store = CompaniesStore.loadOrCreate(STORE_PATH);
        } catch (IOException e) {
            store = new CompaniesStore();
        }

        setupTable(tblFiltered, modelFiltered);
        setupTable(tblUnfiltered, modelUnfiltered);
        setupTable(tblUnsafe, modelUnsafe);

        tabs.add("Filtered", new JScrollPane(tblFiltered));
        tabs.add("Unfiltered", new JScrollPane(tblUnfiltered));
        tabs.add("Unsafe ⚠️", new JScrollPane(tblUnsafe));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Scan latest:"));
        top.add(spTop);
        top.add(new JLabel("messages"));
        top.add(btnScan);
        top.add(btnOpen);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(btnAllow);
        actions.add(btnBlock);
        actions.add(btnRemoveAllow);
        actions.add(btnRemoveBlock);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(actions, BorderLayout.CENTER);
        bottom.add(status, BorderLayout.SOUTH);

        getContentPane().setLayout(new BorderLayout(8, 8));
        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(tabs, BorderLayout.CENTER);
        getContentPane().add(bottom, BorderLayout.SOUTH);

        btnScan.addActionListener(this::doScan);
        btnOpen.addActionListener(e -> openSelected());
        btnAllow.addActionListener(e -> allowSelectedDomain());
        btnBlock.addActionListener(e -> blockSelectedDomain());
        btnRemoveAllow.addActionListener(e -> removeSelectedFrom("allow"));
        btnRemoveBlock.addActionListener(e -> removeSelectedFrom("block"));

        SwingUtilities.invokeLater(() -> btnScan.doClick());
    }

    // ---------- UI helpers ----------

    private void setupTable(JTable table, MailTableModel model) {
        table.setModel(model);
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        @SuppressWarnings("unchecked")
        var sorter = (TableRowSorter<MailTableModel>) table.getRowSorter();
        sorter.setSortKeys(List.of(new RowSorter.SortKey(2, SortOrder.DESCENDING)));

        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getColumnModel().getColumn(0).setPreferredWidth(380);
        table.getColumnModel().getColumn(1).setPreferredWidth(240);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setPreferredWidth(600);
    }

    private void doScan(ActionEvent ev) {
        int top = (Integer) spTop.getValue();
        disableControls(true);
        status("Authenticating…");

        new SwingWorker<Analysis, Void>() {
            @Override
            protected Analysis doInBackground() throws Exception {
                Gmail g = ensureGmail();
                statusBG("Fetching " + top + " messages…");

                ListMessagesResponse resp = g.users()
                        .messages()
                        .list("me")
                        .setLabelIds(Collections.singletonList("INBOX"))
                        .setMaxResults((long) top)
                        .execute();

                List<Message> msgs = new ArrayList<>();
                if (resp.getMessages() != null) {
                    for (Message m : resp.getMessages()) {
                        Message full = g.users().messages()
                                .get("me", m.getId())
                                .setFormat("full")
                                .execute();
                        msgs.add(full);
                    }
                }

                statusBG("Scanning for domains and link safety…");
                return analyzeMessages(msgs, store);
            }

            @Override
            protected void done() {
                try {
                    Analysis a = get();
                    modelFiltered.set(a.filtered);
                    modelUnfiltered.set(a.unfiltered);
                    modelUnsafe.set(a.unsafe);

                    status(String.format(
                            "Scan complete — Filtered: %d | Unfiltered: %d | Unsafe: %d",
                            a.filtered.size(), a.unfiltered.size(), a.unsafe.size()
                    ));
                } catch (Exception ex) {
                    ex.printStackTrace();
                    error("Scan failed: " + ex.getMessage());
                } finally {
                    disableControls(false);
                }
            }
        }.execute();
    }

    // ---------- Gmail auth ----------

    private Gmail ensureGmail() throws Exception {
        synchronized (gmailLock) {
            if (gmail != null) return gmail;

            InputStream in = getClass().getResourceAsStream("/credentials.json");
            if (in == null) {
                throw new FileNotFoundException("credentials.json not found in resources");
            }

            GoogleClientSecrets clientSecrets =
                    GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

            var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            File tokenDir = TOKENS_DIR_PATH.toFile();
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(tokenDir))
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

    // ---------- Message analysis ----------

    private Analysis analyzeMessages(List<Message> msgs, CompaniesStore store) {
        Analysis out = new Analysis();

        for (Message m : msgs) {
            String id = m.getId();
            String subj = "(no subject)";
            String fromHeader = "";
            String whenTxt = "";

            try {
                List<MessagePartHeader> headers =
                        (m.getPayload() != null && m.getPayload().getHeaders() != null)
                                ? m.getPayload().getHeaders()
                                : List.of();

                for (MessagePartHeader h : headers) {
                    String name = (h.getName() == null ? "" : h.getName()).toLowerCase(Locale.ROOT);
                    switch (name) {
                        case "subject" -> subj = h.getValue();
                        case "from" -> fromHeader = h.getValue();
                    }
                }

                Long internalDate = m.getInternalDate();
                if (internalDate != null) {
                    whenTxt = WHEN.format(Instant.ofEpochMilli(internalDate));
                }
            } catch (Exception ignored) {
            }

            String email = extractEmail(fromHeader);
            String body = extractBody(m);
            String link = "https://mail.google.com/mail/u/0/#inbox/" + id;

            Msg row = new Msg(id, subj, email, whenTxt, link);

            boolean fromSchool = isCsusm(email);
            String sdom = senderDomain(email);
            boolean fromAllowed = store.isAllowed(sdom);
            boolean fromBlocked = store.isBlocked(sdom);

            boolean unsafe = false;
            List<String> reasons = new ArrayList<>();

            if (fromBlocked) {
                unsafe = true;
                reasons.add("Sender in block list: " + sdom);
            }

            LinkScanResult r = scanLinks(body, store);
            if (r.suspicious) {
                unsafe = true;
                reasons.addAll(r.reasons);
            }

            row.classFiltered = (fromSchool || fromAllowed);
            row.classUnsafe = unsafe;
            row.reasons = reasons;

            if (row.classFiltered) out.filtered.add(row);
            else out.unfiltered.add(row);
            if (row.classUnsafe) out.unsafe.add(row);
        }

        return out;
    }

    private static String extractBody(Message msg) {
        if (msg.getPayload() == null) return "";
        return extractBodyFromPart(msg.getPayload());
    }

    private static String extractBodyFromPart(MessagePart part) {
        if (part.getBody() != null && part.getBody().getData() != null) {
            byte[] data = Base64.getUrlDecoder().decode(part.getBody().getData());
            return new String(data, StandardCharsets.UTF_8);
        }
        if (part.getParts() != null) {
            for (MessagePart p : part.getParts()) {
                String text = extractBodyFromPart(p);
                if (!text.isEmpty()) return text;
            }
        }
        return "";
    }

    private static String extractEmail(String fromHeader) {
        if (fromHeader == null) return "";
        int lt = fromHeader.indexOf('<');
        int gt = fromHeader.indexOf('>');
        String candidate = (lt >= 0 && gt > lt)
                ? fromHeader.substring(lt + 1, gt)
                : fromHeader;
        return candidate.trim();
    }

    private static boolean isCsusm(String email) {
        return email != null && email.toLowerCase(Locale.ROOT)
                .matches("^[^@]+@(?:[a-z0-9-]+\\.)*csusm\\.edu$");
    }

    private static String senderDomain(String email) {
        if (email == null) return "";
        int at = email.indexOf('@');
        return at > 0 ? email.substring(at + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static LinkScanResult scanLinks(String content, CompaniesStore store) {
        boolean suspicious = false;
        List<String> reasons = new ArrayList<>();
        if (content == null || content.isBlank()) return new LinkScanResult(false, reasons);

        Document doc = Jsoup.parse(content);

        for (Element a : doc.select("a[href]")) {
            String href = a.attr("href");
            String actual = unwindSafeLinks(href);
            URI uri = safeUri(actual);
            if (uri == null || uri.getHost() == null) continue;

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

            if (store.isBlockedByHost(host)) {
                suspicious = true;
                reasons.add("Blocked link host: " + host);
            }
        }

        return new LinkScanResult(suspicious, reasons);
    }

    private static String unwindSafeLinks(String href) {
        try {
            URI u = new URI(href);
            String q = Optional.ofNullable(u.getRawQuery()).orElse("");
            for (String part : q.split("&")) {
                int eq = part.indexOf('=');
                if (eq > 0 && part.substring(0, eq).equalsIgnoreCase("url")) {
                    return URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ignored) {
        }
        return href;
    }

    private static boolean matchesAny(String host, Set<String> bases) {
        for (String b : bases) {
            if (host.equals(b) || host.endsWith("." + b)) return true;
        }
        return false;
    }

    private static String lastLabel(String host) {
        int dot = host.lastIndexOf('.');
        return dot >= 0 ? host.substring(dot + 1) : host;
    }

    private static URI safeUri(String s) {
        try {
            return URI.create(s);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- Allow/block + UI actions ----------

    private void allowSelectedDomain() {
        Msg m = anySelected();
        if (m == null) return;
        String dom = senderDomain(m.from);
        if (dom.isBlank()) {
            info("No sender domain found.");
            return;
        }
        if (store.allow.add(dom)) saveStore();
        info("Allowed: " + dom + ". Rescan to apply.");
    }

    private void blockSelectedDomain() {
        Msg m = anySelected();
        if (m == null) return;
        String dom = senderDomain(m.from);
        if (dom.isBlank()) {
            info("No sender domain found.");
            return;
        }
        if (store.block.add(dom)) saveStore();
        info("Blocked: " + dom + ". Rescan to apply.");
    }

    private void removeSelectedFrom(String list) {
        Msg m = anySelected();
        if (m == null) return;
        String dom = senderDomain(m.from);
        boolean changed = "allow".equals(list)
                ? store.allow.remove(dom)
                : store.block.remove(dom);
        if (changed) {
            saveStore();
            info("Removed " + dom + " from " + list + ".");
        } else {
            info(dom + " not in " + list + " list.");
        }
    }

    private void saveStore() {
        try {
            store.save(STORE_PATH);
        } catch (IOException e) {
            error("Failed to save list: " + e.getMessage());
        }
    }

    private Msg anySelected() {
        JTable t = currentTable();
        int row = t.getSelectedRow();
        if (row < 0) {
            info("Select a message first.");
            return null;
        }
        int modelRow = t.convertRowIndexToModel(row);
        return ((MailTableModel) t.getModel()).rows.get(modelRow);
    }

    private JTable currentTable() {
        JScrollPane pane = (JScrollPane) tabs.getSelectedComponent();
        return (JTable) pane.getViewport().getView();
    }

    private void openSelected() {
        Msg m = anySelected();
        if (m == null) return;
        try {
            Desktop.getDesktop().browse(URI.create(m.webLink));
        } catch (Exception ex) {
            error("Open failed: " + ex.getMessage());
        }
    }

    private void disableControls(boolean busy) {
        btnScan.setEnabled(!busy);
        btnOpen.setEnabled(!busy);
        btnAllow.setEnabled(!busy);
        btnBlock.setEnabled(!busy);
        btnRemoveAllow.setEnabled(!busy);
        btnRemoveBlock.setEnabled(!busy);
        spTop.setEnabled(!busy);
        setCursor(Cursor.getPredefinedCursor(
                busy ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    private void status(String s) {
        status.setText(s);
    }

    private void statusBG(String s) {
        SwingUtilities.invokeLater(() -> status.setText(s));
    }

    private void info(String s) {
        JOptionPane.showMessageDialog(this, s,
                "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    private void error(String s) {
        JOptionPane.showMessageDialog(this, s,
                "Error", JOptionPane.ERROR_MESSAGE);
    }

    private static void setSystemLAF() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
    }

    // ---------- Data classes ----------

    private static class Msg {
        final String id;
        final String subject;
        final String from;
        final String received;
        final String webLink;
        List<String> reasons = List.of();
        boolean classFiltered;
        boolean classUnsafe;

        Msg(String id, String subject, String from, String received, String webLink) {
            this.id = id;
            this.subject = subject;
            this.from = from;
            this.received = received;
            this.webLink = webLink;
        }
    }

    private static class Analysis {
        final List<Msg> filtered = new ArrayList<>();
        final List<Msg> unfiltered = new ArrayList<>();
        final List<Msg> unsafe = new ArrayList<>();
    }

    private static class MailTableModel extends AbstractTableModel {
        private final String[] cols = {"Subject", "From", "Received", "Reasons"};
        List<Msg> rows = new ArrayList<>();

        public void set(List<Msg> list) {
            rows = new ArrayList<>(Objects.requireNonNullElse(list, List.of()));
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return cols.length;
        }

        @Override
        public String getColumnName(int c) {
            return cols[c];
        }

        @Override
        public Object getValueAt(int r, int c) {
            Msg m = rows.get(r);
            return switch (c) {
                case 0 -> m.subject;
                case 1 -> m.from;
                case 2 -> m.received;
                case 3 -> String.join("; ", m.reasons);
                default -> "";
            };
        }
    }

    private static class CompaniesStore {
        Set<String> allow = new TreeSet<>();
        Set<String> block = new TreeSet<>();

        static CompaniesStore loadOrCreate(Path p) throws IOException {
            if (Files.exists(p)) {
                try (Reader r = Files.newBufferedReader(p)) {
                    java.lang.reflect.Type t =
                            new com.google.gson.reflect.TypeToken<CompaniesStore>() {}.getType();
                    CompaniesStore s = GSON.fromJson(r, t);
                    if (s != null) return s;
                }
            }
            Files.createDirectories(p.getParent());
            CompaniesStore s = new CompaniesStore();
            s.allow.add("csusm.edu");
            s.save(p);
            return s;
        }

        void save(Path p) throws IOException {
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p)) {
                GSON.toJson(this, w);
            }
        }

        boolean isAllowed(String senderDomain) {
            if (senderDomain == null) return false;
            String d = senderDomain.toLowerCase(Locale.ROOT);
            for (String a : allow) if (domainMatches(d, a)) return true;
            return false;
        }

        boolean isBlocked(String senderDomain) {
            if (senderDomain == null) return false;
            String d = senderDomain.toLowerCase(Locale.ROOT);
            for (String b : block) if (domainMatches(d, b)) return true;
            return false;
        }

        boolean isAllowedByHost(String host) {
            host = host.toLowerCase(Locale.ROOT);
            for (String a : allow) if (domainMatches(host, a)) return true;
            return false;
        }

        boolean isBlockedByHost(String host) {
            host = host.toLowerCase(Locale.ROOT);
            for (String b : block) if (domainMatches(host, b)) return true;
            return false;
        }

        private boolean domainMatches(String hostOrDomain, String rule) {
            rule = rule.toLowerCase(Locale.ROOT);
            return hostOrDomain.equals(rule) || hostOrDomain.endsWith("." + rule);
        }
    }

    private static class LinkScanResult {
        final boolean suspicious;
        final List<String> reasons;

        LinkScanResult(boolean s, List<String> r) {
            suspicious = s;
            reasons = r;
        }
    }
}
