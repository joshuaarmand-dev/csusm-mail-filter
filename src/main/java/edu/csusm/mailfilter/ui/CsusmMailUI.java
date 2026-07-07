package edu.csusm.mailfilter.ui;

import edu.csusm.mailfilter.model.Analysis;
import edu.csusm.mailfilter.model.Msg;
import edu.csusm.mailfilter.service.EmailAnalyzer;
import edu.csusm.mailfilter.service.GmailService;
import edu.csusm.mailfilter.service.LinkScanner;
import edu.csusm.mailfilter.store.CompaniesStore;
import edu.csusm.mailfilter.util.EmailUtils;

import com.google.api.services.gmail.model.Message;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
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
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;

import java.io.IOException;

import java.net.URI;

import java.nio.file.Path;

import java.util.List;

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
    private final JButton btnAllow = new JButton("Allow domain");
    private final JButton btnBlock = new JButton("Block domain");
    private final JButton btnRemoveAllow = new JButton("Remove from Allow");
    private final JButton btnRemoveBlock = new JButton("Remove from Block");

    private final MailTableModel modelFiltered = new MailTableModel();
    private final MailTableModel modelUnfiltered = new MailTableModel();
    private final MailTableModel modelUnsafe = new MailTableModel();

    private CompaniesStore store;

    private final GmailService gmailService = new GmailService();
    private final EmailAnalyzer emailAnalyzer = new EmailAnalyzer(new LinkScanner());

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

    private void setupTable(JTable table, MailTableModel model) {
        table.setModel(model);
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        @SuppressWarnings("unchecked")
        TableRowSorter<MailTableModel> sorter =
                (TableRowSorter<MailTableModel>) table.getRowSorter();

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
                statusBG("Fetching " + top + " messages…");

                List<Message> messages = gmailService.fetchRecentInboxMessages(top);

                statusBG("Scanning for domains and link safety…");

                return emailAnalyzer.analyzeMessages(messages, store);
            }

            @Override
            protected void done() {
                try {
                    Analysis analysis = get();

                    modelFiltered.set(analysis.filtered);
                    modelUnfiltered.set(analysis.unfiltered);
                    modelUnsafe.set(analysis.unsafe);

                    status(String.format(
                            "Scan complete — Filtered: %d | Unfiltered: %d | Unsafe: %d",
                            analysis.filtered.size(),
                            analysis.unfiltered.size(),
                            analysis.unsafe.size()
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

    private void saveStore() {
        try {
            store.save(STORE_PATH);
        } catch (IOException e) {
            error("Failed to save list: " + e.getMessage());
        }
    }

    private Msg anySelected() {
        JTable table = currentTable();
        int row = table.getSelectedRow();

        if (row < 0) {
            info("Select a message first.");
            return null;
        }

        int modelRow = table.convertRowIndexToModel(row);

        return ((MailTableModel) table.getModel()).getMsgAt(modelRow);
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