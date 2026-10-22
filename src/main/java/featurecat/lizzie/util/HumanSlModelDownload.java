package featurecat.lizzie.util;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;

/**
 * Downloads the HumanSL model into a {@code .part} file and installs it once its size and SHA-256
 * match. A paused, cancelled or broken download keeps what it has and resumes from there with an
 * HTTP range request; only a file that fails verification is thrown away.
 */
final class HumanSlModelDownload {
  private HumanSlModelDownload() {}

  static Path download(
      KataGoAutoSetupHelper.ProgressListener listener,
      KataGoAutoSetupHelper.DownloadSession session,
      List<String> sources)
      throws IOException {
    KataGoAutoSetupHelper.SetupSnapshot snapshot = KataGoAutoSetupHelper.inspectLocalSetup();
    Path modelsDir = KataGoAutoSetupHelper.humanSlModelsDir(snapshot.workingDir);
    Files.createDirectories(modelsDir);
    KataGoAutoSetupHelper.DownloadSession activeSession =
        session != null ? session : new KataGoAutoSetupHelper.DownloadSession();
    activeSession.throwIfCancelled();

    String fileName = KataGoAutoSetupHelper.HUMAN_SL_MODEL_FILE_NAME;
    Path target = modelsDir.resolve(fileName);
    if (KataGoAutoSetupHelper.isValidHumanSlModelFile(target)) {
      KataGoAutoSetupHelper.rememberHumanSlModel(target);
      if (listener != null) {
        listener.onProgress(fileName, Files.size(target), Files.size(target));
      }
      return target;
    }
    Files.deleteIfExists(target);

    Path part = modelsDir.resolve(fileName + ".part");
    long expected = KataGoAutoSetupHelper.humanSlModelSizeBytes();
    if (Files.isRegularFile(part) && expected > 0L && Files.size(part) > expected) {
      Files.delete(part);
    }
    IOException failure = null;
    for (String source : sources) {
      activeSession.throwIfCancelled();
      try {
        fromSource(source, part, expected, listener, activeSession);
      } catch (KataGoAutoSetupHelper.DownloadCancelledException e) {
        throw e;
      } catch (IOException e) {
        // A broken connection keeps the bytes it delivered for the next source or attempt.
        failure = append(failure, e);
        continue;
      }
      try {
        KataGoAutoSetupHelper.verifyOfficialHumanSlModel(part);
      } catch (IOException e) {
        // All bytes arrived but they are not the model: start over from nothing.
        Files.deleteIfExists(part);
        failure = append(failure, e);
        continue;
      }
      activeSession.throwIfCancelled();
      try {
        Files.move(
            part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
      }
      KataGoAutoSetupHelper.rememberHumanSlModel(target);
      return target;
    }
    throw failure != null ? failure : new IOException("No HumanSL download source configured");
  }

  /** Fills {@code part} up to {@code expected} bytes, resuming from its current size. */
  private static void fromSource(
      String source,
      Path part,
      long expected,
      KataGoAutoSetupHelper.ProgressListener listener,
      KataGoAutoSetupHelper.DownloadSession session)
      throws IOException {
    long offset = Files.isRegularFile(part) ? Files.size(part) : 0L;
    if (expected > 0L && offset == expected) {
      return;
    }
    HttpURLConnection conn = null;
    try {
      conn = (HttpURLConnection) NetworkProxy.openConnection(URI.create(source).toURL());
      session.attach(conn);
      session.throwIfCancelled();
      conn.setInstanceFollowRedirects(true);
      conn.setRequestMethod("GET");
      conn.setConnectTimeout(15000);
      conn.setReadTimeout(30000);
      conn.setRequestProperty("User-Agent", KataGoAutoSetupHelper.USER_AGENT);
      conn.setRequestProperty("Accept", "application/octet-stream,*/*");
      conn.setRequestProperty("Accept-Encoding", "identity");
      if (offset > 0L) {
        conn.setRequestProperty("Range", "bytes=" + offset + "-");
      }
      int code = conn.getResponseCode();
      boolean resumed = offset > 0L && code == HttpURLConnection.HTTP_PARTIAL;
      if (resumed) {
        String range = conn.getHeaderField("Content-Range");
        if (range == null || !range.toLowerCase(Locale.ROOT).startsWith("bytes " + offset + "-")) {
          throw new IOException("HTTP resume range mismatch from " + source);
        }
      } else if (code != HttpURLConnection.HTTP_OK) {
        throw new IOException("HTTP " + code + " from " + source);
      }
      long length = conn.getContentLengthLong();
      long total = length < 0L ? expected : (resumed ? offset : 0L) + length;
      if (expected > 0L && total > 0L && total != expected) {
        throw new IOException(
            KataGoAutoSetupHelper.resource(
                "AutoSetup.humanSlModelIncomplete", "HumanSL model download is incomplete."));
      }
      if (total <= 0L) {
        total = expected;
      }
      long downloaded = resumed ? offset : 0L;
      StandardOpenOption[] options =
          resumed
              ? new StandardOpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.APPEND}
              : new StandardOpenOption[] {
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
              };
      try (InputStream raw = conn.getInputStream();
          BufferedInputStream input = new BufferedInputStream(raw);
          OutputStream output = Files.newOutputStream(part, options)) {
        byte[] buffer = new byte[64 * 1024];
        long lastReportTime = 0L;
        int read;
        while ((read = input.read(buffer)) >= 0) {
          session.throwIfCancelled();
          output.write(buffer, 0, read);
          downloaded += read;
          if (expected > 0L && downloaded > expected) {
            Files.deleteIfExists(part);
            throw new IOException(
                KataGoAutoSetupHelper.resource(
                    "AutoSetup.humanSlModelIncomplete", "HumanSL model download is incomplete."));
          }
          long now = System.currentTimeMillis();
          if (listener != null && (now - lastReportTime > 120 || downloaded == total)) {
            listener.onProgress(KataGoAutoSetupHelper.HUMAN_SL_MODEL_FILE_NAME, downloaded, total);
            lastReportTime = now;
          }
        }
      }
      session.throwIfCancelled();
      if (total > 0L && downloaded != total) {
        throw new IOException(
            KataGoAutoSetupHelper.resource(
                "AutoSetup.humanSlModelIncomplete", "HumanSL model download is incomplete."));
      }
    } catch (IOException e) {
      if (session.isCancelled()
          && !(e instanceof KataGoAutoSetupHelper.DownloadCancelledException)) {
        throw new KataGoAutoSetupHelper.DownloadCancelledException(
            KataGoAutoSetupHelper.resource("AutoSetup.downloadCancelled", "Download cancelled."));
      }
      throw e;
    } finally {
      if (conn != null) {
        conn.disconnect();
      }
      session.clear();
    }
  }

  private static IOException append(IOException previous, IOException next) {
    if (previous != null) {
      next.addSuppressed(previous);
    }
    return next;
  }
}
