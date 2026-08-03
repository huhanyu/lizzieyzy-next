package featurecat.lizzie.analysis.remote;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;

/** Loads Zhizi account data without exposing tokens to the UI or diagnostics. */
public final class ZhiziAccountService {
  static final Duration CACHE_DURATION = Duration.ofSeconds(45);
  private static final int RECENT_USAGE_SIZE = 5;
  private static final int RECENT_CREDIT_SIZE = 20;

  private final ZhiziApiClient apiClient;
  private Overview cachedOverview;
  private String cachedTokenFingerprint = "";

  public ZhiziAccountService(ZhiziApiClient apiClient) {
    this.apiClient = apiClient;
  }

  public synchronized Overview fetchOverview(String accountToken, boolean forceRefresh)
      throws IOException, InterruptedException {
    String fingerprint = tokenFingerprint(accountToken);
    Instant now = Instant.now();
    if (!forceRefresh
        && cachedOverview != null
        && cachedTokenFingerprint.equals(fingerprint)
        && now.isBefore(cachedOverview.loadedAt.plus(CACHE_DURATION))) {
      return cachedOverview;
    }

    ZhiziApiClient.AccountProfile account = apiClient.fetchAccount(accountToken);
    ZhiziApiClient.BalanceInfo balance = apiClient.fetchBalance(accountToken);
    ZhiziApiClient.UsagePage recentUsage =
        apiClient.fetchUsages(accountToken, 0, RECENT_USAGE_SIZE, null);
    Overview loaded = new Overview(account, balance, recentUsage, now);
    cachedOverview = loaded;
    cachedTokenFingerprint = fingerprint;
    return loaded;
  }

  public ZhiziApiClient.UsagePage fetchUsages(String accountToken, int page, int pageSize)
      throws IOException, InterruptedException {
    return apiClient.fetchUsages(accountToken, page, pageSize, null);
  }

  public ZhiziApiClient.CreditPage fetchCredits(String accountToken, int page, int pageSize)
      throws IOException, InterruptedException {
    return apiClient.fetchCredits(accountToken, page, pageSize, "");
  }

  public synchronized void clear() {
    cachedOverview = null;
    cachedTokenFingerprint = "";
  }

  public PaymentBaseline capturePaymentBaseline(
      String accountToken, ZhiziApiClient.PaymentPurpose purpose)
      throws IOException, InterruptedException {
    Instant observedAt = Instant.now();
    if (purpose == ZhiziApiClient.PaymentPurpose.BALANCE_TOP_UP) {
      ZhiziApiClient.BalanceInfo balance = apiClient.fetchBalance(accountToken);
      return PaymentBaseline.forTopUp(balance, observedAt);
    }
    ZhiziApiClient.AccountProfile account = apiClient.fetchAccount(accountToken);
    return PaymentBaseline.forMembership(account, observedAt);
  }

  public PaymentVerification verifyPayment(
      String accountToken, PaymentBaseline baseline, ZhiziApiClient.PaymentOrder order)
      throws IOException, InterruptedException {
    if (baseline == null || order == null || baseline.purpose != order.purpose) {
      return PaymentVerification.notSettled();
    }
    if (order.purpose == ZhiziApiClient.PaymentPurpose.BALANCE_TOP_UP) {
      ZhiziApiClient.BalanceInfo balance = apiClient.fetchBalance(accountToken);
      ZhiziApiClient.CreditPage credits =
          apiClient.fetchCredits(accountToken, 0, RECENT_CREDIT_SIZE, "CASH");
      BigDecimal amountYuan = BigDecimal.valueOf(order.amountFen, 2);
      boolean cashTotalIncreased =
          balance.totalCashAmountYuan.compareTo(baseline.totalCashAmountYuan.add(amountYuan)) >= 0;
      boolean remainingBalanceChanged =
          balance.remainingBalanceYuan.compareTo(baseline.remainingBalanceYuan) > 0;
      Instant earliestCredit = baseline.observedAt.minusSeconds(5);
      boolean cashCreditFound =
          credits.items.stream()
              .anyMatch(
                  credit ->
                      "CASH".equals(credit.creditType)
                          && "PAYMENT".equals(credit.source)
                          && amountYuan.compareTo(credit.amountYuan) == 0
                          && credit.createdAt != null
                          && !credit.createdAt.isBefore(earliestCredit));
      return new PaymentVerification(
          cashTotalIncreased && remainingBalanceChanged && cashCreditFound,
          remainingBalanceChanged,
          cashCreditFound,
          false);
    }

    ZhiziApiClient.AccountProfile account = apiClient.fetchAccount(accountToken);
    boolean membershipAdvanced =
        account.membership
            && account.membershipExpiresAt != null
            && (!baseline.membershipActive
                || baseline.membershipExpiresAt == null
                || account.membershipExpiresAt.isAfter(baseline.membershipExpiresAt));
    return new PaymentVerification(membershipAdvanced, false, false, membershipAdvanced);
  }

  private static String tokenFingerprint(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest((token == null ? "" : token).getBytes(StandardCharsets.UTF_8));
      StringBuilder value = new StringBuilder(16);
      for (int i = 0; i < 8; i++) {
        value.append(String.format("%02x", digest[i]));
      }
      return value.toString();
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  public static final class Overview {
    public final ZhiziApiClient.AccountProfile account;
    public final ZhiziApiClient.BalanceInfo balance;
    public final ZhiziApiClient.UsagePage recentUsage;
    public final Instant loadedAt;

    private Overview(
        ZhiziApiClient.AccountProfile account,
        ZhiziApiClient.BalanceInfo balance,
        ZhiziApiClient.UsagePage recentUsage,
        Instant loadedAt) {
      this.account = account;
      this.balance = balance;
      this.recentUsage = recentUsage;
      this.loadedAt = loadedAt;
    }
  }

  public static final class PaymentBaseline {
    public final ZhiziApiClient.PaymentPurpose purpose;
    public final BigDecimal remainingBalanceYuan;
    public final BigDecimal totalCashAmountYuan;
    public final boolean membershipActive;
    public final Instant membershipExpiresAt;
    public final Instant observedAt;

    private PaymentBaseline(
        ZhiziApiClient.PaymentPurpose purpose,
        BigDecimal remainingBalanceYuan,
        BigDecimal totalCashAmountYuan,
        boolean membershipActive,
        Instant membershipExpiresAt,
        Instant observedAt) {
      this.purpose = purpose;
      this.remainingBalanceYuan = remainingBalanceYuan;
      this.totalCashAmountYuan = totalCashAmountYuan;
      this.membershipActive = membershipActive;
      this.membershipExpiresAt = membershipExpiresAt;
      this.observedAt = observedAt;
    }

    private static PaymentBaseline forTopUp(
        ZhiziApiClient.BalanceInfo balance, Instant observedAt) {
      return new PaymentBaseline(
          ZhiziApiClient.PaymentPurpose.BALANCE_TOP_UP,
          balance.remainingBalanceYuan,
          balance.totalCashAmountYuan,
          false,
          null,
          observedAt);
    }

    private static PaymentBaseline forMembership(
        ZhiziApiClient.AccountProfile account, Instant observedAt) {
      return new PaymentBaseline(
          ZhiziApiClient.PaymentPurpose.VIP_MEMBERSHIP,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          account.membership,
          account.membershipExpiresAt,
          observedAt);
    }
  }

  public static final class PaymentVerification {
    public final boolean settled;
    public final boolean remainingBalanceChanged;
    public final boolean cashCreditFound;
    public final boolean membershipAdvanced;

    private PaymentVerification(
        boolean settled,
        boolean remainingBalanceChanged,
        boolean cashCreditFound,
        boolean membershipAdvanced) {
      this.settled = settled;
      this.remainingBalanceChanged = remainingBalanceChanged;
      this.cashCreditFound = cashCreditFound;
      this.membershipAdvanced = membershipAdvanced;
    }

    private static PaymentVerification notSettled() {
      return new PaymentVerification(false, false, false, false);
    }
  }
}
