package featurecat.lizzie.logging;

public final class DiagnosticBundleLimits {
  private final long appWindowHours;
  private final long appCapBytes;
  private final long crashWindowHours;
  private final long crashCapBytes;
  private final long rawCapBytes;
  private final long captureCapBytes;

  public DiagnosticBundleLimits(
      long appWindowHours,
      long appCapBytes,
      long crashWindowHours,
      long crashCapBytes,
      long rawCapBytes,
      long captureCapBytes) {
    this.appWindowHours = appWindowHours;
    this.appCapBytes = appCapBytes;
    this.crashWindowHours = crashWindowHours;
    this.crashCapBytes = crashCapBytes;
    this.rawCapBytes = rawCapBytes;
    this.captureCapBytes = captureCapBytes;
  }

  public static DiagnosticBundleLimits production() {
    return new DiagnosticBundleLimits(
        24, 50L * 1024 * 1024, 24, 10L * 1024 * 1024, 50L * 1024 * 1024, 50L * 1024 * 1024);
  }

  public long appWindowHours() {
    return appWindowHours;
  }

  public long appCapBytes() {
    return appCapBytes;
  }

  public long crashWindowHours() {
    return crashWindowHours;
  }

  public long crashCapBytes() {
    return crashCapBytes;
  }

  public long rawCapBytes() {
    return rawCapBytes;
  }

  public long captureCapBytes() {
    return captureCapBytes;
  }
}
