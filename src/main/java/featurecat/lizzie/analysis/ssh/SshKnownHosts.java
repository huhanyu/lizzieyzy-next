package featurecat.lizzie.analysis.ssh;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.logging.LogCategories;
import featurecat.lizzie.logging.WorkDirectoryResolver;
import java.io.File;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.sshd.client.config.hosts.KnownHostEntry;
import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Host-key policy for every SSH connection the app opens, backed by an OpenSSH-format known_hosts
 * file in the work directory.
 *
 * <p>The first key a server presents is recorded (the same as OpenSSH's {@code
 * StrictHostKeyChecking=accept-new}). Later connections are refused before any credential is sent
 * if the server presents a different key, or a key of a type that was never recorded for it.
 */
public final class SshKnownHosts {
  static final String DIRECTORY_NAME = "ssh";
  static final String FILE_NAME = "known_hosts";

  private static final Logger LOG = LoggerFactory.getLogger(LogCategories.REMOTE);
  private static final Object FILE_UPDATE_LOCK = new Object();

  private final Path file;

  public SshKnownHosts(Path file) {
    this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
  }

  /** The known_hosts file under the current work directory. */
  public static SshKnownHosts forWorkDirectory() {
    return new SshKnownHosts(defaultFile());
  }

  static Path defaultFile() {
    File configured = Lizzie.config == null ? null : Lizzie.config.getWorkDirectory();
    Path workDirectory =
        configured != null ? configured.toPath() : WorkDirectoryResolver.resolve().directory();
    return workDirectory.resolve(DIRECTORY_NAME).resolve(FILE_NAME);
  }

  public Path file() {
    return file;
  }

  /** Key types already recorded for the target, so the client can ask the server for them first. */
  List<String> recordedKeyTypes(SshTarget target) throws IOException {
    Set<String> keyTypes = new LinkedHashSet<>();
    for (KnownHostEntry entry : entriesFor(target)) {
      AuthorizedKeyEntry key = entry.getKeyEntry();
      if (key != null && key.getKeyType() != null) {
        keyTypes.add(key.getKeyType());
      }
    }
    return new ArrayList<>(keyTypes);
  }

  Check newCheck(SshTarget target) throws IOException {
    Path parent = file.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    return new Check(target);
  }

  private List<KnownHostEntry> entriesFor(SshTarget target) throws IOException {
    if (!Files.isRegularFile(file)) {
      return List.of();
    }
    List<KnownHostEntry> matches = new ArrayList<>();
    for (KnownHostEntry entry : KnownHostEntry.readKnownHostEntries(file)) {
      if (entry != null && entry.isHostMatch(target.host(), target.port())) {
        matches.add(entry);
      }
    }
    return matches;
  }

  static String fingerprint(PublicKey key) {
    return key == null ? "?" : KeyUtils.getFingerPrint(key);
  }

  /** Verifies one connection attempt and remembers why it refused a key, if it did. */
  final class Check {
    private final SshTarget target;
    private volatile String expectedFingerprint;
    private volatile String presentedFingerprint;

    private Check(SshTarget target) {
      this.target = target;
    }

    ServerKeyVerifier verifier() {
      KnownHostsServerKeyVerifier verifier =
          new KnownHostsServerKeyVerifier(this::acceptUnknownKey, file) {
            @Override
            protected KnownHostEntry updateKnownHostsFile(
                ClientSession clientSession,
                SocketAddress remoteAddress,
                PublicKey serverKey,
                Path knownHosts,
                Collection<HostEntryPair> knownHostsEntries)
                throws Exception {
              synchronized (FILE_UPDATE_LOCK) {
                return super.updateKnownHostsFile(
                    clientSession, remoteAddress, serverKey, knownHosts, knownHostsEntries);
              }
            }
          };
      verifier.setModifiedServerKeyAcceptor(this::rejectModifiedKey);
      return verifier;
    }

    boolean rejected() {
      return presentedFingerprint != null;
    }

    SshHostKeyMismatchException rejection(Throwable cause) {
      return new SshHostKeyMismatchException(
          target.knownHostsName(), expectedFingerprint, presentedFingerprint, file, cause);
    }

    private boolean acceptUnknownKey(
        ClientSession session, SocketAddress remoteAddress, PublicKey serverKey) {
      List<KnownHostEntry> recorded;
      try {
        recorded = entriesFor(target);
      } catch (IOException e) {
        LOG.warn("Unable to read SSH known hosts {}", file, e);
        return reject("?", serverKey);
      }
      if (!recorded.isEmpty()) {
        // The host is known, but only with other key types: accepting a new type here would let
        // an interceptor bypass the recorded key simply by offering a different algorithm.
        return reject(describeRecorded(session, recorded), serverKey);
      }
      LOG.info(
          "Recorded SSH host key for {} on first connection: {} {}",
          target.knownHostsName(),
          KeyUtils.getKeyType(serverKey),
          fingerprint(serverKey));
      return true;
    }

    private boolean rejectModifiedKey(
        ClientSession session,
        SocketAddress remoteAddress,
        KnownHostEntry entry,
        PublicKey expected,
        PublicKey actual) {
      return reject(fingerprint(expected), actual);
    }

    private boolean reject(String expected, PublicKey presented) {
      expectedFingerprint = expected;
      presentedFingerprint = fingerprint(presented);
      LOG.warn(
          "Refused SSH host key for {}: recorded {}, presented {} {} (known hosts: {})",
          target.knownHostsName(),
          expected,
          KeyUtils.getKeyType(presented),
          presentedFingerprint,
          file);
      return false;
    }

    private String describeRecorded(ClientSession session, List<KnownHostEntry> recorded) {
      List<String> fingerprints = new ArrayList<>();
      for (KnownHostEntry entry : recorded) {
        try {
          PublicKey key =
              entry.getKeyEntry().resolvePublicKey(session, PublicKeyEntryResolver.IGNORING);
          fingerprints.add(
              key == null
                  ? entry.getKeyEntry().getKeyType()
                  : KeyUtils.getKeyType(key) + " " + fingerprint(key));
        } catch (IOException | java.security.GeneralSecurityException | RuntimeException e) {
          fingerprints.add(entry.getKeyEntry().getKeyType());
        }
      }
      return String.join(", ", fingerprints);
    }
  }
}
