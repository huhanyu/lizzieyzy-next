package featurecat.lizzie.util;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;

/** Whether a downloaded KataGo weight file is whole when nothing else vouches for it. */
final class KataGoWeightFile {
  private KataGoWeightFile() {}

  /**
   * For a weight listed without size or digest (the rows read from the networks page) only its own
   * format can tell. KataGo weights are gzip files, whose trailer carries a CRC and the length, so
   * a cut-off file fails to read to the end. A size above 1 MiB used to be taken as whole, and a
   * stopped download was then installed as it was; other names keep that rule.
   *
   * @param info the listing, for the file's name; the path may be the download's ".part" file
   */
  static boolean isWhole(Path path, KataGoAutoSetupHelper.RemoteWeightInfo info)
      throws IOException {
    String name = info == null ? path.getFileName().toString() : info.fileName();
    if (!name.toLowerCase(Locale.ROOT).endsWith(".gz")) {
      return Files.size(path) > 1024L * 1024L;
    }
    try (InputStream input = new GZIPInputStream(Files.newInputStream(path), 64 * 1024)) {
      byte[] buffer = new byte[64 * 1024];
      while (input.read(buffer) >= 0) {}
      return true;
    } catch (ZipException | EOFException e) {
      return false;
    }
  }
}
