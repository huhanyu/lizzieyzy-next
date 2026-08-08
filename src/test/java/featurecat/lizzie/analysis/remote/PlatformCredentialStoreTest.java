package featurecat.lizzie.analysis.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlatformCredentialStoreTest {
  @Test
  void macKeychainReceivesSecretsOnlyThroughStandardInput() throws Exception {
    RecordingRunner runner = new RecordingRunner();
    runner.readOutput = "stored-secret\n";
    CredentialStore store = PlatformCredentialStore.create("Mac OS X", Path.of("unused"), runner);

    store.write(CredentialStore.Kind.PASSWORD, "user@example.com", "never-in-argv");
    assertEquals(
        "stored-secret",
        store.read(CredentialStore.Kind.PASSWORD, "user@example.com").orElseThrow());

    assertFalse(runner.flattenedCommands().contains("never-in-argv"));
    assertTrue(
        runner.inputs.contains(
            "never-in-argv" + System.lineSeparator() + "never-in-argv" + System.lineSeparator()));
    assertTrue(runner.flattenedCommands().contains("add-generic-password"));
    assertTrue(runner.flattenedCommands().contains("find-generic-password"));
  }

  @Test
  void aiCommentaryUsesAnIndependentMacKeychainService() throws Exception {
    RecordingRunner runner = new RecordingRunner();
    CredentialStore store = PlatformCredentialStore.create("Mac OS X", Path.of("unused"), runner);

    store.write(CredentialStore.Kind.API_KEY, "provider-account", "never-in-argv");

    String commands = runner.flattenedCommands();
    assertTrue(commands.contains("cn.lizzieyzy.next.ai-commentary.api-key"));
    assertFalse(commands.contains("cn.lizzieyzy.next.zhizi.api-key"));
    assertFalse(commands.contains("never-in-argv"));
  }

  @Test
  void linuxSecretServiceReceivesSecretsOnlyThroughStandardInput() throws Exception {
    RecordingRunner runner = new RecordingRunner();
    runner.readOutput = "linux-secret\n";
    CredentialStore store = PlatformCredentialStore.create("Linux", Path.of("unused"), runner);

    store.write(CredentialStore.Kind.ACCOUNT_TOKEN, "user@example.com", "never-in-argv");
    assertEquals(
        "linux-secret",
        store.read(CredentialStore.Kind.ACCOUNT_TOKEN, "user@example.com").orElseThrow());

    assertFalse(runner.flattenedCommands().contains("never-in-argv"));
    assertTrue(runner.inputs.contains("never-in-argv" + System.lineSeparator()));
    assertTrue(runner.flattenedCommands().contains("secret-tool store"));
    assertTrue(runner.flattenedCommands().contains("secret-tool lookup"));
  }

  @Test
  void windowsDpapiPersistsOnlyEncryptedUserScopedBlob() throws Exception {
    Path directory = Files.createTempDirectory("dpapi-store");
    RecordingRunner runner = new RecordingRunner();
    runner.protectedOutput = "encrypted-dpapi-blob";
    runner.unprotectedOutput = "windows-secret";
    CredentialStore store = PlatformCredentialStore.create("Windows 11", directory, runner);

    store.write(CredentialStore.Kind.ACCOUNT_TOKEN, "user@example.com", "never-in-file");

    List<Path> files;
    try (var paths = Files.list(directory)) {
      files = paths.toList();
    }
    assertEquals(1, files.size());
    assertEquals("encrypted-dpapi-blob", Files.readString(files.get(0)));
    assertFalse(files.get(0).getFileName().toString().contains("user@example.com"));
    assertEquals(
        "windows-secret",
        store.read(CredentialStore.Kind.ACCOUNT_TOKEN, "user@example.com").orElseThrow());
    assertFalse(runner.flattenedCommands().contains("never-in-file"));
    assertTrue(runner.inputs.contains("never-in-file"));
  }

  @Test
  void unsupportedPlatformUsesSessionOnlyBackend() {
    CredentialStore store =
        PlatformCredentialStore.create("Plan 9", Path.of("unused"), new RecordingRunner());

    assertEquals("session-only", store.backendName());
    assertFalse(store.isAvailable());
  }

  private static final class RecordingRunner
      implements PlatformCredentialStore.CredentialCommandRunner {
    final List<List<String>> commands = new ArrayList<>();
    final List<String> inputs = new ArrayList<>();
    String readOutput = "";
    String protectedOutput = "encrypted";
    String unprotectedOutput = "decrypted";

    @Override
    public PlatformCredentialStore.CommandResult run(
        List<String> command, String input, Duration timeout) throws IOException {
      commands.add(List.copyOf(command));
      inputs.add(input == null ? "" : input);
      String flattened = String.join(" ", command);
      if (flattened.contains("Unprotect(")) {
        return new PlatformCredentialStore.CommandResult(0, unprotectedOutput);
      }
      if (flattened.contains("Protect(")) {
        return new PlatformCredentialStore.CommandResult(0, protectedOutput);
      }
      if (flattened.contains("find-generic-password") || flattened.contains("secret-tool lookup")) {
        return new PlatformCredentialStore.CommandResult(0, readOutput);
      }
      return new PlatformCredentialStore.CommandResult(0, "");
    }

    String flattenedCommands() {
      StringBuilder out = new StringBuilder();
      for (List<String> command : commands) {
        if (out.length() > 0) {
          out.append('\n');
        }
        out.append(String.join(" ", command));
      }
      return out.toString();
    }
  }
}
