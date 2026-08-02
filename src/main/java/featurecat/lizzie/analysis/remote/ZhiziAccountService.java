package featurecat.lizzie.analysis.remote;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;

/** Loads Zhizi account data without exposing tokens to the UI or diagnostics. */
public final class ZhiziAccountService {
  static final Duration CACHE_DURATION = Duration.ofSeconds(45);
  private static final int RECENT_USAGE_SIZE = 5;

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
}
