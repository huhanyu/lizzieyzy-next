package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.analysis.ssh.SshKnownHosts;
import featurecat.lizzie.analysis.ssh.SshTarget;
import featurecat.lizzie.analysis.ssh.TestSshServer;
import featurecat.lizzie.util.Utils;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(60)
class SshEngineControllerTest {
  @TempDir Path temp;

  @Test
  void keyFileLoginUsesTheConfiguredPortInsteadOfAlways22() throws Exception {
    KeyPair clientKey = TestSshServer.ecKeyPair();
    Path keyFile = temp.resolve("id_ecdsa");
    try (OutputStream out = Files.newOutputStream(keyFile)) {
      OpenSSHKeyPairResourceWriter.INSTANCE.writePrivateKey(clientKey, "test", null, out);
    }
    try (TestSshServer server =
        TestSshServer.builder().acceptedClientKey(clientKey.getPublic()).start()) {
      assertNotEquals(SshTarget.DEFAULT_PORT, server.port());
      RecordingController controller = new RecordingController(server.port());

      assertTrue(controller.loginByFileKey("katago gtp", "player", keyFile.toFile()));
      assertEquals("= test-engine", gtp(controller, "name"));
      assertNull(controller.failure);
      controller.close();
      controller.close();
    }
  }

  @Test
  void savedPasswordIsDecryptedBeforeLogin() throws Exception {
    try (TestSshServer server = TestSshServer.builder().password("s3cret").start()) {
      RecordingController controller = new RecordingController(server.port());
      assertTrue(controller.login("katago gtp", "player", Utils.doEncrypt("s3cret")));
      assertEquals("= test-engine", gtp(controller, "name"));
      controller.close();
    }
  }

  @Test
  void rejectedLoginAndChangedHostKeyAreReportedSeparately() throws Exception {
    try (TestSshServer server = TestSshServer.builder().password("s3cret").start()) {
      RecordingController rejected = new RecordingController(server.port());
      assertFalse(rejected.login("katago gtp", "player", Utils.doEncrypt("wrong")));
      assertTrue(rejected.authenticationRejected);
      assertNull(rejected.failure);

      Files.writeString(
          temp.resolve("known_hosts"),
          "[127.0.0.1]:"
              + server.port()
              + " "
              + PublicKeyEntry.toString(TestSshServer.ecKeyPair().getPublic())
              + "\n");
      int passwordsBeforeInterception = server.passwordAttempts.get();
      RecordingController intercepted = new RecordingController(server.port());
      assertFalse(intercepted.login("katago gtp", "player", Utils.doEncrypt("s3cret")));
      assertFalse(intercepted.authenticationRejected);
      assertTrue(intercepted.failure.contains("[127.0.0.1]:" + server.port()), intercepted.failure);
      assertEquals(
          passwordsBeforeInterception,
          server.passwordAttempts.get(),
          "no password is sent to a server whose host key changed");
    }
  }

  @Test
  void closeBeforeLoginIsHarmless() {
    new RecordingController(2222).close();
  }

  private static String gtp(SshEngineController controller, String command) throws IOException {
    OutputStream stdin = controller.getStdin();
    stdin.write((command + "\n").getBytes(StandardCharsets.UTF_8));
    stdin.flush();
    InputStream stdout = controller.getStdout();
    StringBuilder line = new StringBuilder();
    int next;
    while ((next = stdout.read()) != -1 && next != '\n') {
      line.append((char) next);
    }
    return line.toString();
  }

  private final class RecordingController extends SshEngineController {
    private boolean authenticationRejected;
    private String failure;

    private RecordingController(int port) {
      super("127.0.0.1", String.valueOf(port));
    }

    @Override
    SshKnownHosts knownHosts() {
      return new SshKnownHosts(temp.resolve("known_hosts"));
    }

    @Override
    protected void onAuthenticationRejected() {
      authenticationRejected = true;
    }

    @Override
    protected void onConnectionFailed(String message) {
      failure = message;
    }
  }
}
