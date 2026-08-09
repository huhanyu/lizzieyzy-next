package featurecat.lizzie.analysis;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Cross-platform fake GTP process used by engine lifecycle integration tests. */
public final class UpdateEngineGtpFixture {
  private UpdateEngineGtpFixture() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 3) {
      throw new IllegalArgumentException("expected log, startup gate, and loadsgf failure paths");
    }
    Path log = Path.of(args[0]);
    Path startupGate = Path.of(args[1]);
    Path loadSgfFailure = Path.of(args[2]);
    try (BufferedReader input =
            new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        BufferedWriter output =
            new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8))) {
      String line;
      while ((line = input.readLine()) != null) {
        append(log, line);
        ParsedCommand parsed = ParsedCommand.parse(line);
        if (parsed.command.startsWith("loadsgf ")) {
          Path sgf = Path.of(parsed.command.substring("loadsgf ".length()));
          append(log, "SGF:" + Files.readString(sgf));
          if (Files.isRegularFile(loadSgfFailure)) {
            writeResponse(output, parsed.id, false, "controlled restore failure");
            continue;
          }
        }
        if ("name".equals(parsed.command)) {
          while (!Files.isRegularFile(startupGate)) {
            Thread.sleep(10L);
          }
        }
        String body =
            parsed.id.isEmpty()
                ? switch (parsed.command) {
                  case "name" -> "KataGo";
                  case "version" -> "1.15";
                  case "list_commands" -> "protocol_version";
                  default -> "";
                }
                : "";
        writeResponse(output, parsed.id, true, body);
        if ("quit".equals(parsed.command)) {
          return;
        }
      }
    }
  }

  private static void append(Path log, String line) throws Exception {
    Files.writeString(
        log,
        line + System.lineSeparator(),
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND);
  }

  private static void writeResponse(
      BufferedWriter output, String id, boolean success, String body) throws Exception {
    output.write(success ? '=' : '?');
    output.write(id);
    if (!body.isEmpty()) {
      output.write(' ');
      output.write(body);
    }
    output.newLine();
    output.newLine();
    output.flush();
  }

  private static final class ParsedCommand {
    private final String id;
    private final String command;

    private ParsedCommand(String id, String command) {
      this.id = id;
      this.command = command;
    }

    private static ParsedCommand parse(String line) {
      int separator = line.indexOf(' ');
      if (separator > 0 && line.substring(0, separator).chars().allMatch(Character::isDigit)) {
        return new ParsedCommand(line.substring(0, separator), line.substring(separator + 1));
      }
      return new ParsedCommand("", line);
    }
  }
}
