package featurecat.lizzie.gui;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.remote.RemoteComputeConfig;
import featurecat.lizzie.analysis.remote.ZhiziAccountService;
import featurecat.lizzie.analysis.remote.ZhiziApiClient;
import featurecat.lizzie.analysis.remote.ZhiziApiException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

/** Paged, read-only Zhizi usage and account credit details. */
final class ZhiziAccountDetailsDialog extends JDialog {
  private static final int PAGE_SIZE = 20;
  private static final Color BACKGROUND = new Color(250, 247, 240);
  private static final Color TEXT = new Color(43, 39, 31);
  private static final Color MUTED = new Color(112, 104, 90);
  private static final Color GREEN = new Color(43, 139, 90);
  private static final DateTimeFormatter DATE_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

  private final ZhiziAccountService accountService;
  private final String accountToken;
  private final JTable usageTable = new JTable();
  private final JTable creditTable = new JTable();
  private final JLabel usagePageLabel = new JLabel();
  private final JLabel creditPageLabel = new JLabel();
  private final JLabel statusLabel = new JLabel(" ");
  private final JButton usagePrevious =
      new JButton(text("RemoteCompute.account.previous", "Previous"));
  private final JButton usageNext = new JButton(text("RemoteCompute.account.next", "Next"));
  private final JButton usageRefresh =
      new JButton(text("RemoteCompute.account.refresh", "Refresh"));
  private final JButton creditPrevious =
      new JButton(text("RemoteCompute.account.previous", "Previous"));
  private final JButton creditNext = new JButton(text("RemoteCompute.account.next", "Next"));
  private final JButton creditRefresh =
      new JButton(text("RemoteCompute.account.refresh", "Refresh"));

  private SwingWorker<?, ?> usageWorker;
  private SwingWorker<?, ?> creditWorker;
  private int usagePage;
  private int creditPage;
  private long usageTotal;
  private long creditTotal;

  ZhiziAccountDetailsDialog(
      Frame owner, ZhiziAccountService accountService, String accountToken, int initialTab) {
    super(owner, text("RemoteCompute.account.detailsTitle", "Zhizi account details"), false);
    this.accountService = accountService;
    this.accountToken = accountToken == null ? "" : accountToken;
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setMinimumSize(new Dimension(760, 480));
    setPreferredSize(new Dimension(900, 560));
    setContentPane(buildContent(initialTab));
    configureActions();
    AccessibilitySupport.installEscapeToClose(getRootPane(), this);
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosed(WindowEvent event) {
            cancelWorkers();
          }
        });
    pack();
    setLocationRelativeTo(owner);
    LizzieFrame.constrainWindowToAvailableWorkArea(this);
    loadUsagePage(0);
    loadCreditPage(0);
  }

  private JPanel buildContent(int initialTab) {
    JPanel root = new JPanel(new BorderLayout(0, 14));
    root.setBackground(BACKGROUND);
    root.setBorder(new EmptyBorder(22, 24, 18, 24));

    JLabel title = new JLabel(text("RemoteCompute.account.detailsTitle", "Zhizi account details"));
    title.setForeground(TEXT);
    title.setFont(title.getFont().deriveFont(Font.BOLD, 24F));
    root.add(title, BorderLayout.NORTH);

    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab(text("RemoteCompute.account.usageTab", "Usage"), buildUsagePanel());
    tabs.addTab(text("RemoteCompute.account.creditTab", "Funds"), buildCreditPanel());
    tabs.setSelectedIndex(Math.max(0, Math.min(1, initialTab)));
    AccessibilitySupport.named(
        tabs,
        text("RemoteCompute.account.detailsTitle", "Zhizi account details"),
        text("RemoteCompute.account.detailsDescription", "Review recent usage and funds."));
    root.add(tabs, BorderLayout.CENTER);

    statusLabel.setForeground(MUTED);
    statusLabel.setFont(statusLabel.getFont().deriveFont(13F));
    AccessibilitySupport.named(
        statusLabel,
        text("RemoteCompute.connectionStatus", "Connection status"),
        statusLabel.getText());
    root.add(statusLabel, BorderLayout.SOUTH);
    return root;
  }

  private JPanel buildUsagePanel() {
    configureTable(
        usageTable,
        new String[] {
          text("RemoteCompute.account.time", "Time"),
          text("RemoteCompute.account.plan", "Plan"),
          text("RemoteCompute.account.duration", "Duration"),
          text("RemoteCompute.account.cost", "Cost"),
          text("RemoteCompute.account.state", "Status")
        });
    return pagedPanel(usageTable, usagePrevious, usagePageLabel, usageNext, usageRefresh);
  }

  private JPanel buildCreditPanel() {
    configureTable(
        creditTable,
        new String[] {
          text("RemoteCompute.account.time", "Time"),
          text("RemoteCompute.account.creditType", "Type"),
          text("RemoteCompute.account.amount", "Amount"),
          text("RemoteCompute.account.source", "Source")
        });
    return pagedPanel(creditTable, creditPrevious, creditPageLabel, creditNext, creditRefresh);
  }

  private JPanel pagedPanel(
      JTable table, JButton previous, JLabel page, JButton next, JButton refresh) {
    JPanel panel = new JPanel(new BorderLayout(0, 10));
    panel.setOpaque(false);
    JScrollPane scroll = new JScrollPane(table);
    scroll.setBorder(BorderFactory.createLineBorder(new Color(221, 211, 190)));
    panel.add(scroll, BorderLayout.CENTER);

    JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    controls.setOpaque(false);
    page.setForeground(MUTED);
    controls.add(previous);
    controls.add(page);
    controls.add(next);
    controls.add(refresh);
    for (JButton button : new JButton[] {previous, next, refresh}) {
      AccessibilitySupport.button(button, button.getText(), button.getText());
    }
    panel.add(controls, BorderLayout.SOUTH);
    return panel;
  }

  private static void configureTable(JTable table, String[] columns) {
    table.setModel(
        new DefaultTableModel(columns, 0) {
          @Override
          public boolean isCellEditable(int row, int column) {
            return false;
          }
        });
    table.setRowHeight(30);
    table.setFillsViewportHeight(true);
    table.setAutoCreateRowSorter(true);
    table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
    table.getTableHeader().setReorderingAllowed(false);
    AccessibilitySupport.named(
        table, text("RemoteCompute.account.detailsTitle", "Zhizi account details"), "");
  }

  private void loadUsagePage(int requestedPage) {
    if (usageWorker != null && !usageWorker.isDone()) {
      return;
    }
    int targetPage = Math.max(0, requestedPage);
    setUsageBusy(true);
    status(text("RemoteCompute.account.loadingUsage", "Loading usage..."), false);
    usageWorker =
        new SwingWorker<ZhiziApiClient.UsagePage, Void>() {
          @Override
          protected ZhiziApiClient.UsagePage doInBackground() throws Exception {
            return accountService.fetchUsages(accountToken, targetPage, PAGE_SIZE);
          }

          @Override
          protected void done() {
            try {
              if (!isCancelled()) {
                showUsagePage(get());
                status(text("RemoteCompute.account.updated", "Account data updated."), false);
              }
            } catch (Exception error) {
              handleFailure(error);
            } finally {
              usageWorker = null;
              setUsageBusy(false);
            }
          }
        };
    usageWorker.execute();
  }

  private void loadCreditPage(int requestedPage) {
    if (creditWorker != null && !creditWorker.isDone()) {
      return;
    }
    int targetPage = Math.max(0, requestedPage);
    setCreditBusy(true);
    status(text("RemoteCompute.account.loadingCredits", "Loading funds..."), false);
    creditWorker =
        new SwingWorker<ZhiziApiClient.CreditPage, Void>() {
          @Override
          protected ZhiziApiClient.CreditPage doInBackground() throws Exception {
            return accountService.fetchCredits(accountToken, targetPage, PAGE_SIZE);
          }

          @Override
          protected void done() {
            try {
              if (!isCancelled()) {
                showCreditPage(get());
                status(text("RemoteCompute.account.updated", "Account data updated."), false);
              }
            } catch (Exception error) {
              handleFailure(error);
            } finally {
              creditWorker = null;
              setCreditBusy(false);
            }
          }
        };
    creditWorker.execute();
  }

  private void showUsagePage(ZhiziApiClient.UsagePage page) {
    usagePage = page.page;
    usageTotal = page.total;
    DefaultTableModel model = (DefaultTableModel) usageTable.getModel();
    model.setRowCount(0);
    for (ZhiziApiClient.UsageRecord item : page.items) {
      model.addRow(
          new Object[] {
            formatTime(item.startedAt),
            usagePlan(item),
            formatDuration(item.durationSeconds),
            money(item.totalCostYuan),
            usageState(item)
          });
    }
    updateUsageControls();
  }

  private void showCreditPage(ZhiziApiClient.CreditPage page) {
    creditPage = page.page;
    creditTotal = page.total;
    DefaultTableModel model = (DefaultTableModel) creditTable.getModel();
    model.setRowCount(0);
    for (ZhiziApiClient.CreditRecord item : page.items) {
      String source = !item.productName.isBlank() ? item.productName : item.source;
      model.addRow(
          new Object[] {
            formatTime(item.createdAt), creditType(item.creditType), money(item.amountYuan), source
          });
    }
    updateCreditControls();
  }

  private void setUsageBusy(boolean busy) {
    usagePrevious.setEnabled(!busy && usagePage > 0);
    usageNext.setEnabled(!busy && (long) (usagePage + 1) * PAGE_SIZE < usageTotal);
    usageRefresh.setEnabled(!busy);
  }

  private void setCreditBusy(boolean busy) {
    creditPrevious.setEnabled(!busy && creditPage > 0);
    creditNext.setEnabled(!busy && (long) (creditPage + 1) * PAGE_SIZE < creditTotal);
    creditRefresh.setEnabled(!busy);
  }

  private void updateUsageControls() {
    usagePageLabel.setText(pageText(usagePage, usageTotal));
    setUsageBusy(false);
  }

  private void updateCreditControls() {
    creditPageLabel.setText(pageText(creditPage, creditTotal));
    setCreditBusy(false);
  }

  private void configureActions() {
    usagePrevious.addActionListener(event -> loadUsagePage(usagePage - 1));
    usageNext.addActionListener(event -> loadUsagePage(usagePage + 1));
    usageRefresh.addActionListener(event -> loadUsagePage(usagePage));
    creditPrevious.addActionListener(event -> loadCreditPage(creditPage - 1));
    creditNext.addActionListener(event -> loadCreditPage(creditPage + 1));
    creditRefresh.addActionListener(event -> loadCreditPage(creditPage));
  }

  private void handleFailure(Exception error) {
    Throwable cause = error.getCause() == null ? error : error.getCause();
    ZhiziApiException apiError = findApiError(cause);
    if (apiError != null && apiError.isUnauthorized()) {
      RemoteComputeConfig.invalidateZhiziToken();
    }
    status(RemoteComputeConfig.friendlyZhiziErrorMessage(cause, ""), true);
  }

  private void status(String message, boolean error) {
    statusLabel.setText(message == null ? "" : message);
    statusLabel.setForeground(error ? new Color(190, 69, 56) : MUTED);
    AccessibilitySupport.named(
        statusLabel,
        text("RemoteCompute.connectionStatus", "Connection status"),
        statusLabel.getText());
  }

  private void cancelWorkers() {
    if (usageWorker != null) {
      usageWorker.cancel(true);
    }
    if (creditWorker != null) {
      creditWorker.cancel(true);
    }
  }

  private static String pageText(int page, long total) {
    return MessageFormat.format(
        text("RemoteCompute.account.page", "Page {0} · {1} records"), page + 1, total);
  }

  private static String usagePlan(ZhiziApiClient.UsageRecord item) {
    String value = item.gpuType.isBlank() ? item.engineType : item.gpuType;
    if (item.vip) {
      return text("RemoteCompute.account.vip", "VIP") + (value.isBlank() ? "" : " · " + value);
    }
    return value;
  }

  private static String usageState(ZhiziApiClient.UsageRecord item) {
    if (item.finished) {
      return text("RemoteCompute.account.finished", "Finished");
    }
    if (item.ready) {
      return text("RemoteCompute.account.running", "Running");
    }
    return text("RemoteCompute.account.preparing", "Preparing");
  }

  private static String creditType(String type) {
    switch (type == null ? "" : type) {
      case "CASH":
        return text("RemoteCompute.account.creditCash", "Cash");
      case "COUPON":
        return text("RemoteCompute.account.creditCoupon", "Coupon");
      case "PURCHASE_PRODUCT":
        return text("RemoteCompute.account.creditProduct", "Product");
      default:
        return type == null ? "" : type;
    }
  }

  private static String money(BigDecimal amount) {
    BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
    return "¥" + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
  }

  private static String formatTime(Instant instant) {
    return instant == null ? "-" : DATE_TIME.format(instant);
  }

  private static String formatDuration(long seconds) {
    long safe = Math.max(0L, seconds);
    long hours = safe / 3600;
    long minutes = (safe % 3600) / 60;
    if (hours > 0) {
      return MessageFormat.format(
          text("RemoteCompute.account.hoursMinutes", "{0}h {1}m"), hours, minutes);
    }
    return MessageFormat.format(
        text("RemoteCompute.account.minutes", "{0} min"), Math.max(1L, minutes));
  }

  private static ZhiziApiException findApiError(Throwable error) {
    Throwable current = error;
    for (int depth = 0; current != null && depth < 8; depth++) {
      if (current instanceof ZhiziApiException) {
        return (ZhiziApiException) current;
      }
      current = current.getCause();
    }
    return null;
  }

  private static String text(String key, String fallback) {
    try {
      if (Lizzie.resourceBundle != null && Lizzie.resourceBundle.containsKey(key)) {
        return Lizzie.resourceBundle.getString(key);
      }
    } catch (Exception ignored) {
    }
    return fallback;
  }
}
