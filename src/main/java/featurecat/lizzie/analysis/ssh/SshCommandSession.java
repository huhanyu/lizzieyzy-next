package featurecat.lizzie.analysis.ssh;

import featurecat.lizzie.logging.LogCategories;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.sshd.client.ClientBuilder;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.config.hosts.HostConfigEntryResolver;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.kex.BuiltinDHFactories;
import org.apache.sshd.common.kex.KeyExchangeFactory;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.apache.sshd.common.signature.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One remote command started over SSH, exposing its stdin, stdout and stderr like a local {@link
 * Process}. Every connection is checked against {@link SshKnownHosts} before credentials are sent.
 */
public final class SshCommandSession implements AutoCloseable {
  static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  static final Duration AUTH_TIMEOUT = Duration.ofSeconds(20);
  static final Duration CHANNEL_OPEN_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(3);

  private static final Logger LOG = LoggerFactory.getLogger(LogCategories.REMOTE);

  private final SshClient client;
  private final ClientSession session;
  private final ChannelExec channel;
  private final Object closeLock = new Object();
  private boolean closed;

  private SshCommandSession(SshClient client, ClientSession session, ChannelExec channel) {
    this.client = client;
    this.session = session;
    this.channel = channel;
  }

  /** Connects with the work directory's known_hosts file and starts {@code command}. */
  public static SshCommandSession open(SshTarget target, SshCredential credential, String command)
      throws IOException {
    return open(target, credential, command, SshKnownHosts.forWorkDirectory());
  }

  public static SshCommandSession open(
      SshTarget target, SshCredential credential, String command, SshKnownHosts knownHosts)
      throws IOException {
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(credential, "credential");
    Objects.requireNonNull(command, "command");
    Objects.requireNonNull(knownHosts, "knownHosts");
    SshKnownHosts.Check hostKeyCheck = knownHosts.newCheck(target);
    SshClient client = SshClient.setUpDefaultClient();
    // Only the credential configured for this engine may be offered: never the user's own
    // ~/.ssh/id_* keys, and never host aliases, users or proxies from ~/.ssh/config.
    client.setKeyIdentityProvider(KeyIdentityProvider.EMPTY_KEYS_PROVIDER);
    client.setHostConfigEntryResolver(HostConfigEntryResolver.EMPTY);
    client.setKeyExchangeFactories(
        withLegacyKeyExchangeFallbacks(client.getKeyExchangeFactories()));
    client.setSignatureFactories(
        preferRecordedKeyTypes(
            client.getSignatureFactories(), knownHosts.recordedKeyTypes(target)));
    client.setServerKeyVerifier(hostKeyCheck.verifier());
    ClientSession session = null;
    ChannelExec channel = null;
    boolean started = false;
    try {
      client.start();
      session =
          client
              .connect(target.username(), target.host(), target.port())
              .verify(CONNECT_TIMEOUT)
              .getClientSession();
      credential.addTo(session);
      authenticate(session, hostKeyCheck);
      channel = session.createExecChannel(command);
      channel.open().verify(CHANNEL_OPEN_TIMEOUT);
      started = true;
      return new SshCommandSession(client, session, channel);
    } catch (IOException | RuntimeException e) {
      if (hostKeyCheck.rejected()) {
        throw hostKeyCheck.rejection(e);
      }
      throw e;
    } finally {
      if (!started) {
        closeQuietly(channel, session, client);
      }
    }
  }

  private static void authenticate(ClientSession session, SshKnownHosts.Check hostKeyCheck)
      throws IOException {
    AuthFuture auth = session.auth();
    if (!auth.await(AUTH_TIMEOUT)) {
      throw new IOException("SSH authentication timed out after " + AUTH_TIMEOUT.toSeconds() + "s");
    }
    if (hostKeyCheck.rejected()) {
      throw hostKeyCheck.rejection(auth.getException());
    }
    if (auth.isSuccess()) {
      return;
    }
    Throwable failure = auth.getException();
    if (session.isAuthenticated()) {
      return;
    }
    if (failure == null || session.isOpen()) {
      throw new SshAuthenticationException(
          "SSH server rejected the credentials for " + session.getUsername(), failure);
    }
    throw failure instanceof IOException
        ? (IOException) failure
        : new IOException("SSH connection failed: " + failure.getMessage(), failure);
  }

  /**
   * MINA's defaults are modern-only. The previous client (ganymed-ssh2) could only use SHA-1 key
   * exchange, so servers configured for it keep working through these lowest-priority fallbacks.
   */
  static List<KeyExchangeFactory> withLegacyKeyExchangeFallbacks(
      List<KeyExchangeFactory> defaults) {
    List<KeyExchangeFactory> factories = new ArrayList<>(defaults);
    for (BuiltinDHFactories legacy : List.of(BuiltinDHFactories.dhg14, BuiltinDHFactories.dhgex)) {
      boolean present =
          factories.stream().anyMatch(factory -> factory.getName().equals(legacy.getName()));
      if (!present && legacy.isSupported()) {
        factories.add(ClientBuilder.DH2KEX.apply(legacy));
      }
    }
    return factories;
  }

  /** Asks the server for a recorded key type first so a known host is not falsely refused. */
  static List<NamedFactory<Signature>> preferRecordedKeyTypes(
      List<NamedFactory<Signature>> defaults, List<String> recordedKeyTypes) {
    if (recordedKeyTypes.isEmpty()) {
      return defaults;
    }
    List<NamedFactory<Signature>> preferred = new ArrayList<>();
    for (String keyType : recordedKeyTypes) {
      Set<String> equivalents = new HashSet<>(KeyUtils.getAllEquivalentKeyTypes(keyType));
      equivalents.add(keyType);
      // Keep the default order inside a key type, so rsa-sha2-512 still beats ssh-rsa.
      for (NamedFactory<Signature> factory : defaults) {
        if (equivalents.contains(factory.getName()) && !preferred.contains(factory)) {
          preferred.add(factory);
        }
      }
    }
    for (NamedFactory<Signature> factory : defaults) {
      if (!preferred.contains(factory)) {
        preferred.add(factory);
      }
    }
    return preferred;
  }

  public InputStream stdout() {
    return channel.getInvertedOut();
  }

  public InputStream stderr() {
    return channel.getInvertedErr();
  }

  public OutputStream stdin() {
    return channel.getInvertedIn();
  }

  /** Waits until the remote command exits or closes its channel. */
  public boolean awaitCompletion(Duration timeout) {
    Set<ClientChannelEvent> events =
        channel.waitFor(
            EnumSet.of(ClientChannelEvent.CLOSED, ClientChannelEvent.EXIT_STATUS),
            timeout.toMillis());
    return !events.contains(ClientChannelEvent.TIMEOUT);
  }

  public boolean isOpen() {
    return channel.isOpen() && session.isOpen();
  }

  @Override
  public void close() {
    synchronized (closeLock) {
      if (closed) {
        return;
      }
      closed = true;
    }
    closeQuietly(channel, session, client);
  }

  private static void closeQuietly(ChannelExec channel, ClientSession session, SshClient client) {
    try {
      if (channel != null) {
        channel.close(false).await(CLOSE_TIMEOUT);
      }
    } catch (IOException | RuntimeException e) {
      LOG.debug("Unable to close SSH channel cleanly", e);
    }
    try {
      if (session != null) {
        session.close(false).await(CLOSE_TIMEOUT);
      }
    } catch (IOException | RuntimeException e) {
      LOG.debug("Unable to close SSH session cleanly", e);
    }
    try {
      client.stop();
    } catch (RuntimeException e) {
      LOG.debug("Unable to stop SSH client cleanly", e);
    }
  }
}
