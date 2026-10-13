package featurecat.lizzie.update;

final class UpdateDownloadText {
  private UpdateDownloadText() {}

  static String status(UpdateCheckCoordinator.State state) {
    String message = switch (state.download().phase()) {
      case NONE -> "";
      case CANCELLED -> UpdateText.tr("WindowsUpdate.status.cancelled", "下载已停止，进度已保留。",
          "Download stopped. Progress was kept so it can resume later.");
      case FAILED -> UpdateText.tr("WindowsUpdate.status.failed", "更新失败", "Update failed");
      case READY -> UpdateText.tr("WindowsUpdate.ready", "更新已下载并校验，等待您的操作。",
          "Update downloaded and verified. Waiting for your action.");
      case DOWNLOADING -> progress(state.download().progress());
    };
    return state.download().error() == null ? message : message + "\n" + state.download().error();
  }

  private static String progress(ResumableDownloader.Progress progress) {
    if (progress == null) return UpdateText.tr("WindowsUpdate.status.preparing", "准备下载...", "Preparing download...");
    String status = switch (progress.state) {
      case VERIFYING -> UpdateText.tr("WindowsUpdate.status.verifying", "下载完成，正在校验文件...", "Download complete. Verifying file...");
      case PAUSED -> UpdateText.tr("WindowsUpdate.status.paused", "下载已暂停，进度已保留。", "Download paused. Progress is preserved.");
      default -> UpdateText.tr("WindowsUpdate.downloading", "正在后台下载更新", "Downloading update in the background");
    };
    return status + "\n" + WindowsUpdateDialog.formatBytes(progress.completedBytes) + " / "
        + WindowsUpdateDialog.formatBytes(progress.totalBytes);
  }

  static String action(UpdateCheckResult result) {
    if (result.windowsPlan != null) return UpdateText.tr("WindowsUpdate.installRestart", "安装并重启", "Install and restart");
    return "macos".equals(result.packagePlan.platform)
        ? UpdateText.tr("WindowsUpdate.openDmg", "打开 DMG", "Open DMG")
        : UpdateText.tr("WindowsUpdate.openFolder", "打开下载目录", "Open download folder");
  }
}
