package edu.csusm.mailfilter.ui;

import edu.csusm.mailfilter.model.Analysis;
import edu.csusm.mailfilter.model.Msg;
import edu.csusm.mailfilter.service.EmailAnalyzer;
import edu.csusm.mailfilter.service.GmailService;
import edu.csusm.mailfilter.service.LinkScanner;
import edu.csusm.mailfilter.store.CompaniesStore;
import edu.csusm.mailfilter.util.CsvExporter;
import edu.csusm.mailfilter.util.EmailUtils;

import com.google.api.services.gmail.model.Message;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.table.TableRowSorter;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import java.io.File;
import java.io.IOException;

import java.net.URI;

import java.nio.file.Files;
import java.nio.file.Path;

import java.time.LocalDate;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class CsusmMailUI extends JFrame {

    private static final Path STORE_PATH =
            Path.of(System.getProperty("user.home"), ".csusm-filter", "companies.json");

    private final JTable tblFiltered = new JTable();
    private final JTable tblUnfiltered = new JTable();
    private final JTable tblUnsafe = new JTable();

    private final JLabel status = new JLabel("Ready");

    private final JSpinner spTop =
            new JSpinner(new SpinnerNumberModel(100, 25, 1000, 25));

    private final JButton btnScan = new JButton("Scan");
    private final JButton btnOpen = new JButton("Open message");
    private final JButton btnExport = new JButton("Export CSV\u2026");
    private final JButton btnAllow = new JButton("Allow domain");
    private final JButton btnBlock = new JButton("Block domain");
    private final JButton btnRemoveAllow = new JButton("Remove from Allow");
    private final JButton btnRemoveBlock = new JButton("Remove from Block");

    private final MailTableModel modelFiltered = new MailTableModel();
    private final MailTableModel modelUnfiltered = new MailTableModel();
    private final MailTableModel modelUnsafe = new MailTableModel();

    private CompaniesStore store;

    /** Result of the most recent successful scan, for CSV export. */
    private Analysis lastAnalysis;

    private final GmailService gmailService = new GmailService();
    private final EmailAnalyzer emailAnalyzer = new EmailAnalyzer(new LinkScanner());

    private final JTabbedPane tabs = new JTabbedPane();

    private final JTextArea details = new JTextArea();

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
        top.add(btnExport);

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
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        details.setText(MessageDetails.describe(null));

        // Without explicit sizes the tables claim almost all the height and the
        // details panel opens as a sliver.
        tabs.setPreferredSize(new Dimension(1000, 340));
        JScrollPane detailsScroll = new JScrollPane(details);
        detailsScroll.setPreferredSize(new Dimension(1000, 200));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabs, detailsScroll);
        split.setResizeWeight(0.65);

        tabs.addChangeListener(e -> updateDetails());

        getContentPane().add(split, BorderLayout.CENTER);
        getContentPane().add(bottom, BorderLayout.SOUTH);

        btnScan.addActionListener(this::doScan);
        btnOpen.addActionListener(e -> openSelected());
        btnExport.addActionListener(e -> exportCsv());
        btnAllow.addActionListener(e -> allowSelectedDomain());
        btnBlock.addActionListener(e -> blockSelectedDomain());
        btnRemoveAllow.addActionListener(e -> removeSelectedFrom("allow"));
        btnRemoveBlock.addActionListener(e -> removeSelectedFrom("block"));

        SwingUtilities.invokeLater(() -> btnScan.doClick());
    }

    private void setupTable(JTable table, MailTableModel model) {
        table.setModel(model);
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        @SuppressWarnings("unchecked")
        TableRowSorter<MailTableModel> sorter =
                (TableRowSorter<MailTableModel>) table.getRowSorter();

        sorter.setSortKeys(List.of(new RowSorter.SortKey(2, SortOrder.DESCENDING)));

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateDetails();
            }
        });

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) {
                    openSelected();
                }
            }
        });

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
                statusBG("Fetching " + top + " messages…");

                List<Message> messages = gmailService.fetchRecentInboxMessages(top,
                        (done, total) -> statusBG("Fetching message " + done + " of " + total + "\u2026"));

                statusBG("Scanning for domains and link safety…");

                return emailAnalyzer.analyzeMessages(messages, store);
            }

            @Override
            protected void done() {
                try {
                    Analysis analysis = get();
                    lastAnalysis = analysis;

                    modelFiltered.set(analysis.filtered);
                    modelUnfiltered.set(analysis.unfiltered);
                    modelUnsafe.set(analysis.unsafe);
                    updateDetails();

                    status(String.format(
                            "Scan complete — Filtered: %d | Unfiltered: %d | Unsafe: %d",
                            analysis.filtered.size(),
                            analysis.unfiltered.size(),
                            analysis.unsafe.size()
                    ));
                } catch (ExecutionException ex) {
                    // get() wraps whatever doInBackground threw; show the real error.
                    Throwable cause = (ex.getCause() != null) ? ex.getCause() : ex;
                    cause.printStackTrace();
                    error("Scan failed: " + describe(cause));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    error("Scan was interrupted.");
                } finally {
                    disableControls(false);
                }
            }
        }.execute();
    }

    private static String describe(Throwable error) {
        String message = error.getMessage();

        return (message == null || message.isBlank())
                ? error.getClass().getSimpleName()
                : message;
    }

    private void allowSelectedDomain() {
        Msg msg = anySelected();

        if (msg == null) {
            return;
        }

        String domain = EmailUtils.senderDomain(msg.from);

        if (domain.isBlank()) {
            info("No sender domain found.");
            return;
        }

        if (store.addAllowed(domain)) {
            saveStore();
        }

        info("Allowed: " + domain + ". Rescan to apply.");
    }

    private void blockSelectedDomain() {
        Msg msg = anySelected();

        if (msg == null) {
            return;
        }

        String domain = EmailUtils.senderDomain(msg.from);

        if (domain.isBlank()) {
            info("No sender domain found.");
            return;
        }

        if (store.addBlocked(domain)) {
            saveStore();
        }

        info("Blocked: " + domain + ". Rescan to apply.");
    }

    private void removeSelectedFrom(String list) {
        Msg msg = anySelected();

        if (msg == null) {
            return;
        }

        String domain = EmailUtils.senderDomain(msg.from);

        boolean changed = "allow".equals(list)
                ? store.removeAllowed(domain)
                : store.removeBlocked(domain);

        if (changed) {
            saveStore();
            info("Removed " + domain + " from " + list + ".");
        } else {
            info(domain + " not in " + list + " list.");
        }
    }

    private void exportCsv() {
        if (lastAnalysis == null) {
            info("Run a scan first.");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("csusm-mail-scan-" + LocalDate.now() + ".csv"));

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path path = chooser.getSelectedFile().toPath();

        if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv")) {
            path = path.resolveSibling(path.getFileName() + ".csv");
        }

        if (Files.exists(path)) {
            int answer = JOptionPane.showConfirmDialog(this,
                    path.getFileName() + " already exists. Replace it?",
                    "Export CSV", JOptionPane.YES_NO_OPTION);

            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }

        try {
            CsvExporter.write(lastAnalysis, path);
            status("Exported " + path);
        } catch (IOException ex) {
            error("Export failed: " + ex.getMessage());
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
        Msg msg = selectedMsg();

        if (msg == null) {
            info("Select a message first.");
        }

        return msg;
    }

    /** The selected message in the current tab, or null. Never shows a dialog. */
    private Msg selectedMsg() {
        JTable table = currentTable();
        int row = table.getSelectedRow();

        if (row < 0) {
            return null;
        }

        int modelRow = table.convertRowIndexToModel(row);

        return ((MailTableModel) table.getModel()).getMsgAt(modelRow);
    }

    private void updateDetails() {
        details.setText(MessageDetails.describe(selectedMsg()));
        details.setCaretPosition(0);
    }

    private JTable currentTable() {
        JScrollPane pane = (JScrollPane) tabs.getSelectedComponent();
        return (JTable) pane.getViewport().getView();
    }

    private void openSelected() {
        Msg msg = anySelected();

        if (msg == null) {
            return;
        }

        try {
            Desktop.getDesktop().browse(URI.create(msg.webLink));
        } catch (Exception ex) {
            error("Open failed: " + ex.getMessage());
        }
    }

    private void disableControls(boolean busy) {
        btnScan.setEnabled(!busy);
        btnOpen.setEnabled(!busy);
        btnExport.setEnabled(!busy);
        btnAllow.setEnabled(!busy);
        btnBlock.setEnabled(!busy);
        btnRemoveAllow.setEnabled(!busy);
        btnRemoveBlock.setEnabled(!busy);
        spTop.setEnabled(!busy);

        setCursor(Cursor.getPredefinedCursor(
                busy ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR
        ));
    }

    private void status(String message) {
        status.setText(message);
    }

    private void statusBG(String message) {
        SwingUtilities.invokeLater(() -> status.setText(message));
    }

    private void info(String message) {
        JOptionPane.showMessageDialog(
                this,
                message,
                "Info",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void error(String message) {
        JOptionPane.showMessageDialog(
                this,
                message,
                "Error",
                JOptionPane.ERROR_MESSAGE
        );
    }

    private static void setSystemLAF() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
    }
}