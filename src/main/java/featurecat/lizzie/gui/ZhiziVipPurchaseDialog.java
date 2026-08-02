package featurecat.lizzie.gui;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.remote.RemoteComputeConfig;
import featurecat.lizzie.analysis.remote.ZhiziAccountService;
import featurecat.lizzie.analysis.remote.ZhiziApiClient;
import featurecat.lizzie.analysis.remote.ZhiziApiException;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.MessageFormat;
import java.util.List;
import java.util.MissingResourceException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

/** Safe, fixed-product WeChat Native Pay flow backed by Zhizi's official API. */
final class ZhiziVipPurchaseDialog extends JDialog {
  private static final String PRODUCT_CARD = "products";
  private static final String PAYMENT_CARD = "payment";
  private static final Color BACKGROUND = new Color(250, 247, 240);
  private static final Color CARD = new Color(255, 253, 248);
  private static final Color BORDER = new Color(221, 211, 190);
  private static final Color TEXT = new Color(43, 39, 31);
  private static final Color MUTED = new Color(112, 104, 90);
  private static final Color GREEN = new Color(43, 139, 90);
  private static final Color ERROR = new Color(190, 69, 56);

  private final ZhiziApiClient apiClient;
  private final ZhiziAccountService accountService;
  private final String accountToken;
  private final Runnable paymentCompleted;
  private final CardLayout cardLayout = new CardLayout();
  private final JPanel cards = new JPanel(cardLayout);
  private final JComboBox<ProductItem> productBox = new JComboBox<>();
  private final JLabel productSummary = new JLabel(" ");
  private final JLabel productStatus = new JLabel(" ");
  private final JLabel paymentProduct = new JLabel(" ");
  private final JLabel paymentAmount = new JLabel(" ");
  private final JLabel paymentStatus = new JLabel(" ");
  private final JLabel qrCode = new JLabel();
  private final JButton createOrderButton =
      primaryButton(text("RemoteCompute.payment.createOrder", "Continue to WeChat Pay"));
  private final JButton retryButton =
      secondaryButton(text("RemoteCompute.payment.newOrder", "Create a new order"));
  private final JButton closeButton =
      secondaryButton(text("RemoteCompute.payment.close", "Close"));

  private SwingWorker<List<ZhiziApiClient.MembershipProduct>, Void> productsWorker;
  private SwingWorker<ZhiziApiClient.PaymentOrder, Void> createWorker;
  private SwingWorker<ZhiziApiClient.PaymentOrder, Void> pollWorker;
  private Timer pollTimer;
  private ZhiziApiClient.PaymentOrder currentOrder;
  private boolean creatingOrder;
  private boolean reauthenticationRequired;
  private int consecutivePollFailures;

  ZhiziVipPurchaseDialog(
      Frame owner,
      ZhiziApiClient apiClient,
      ZhiziAccountService accountService,
      String accountToken,
      Runnable paymentCompleted) {
    super(owner, text("RemoteCompute.payment.title", "Zhizi VIP membership"), true);
    this.apiClient = apiClient;
    this.accountService = accountService;
    this.accountToken = accountToken == null ? "" : accountToken.trim();
    this.paymentCompleted = paymentCompleted == null ? () -> {} : paymentCompleted;
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setMinimumSize(new Dimension(650, 500));
    setPreferredSize(new Dimension(720, 540));
    setContentPane(buildContent());
    configureActions();
    AccessibilitySupport.installEscapeToClose(getRootPane(), this);
    pack();
    setLocationRelativeTo(owner);
    LizzieFrame.constrainWindowToAvailableWorkArea(this);
    loadProducts();
  }

  private JPanel buildContent() {
    JPanel root = new JPanel(new BorderLayout(0, 18));
    root.setBackground(BACKGROUND);
    root.setBorder(new EmptyBorder(24, 28, 22, 28));

    JPanel heading = new JPanel();
    heading.setOpaque(false);
    heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
    JLabel title = new JLabel(text("RemoteCompute.payment.title", "Zhizi VIP membership"));
    title.setForeground(TEXT);
    title.setFont(title.getFont().deriveFont(Font.BOLD, 26F));
    title.setAlignmentX(Component.LEFT_ALIGNMENT);
    JLabel subtitle =
        new JLabel(
            text(
                "RemoteCompute.payment.subtitle",
                "Choose an official plan, then scan the code with WeChat."));
    subtitle.setForeground(MUTED);
    subtitle.setFont(subtitle.getFont().deriveFont(14F));
    subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
    heading.add(title);
    heading.add(Box.createVerticalStrut(5));
    heading.add(subtitle);
    root.add(heading, BorderLayout.NORTH);

    cards.setOpaque(false);
    cards.add(buildProductCard(), PRODUCT_CARD);
    cards.add(buildPaymentCard(), PAYMENT_CARD);
    root.add(cards, BorderLayout.CENTER);
    return root;
  }

  private JPanel buildProductCard() {
    JPanel panel = cardPanel();
    panel.setLayout(new GridBagLayout());
    GridBagConstraints gbc = constraints();

    JLabel label = new JLabel(text("RemoteCompute.payment.plan", "Official VIP plan"));
    label.setForeground(TEXT);
    label.setFont(label.getFont().deriveFont(Font.BOLD, 15F));
    AccessibilitySupport.labelFor(
        label,
        productBox,
        text("RemoteCompute.payment.planDescription", "Select an official Zhizi VIP plan."));
    panel.add(label, gbc);

    gbc.gridy++;
    gbc.insets = new Insets(8, 0, 0, 0);
    productBox.setPreferredSize(new Dimension(520, 48));
    productBox.setRenderer(new ProductRenderer());
    productBox.setEnabled(false);
    panel.add(productBox, gbc);

    gbc.gridy++;
    gbc.insets = new Insets(14, 0, 0, 0);
    productSummary.setForeground(TEXT);
    productSummary.setFont(productSummary.getFont().deriveFont(Font.BOLD, 18F));
    panel.add(productSummary, gbc);

    gbc.gridy++;
    gbc.insets = new Insets(8, 0, 0, 0);
    productStatus.setForeground(MUTED);
    productStatus.setFont(productStatus.getFont().deriveFont(13F));
    panel.add(productStatus, gbc);

    gbc.gridy++;
    gbc.insets = new Insets(24, 0, 0, 0);
    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    actions.setOpaque(false);
    stylePrimary(createOrderButton);
    JButton cancel = secondaryButton(text("RemoteCompute.payment.cancel", "Cancel"));
    styleSecondary(cancel);
    cancel.addActionListener(event -> dispose());
    actions.add(cancel);
    actions.add(createOrderButton);
    panel.add(actions, gbc);

    gbc.gridy++;
    gbc.weighty = 1;
    panel.add(Box.createVerticalGlue(), gbc);
    return panel;
  }

  private JPanel buildPaymentCard() {
    JPanel panel = cardPanel();
    panel.setLayout(new BorderLayout(22, 0));

    qrCode.setPreferredSize(new Dimension(276, 276));
    qrCode.setHorizontalAlignment(JLabel.CENTER);
    qrCode.setVerticalAlignment(JLabel.CENTER);
    qrCode.setOpaque(true);
    qrCode.setBackground(Color.WHITE);
    qrCode.setBorder(BorderFactory.createLineBorder(BORDER));
    AccessibilitySupport.named(
        qrCode,
        text("RemoteCompute.payment.qrName", "WeChat payment QR code"),
        text("RemoteCompute.payment.qrDescription", "Scan this code with WeChat to pay."));
    panel.add(qrCode, BorderLayout.WEST);

    JPanel details = new JPanel();
    details.setOpaque(false);
    details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
    JLabel scan = new JLabel(text("RemoteCompute.payment.scan", "Scan with WeChat"));
    scan.setForeground(TEXT);
    scan.setFont(scan.getFont().deriveFont(Font.BOLD, 23F));
    scan.setAlignmentX(Component.LEFT_ALIGNMENT);
    details.add(scan);
    details.add(Box.createVerticalStrut(14));
    paymentProduct.setForeground(TEXT);
    paymentProduct.setFont(paymentProduct.getFont().deriveFont(Font.BOLD, 16F));
    paymentProduct.setAlignmentX(Component.LEFT_ALIGNMENT);
    details.add(paymentProduct);
    details.add(Box.createVerticalStrut(8));
    paymentAmount.setForeground(GREEN);
    paymentAmount.setFont(paymentAmount.getFont().deriveFont(Font.BOLD, 24F));
    paymentAmount.setAlignmentX(Component.LEFT_ALIGNMENT);
    details.add(paymentAmount);
    details.add(Box.createVerticalStrut(16));
    paymentStatus.setForeground(MUTED);
    paymentStatus.setFont(paymentStatus.getFont().deriveFont(Font.BOLD, 13F));
    paymentStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
    details.add(paymentStatus);
    details.add(Box.createVerticalStrut(18));
    JLabel provider =
        new JLabel(
            "<html>"
                + text(
                    "RemoteCompute.payment.providerNotice",
                    "The VIP service and payment are provided by Zhizi. LizzieYzy Next does not store payment details.")
                + "</html>");
    provider.setForeground(MUTED);
    provider.setFont(provider.getFont().deriveFont(12.5F));
    provider.setAlignmentX(Component.LEFT_ALIGNMENT);
    details.add(provider);
    details.add(Box.createVerticalGlue());

    retryButton.setVisible(false);
    styleSecondary(retryButton);
    closeButton.setText(text("RemoteCompute.payment.close", "Close"));
    styleSecondary(closeButton);
    JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    actions.setOpaque(false);
    actions.setAlignmentX(Component.LEFT_ALIGNMENT);
    actions.add(retryButton);
    actions.add(closeButton);
    details.add(actions);
    panel.add(details, BorderLayout.CENTER);
    return panel;
  }

  private void configureActions() {
    productBox.addActionListener(event -> showSelectedProduct());
    createOrderButton.addActionListener(event -> confirmAndCreateOrder());
    retryButton.addActionListener(
        event -> {
          if (reauthenticationRequired) {
            dispose();
          } else {
            resetForNewOrder();
          }
        });
    closeButton.addActionListener(event -> dispose());
    AccessibilitySupport.button(
        createOrderButton,
        createOrderButton.getText(),
        text("RemoteCompute.payment.createOrderDescription", "Review and create one WeChat order."));
    AccessibilitySupport.button(retryButton, retryButton.getText(), retryButton.getText());
    AccessibilitySupport.button(closeButton, closeButton.getText(), closeButton.getText());
  }

  private void loadProducts() {
    productStatus.setText(text("RemoteCompute.payment.loadingProducts", "Loading official plans..."));
    createOrderButton.setEnabled(false);
    productsWorker =
        new SwingWorker<List<ZhiziApiClient.MembershipProduct>, Void>() {
          @Override
          protected List<ZhiziApiClient.MembershipProduct> doInBackground() throws Exception {
            return apiClient.fetchMembershipProducts();
          }

          @Override
          protected void done() {
            try {
              if (isCancelled()) {
                return;
              }
              List<ZhiziApiClient.MembershipProduct> products = get();
              productBox.removeAllItems();
              for (ZhiziApiClient.MembershipProduct product : products) {
                productBox.addItem(new ProductItem(product));
              }
              boolean available = productBox.getItemCount() > 0;
              productBox.setEnabled(available);
              createOrderButton.setEnabled(available);
              productStatus.setText(
                  available
                      ? text(
                          "RemoteCompute.payment.livePrice",
                          "The price is loaded from Zhizi immediately before purchase.")
                      : text(
                          "RemoteCompute.payment.noProducts",
                          "No official VIP plan is currently available. Use the Zhizi app instead."));
              showSelectedProduct();
            } catch (Exception failure) {
              productBox.setEnabled(false);
              createOrderButton.setEnabled(false);
              productStatus.setForeground(ERROR);
              productStatus.setText(
                  text(
                      "RemoteCompute.payment.productsFailed",
                      "Could not load official plans. No order was created."));
            } finally {
              productsWorker = null;
            }
          }
        };
    productsWorker.execute();
  }

  private void showSelectedProduct() {
    ProductItem selected = (ProductItem) productBox.getSelectedItem();
    if (selected == null) {
      productSummary.setText(" ");
      return;
    }
    productSummary.setText(
        format(
            "RemoteCompute.payment.summary",
            "{0} · {1}",
            monthsText(selected.product.durationMonths),
            formatFen(selected.product.priceFen)));
  }

  private void confirmAndCreateOrder() {
    if (creatingOrder || currentOrder != null) {
      return;
    }
    ProductItem selected = (ProductItem) productBox.getSelectedItem();
    if (selected == null) {
      return;
    }
    ZhiziApiClient.MembershipProduct product = selected.product;
    String confirmation =
        "<html><b>"
            + text("RemoteCompute.payment.confirmTitle", "Confirm VIP order")
            + "</b><br><br>"
            + format(
                "RemoteCompute.payment.confirmProduct",
                "Plan: {0}",
                monthsText(product.durationMonths))
            + "<br>"
            + format(
                "RemoteCompute.payment.confirmAmount", "Amount: {0}", formatFen(product.priceFen))
            + "<br>"
            + text("RemoteCompute.payment.confirmMethod", "Payment: WeChat Pay")
            + "<br><br>"
            + text("RemoteCompute.payment.confirmProvider", "Service is provided by Zhizi.")
            + "</html>";
    int answer =
        JOptionPane.showConfirmDialog(
            this,
            confirmation,
            text("RemoteCompute.payment.confirmTitle", "Confirm VIP order"),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE);
    if (answer != JOptionPane.OK_OPTION) {
      return;
    }
    createOrder(product);
  }

  private void createOrder(ZhiziApiClient.MembershipProduct product) {
    creatingOrder = true;
    createOrderButton.setEnabled(false);
    productBox.setEnabled(false);
    productStatus.setForeground(MUTED);
    productStatus.setText(text("RemoteCompute.payment.creating", "Creating one secure order..."));
    createWorker =
        new SwingWorker<ZhiziApiClient.PaymentOrder, Void>() {
          @Override
          protected ZhiziApiClient.PaymentOrder doInBackground() throws Exception {
            return apiClient.createMembershipOrder(accountToken, product, false);
          }

          @Override
          protected void done() {
            try {
              if (isCancelled()) {
                return;
              }
              showOrder(get(), product);
            } catch (Exception failure) {
              ZhiziApiException apiError = findApiError(failure);
              if (apiError != null && apiError.isUnauthorized()) {
                RemoteComputeConfig.invalidateZhiziToken();
              }
              productStatus.setForeground(ERROR);
              productStatus.setText(
                  text(
                      "RemoteCompute.payment.createFailed",
                      "The order result is uncertain. It was not retried. Check Zhizi before trying again."));
              createOrderButton.setEnabled(true);
              productBox.setEnabled(true);
            } finally {
              creatingOrder = false;
              createWorker = null;
            }
          }
        };
    createWorker.execute();
  }

  private void showOrder(
      ZhiziApiClient.PaymentOrder order, ZhiziApiClient.MembershipProduct selectedProduct)
      throws Exception {
    paymentProduct.setText(monthsText(selectedProduct.durationMonths));
    paymentAmount.setText(formatFen(order.amountFen));
    if (order.status == ZhiziApiClient.PaymentStatus.PENDING) {
      qrCode.setIcon(new ImageIcon(renderQr(order.codeUrl, 276)));
    } else {
      qrCode.setIcon(null);
    }
    currentOrder = order;
    cardLayout.show(cards, PAYMENT_CARD);
    if (order.status == ZhiziApiClient.PaymentStatus.SUCCESS) {
      finishSuccess();
    } else if (order.status == ZhiziApiClient.PaymentStatus.FAIL) {
      finishFailure();
    } else {
      paymentStatus.setForeground(MUTED);
      paymentStatus.setText(
          text("RemoteCompute.payment.waiting", "Waiting for payment confirmation..."));
      startPolling();
    }
  }

  private void startPolling() {
    stopPolling();
    consecutivePollFailures = 0;
    pollTimer = new Timer(2000, event -> pollOrder());
    pollTimer.setInitialDelay(2000);
    pollTimer.start();
  }

  private void pollOrder() {
    if (currentOrder == null || pollWorker != null || !isDisplayable()) {
      return;
    }
    String expectedOrderId = currentOrder.id;
    pollWorker =
        new SwingWorker<ZhiziApiClient.PaymentOrder, Void>() {
          @Override
          protected ZhiziApiClient.PaymentOrder doInBackground() throws Exception {
            return apiClient.fetchOrder(accountToken, expectedOrderId);
          }

          @Override
          protected void done() {
            try {
              if (isCancelled()
                  || currentOrder == null
                  || !expectedOrderId.equals(currentOrder.id)) {
                return;
              }
              ZhiziApiClient.PaymentOrder updated = get();
              if (updated.amountFen != currentOrder.amountFen
                  || !updated.productName.equals(currentOrder.productName)) {
                throw new ZhiziApiException(
                    200,
                    "invalid_response",
                    "",
                    0,
                    false,
                    ZhiziApiException.Operation.FETCH_ORDER);
              }
              currentOrder = updated;
              consecutivePollFailures = 0;
              if (pollTimer != null) {
                pollTimer.setDelay(2000);
              }
              if (currentOrder.status == ZhiziApiClient.PaymentStatus.SUCCESS) {
                finishSuccess();
              } else if (currentOrder.status == ZhiziApiClient.PaymentStatus.FAIL) {
                finishFailure();
              } else {
                paymentStatus.setForeground(MUTED);
                paymentStatus.setText(
                    text("RemoteCompute.payment.waiting", "Waiting for payment confirmation..."));
              }
            } catch (Exception failure) {
              consecutivePollFailures++;
              if (pollTimer != null) {
                pollTimer.setDelay(Math.min(10000, 2000 * (consecutivePollFailures + 1)));
              }
              paymentStatus.setForeground(ERROR);
              paymentStatus.setText(
                  text(
                      "RemoteCompute.payment.pollRetry",
                      "The network is unstable. Payment status will be checked again; no new order will be created."));
              ZhiziApiException apiError = findApiError(failure);
              if (apiError != null && apiError.isUnauthorized()) {
                RemoteComputeConfig.invalidateZhiziToken();
                stopPolling();
                reauthenticationRequired = true;
                retryButton.setText(
                    text("RemoteCompute.payment.closeAndLogin", "Close and sign in again"));
                retryButton.setVisible(true);
              }
            } finally {
              pollWorker = null;
            }
          }
        };
    pollWorker.execute();
  }

  private void finishSuccess() {
    stopPolling();
    accountService.clear();
    paymentStatus.setForeground(GREEN);
    paymentStatus.setText(
        text(
            "RemoteCompute.payment.success",
            "Payment confirmed by Zhizi. Account status is being refreshed."));
    retryButton.setVisible(false);
    paymentCompleted.run();
  }

  private void finishFailure() {
    stopPolling();
    paymentStatus.setForeground(ERROR);
    paymentStatus.setText(
        text(
            "RemoteCompute.payment.failed",
            "Zhizi reported that this order failed. You can create a new order."));
    retryButton.setText(text("RemoteCompute.payment.newOrder", "Create a new order"));
    retryButton.setVisible(true);
  }

  private void resetForNewOrder() {
    stopPolling();
    reauthenticationRequired = false;
    currentOrder = null;
    qrCode.setIcon(null);
    retryButton.setVisible(false);
    productStatus.setForeground(MUTED);
    productStatus.setText(
        text(
            "RemoteCompute.payment.livePrice",
            "The price is loaded from Zhizi immediately before purchase."));
    productBox.setEnabled(productBox.getItemCount() > 0);
    createOrderButton.setEnabled(productBox.getItemCount() > 0);
    cardLayout.show(cards, PRODUCT_CARD);
  }

  private void stopPolling() {
    if (pollTimer != null) {
      pollTimer.stop();
      pollTimer = null;
    }
    if (pollWorker != null) {
      pollWorker.cancel(true);
      pollWorker = null;
    }
  }

  @Override
  public void dispose() {
    stopPolling();
    if (productsWorker != null) {
      productsWorker.cancel(true);
      productsWorker = null;
    }
    if (createWorker != null) {
      createWorker.cancel(true);
      createWorker = null;
    }
    super.dispose();
  }

  static BufferedImage renderQr(String value, int size) throws Exception {
    BitMatrix matrix = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size);
    return MatrixToImageWriter.toBufferedImage(matrix);
  }

  static String formatFen(long amountFen) {
    BigDecimal yuan = BigDecimal.valueOf(Math.max(0L, amountFen), 2);
    return "¥" + yuan.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
  }

  private static String monthsText(int months) {
    return format("RemoteCompute.payment.months", "{0}-month VIP", months);
  }

  private static ZhiziApiException findApiError(Throwable failure) {
    Throwable current = failure;
    while (current != null) {
      if (current instanceof ZhiziApiException) {
        return (ZhiziApiException) current;
      }
      current = current.getCause();
    }
    return null;
  }

  private static JPanel cardPanel() {
    JPanel panel = new JPanel();
    panel.setBackground(CARD);
    panel.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER), new EmptyBorder(24, 26, 24, 26)));
    return panel;
  }

  private static GridBagConstraints constraints() {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    return gbc;
  }

  private static void stylePrimary(JButton button) {
    button.setPreferredSize(new Dimension(220, 44));
  }

  private static void styleSecondary(JButton button) {
    button.setPreferredSize(
        new Dimension(AccessibilitySupport.localizedControlWidth(button, 92), 42));
  }

  private static JButton primaryButton(String label) {
    return new RemoteComputeDialog.RoundedButton(
        label, GREEN, new Color(34, 121, 77), Color.WHITE, 18);
  }

  private static JButton secondaryButton(String label) {
    return new RemoteComputeDialog.RoundedButton(label, CARD, BORDER, TEXT, 16);
  }

  private static String text(String key, String fallback) {
    try {
      return Lizzie.resourceBundle == null ? fallback : Lizzie.resourceBundle.getString(key);
    } catch (MissingResourceException ignored) {
      return fallback;
    }
  }

  private static String format(String key, String fallback, Object... args) {
    return MessageFormat.format(text(key, fallback), args);
  }

  private static final class ProductItem {
    private final ZhiziApiClient.MembershipProduct product;

    private ProductItem(ZhiziApiClient.MembershipProduct product) {
      this.product = product;
    }

    @Override
    public String toString() {
      return monthsText(product.durationMonths) + " · " + formatFen(product.priceFen);
    }
  }

  private static final class ProductRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(
        JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      JLabel label =
          (JLabel)
              super.getListCellRendererComponent(
                  list, value, index, isSelected, cellHasFocus);
      label.setBorder(new EmptyBorder(7, 12, 7, 12));
      label.setFont(label.getFont().deriveFont(Font.BOLD, 14F));
      return label;
    }
  }
}
