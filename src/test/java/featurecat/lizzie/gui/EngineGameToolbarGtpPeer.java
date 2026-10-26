package featurecat.lizzie.gui;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * KataGo-shaped GTP participant for {@link EngineGameToolbarIsolationAcceptanceIT}. It answers the
 * startup, rules, parameter and analysis commands an analysis-mode engine game uses, plays legal
 * moves, and lets the probe stabilise lifecycle states by holding one classified response until a
 * release file appears. It makes no claim about real KataGo search or parameter effects.
 *
 * <p>Arguments: {@code <root> <gtpName> <initialWrn> <initialPda> [flag...]}. Flags: {@code
 * game-values=a,b,...} (values only an engine game writes; setting breadth or aggressiveness to any
 * other value after one of them is a batch restore), {@code reject-set=v} (answer {@code ?} to
 * setting either parameter to {@code v}) and {@code reject-param-restore}. A hold is requested by
 * creating {@code <root>/hold-<label>}; the peer consumes it on the next matching command, writes
 * {@code <root>/held-<label>.txt}, and answers once {@code <root>/release-<label>} exists, at most
 * 7 s later because engine-game confirmations time out after 8 s. Labels: {@code get-param}, {@code
 * rules-restore}, {@code param-restore}.
 */
public final class EngineGameToolbarGtpPeer {
  static final String WRN = "analysisWideRootNoise";
  static final String PDA = "playoutDoublingAdvantage";
  static final String CHINESE_RULES =
      "{\"friendlyPassOk\":true,\"hasButton\":false,\"ko\":\"SIMPLE\",\"scoring\":\"AREA\","
          + "\"suicide\":false,\"tax\":\"NONE\",\"whiteHandicapBonus\":\"N\"}";

  /** The process's own rules differ from the match rules, so every retirement restores them. */
  static final String JAPANESE_RULES =
      "{\"friendlyPassOk\":false,\"hasButton\":false,\"ko\":\"SIMPLE\",\"scoring\":\"TERRITORY\","
          + "\"suicide\":false,\"tax\":\"SEKI\",\"whiteHandicapBonus\":\"0\"}";

  private static final Pattern COMMAND = Pattern.compile("^(\\d+)?\\s*(\\S+)\\s*(.*)$");
  private static final String COLUMNS = "ABCDEFGHJKLMNOPQRSTUVWXYZ";
  private static final long HOLD_LIMIT_MILLIS = 7_000;
  private static final List<String> COMMANDS =
      List.of(
          "protocol_version",
          "name",
          "version",
          "list_commands",
          "known_command",
          "boardsize",
          "komi",
          "clear_board",
          "play",
          "undo",
          "kata-analyze",
          "kata-set-rules",
          "kata-get-rules",
          "kata-get-param",
          "kata-set-param",
          "stop",
          "quit");
  private static final Object OUTPUT_LOCK = new Object();

  private static Path root;
  private static Path incarnation;
  private static BufferedWriter output;
  private static String gtpName;
  private static final Map<String, Double> parameters = new HashMap<>();
  private static final Set<Double> gameValues = new HashSet<>();
  private static final Set<String> armedParameters = new HashSet<>();
  private static Double rejectSet;
  private static boolean rejectParamRestore;
  private static String rules = JAPANESE_RULES;
  private static boolean rulesArmed;
  private static int boardSize = 19;
  private static final Set<String> stones = new LinkedHashSet<>();
  private static final List<String> moves = new ArrayList<>();
  private static boolean analyzing;
  private static int visits;

  private EngineGameToolbarGtpPeer() {}

  public static void main(String[] args) throws Exception {
    root = Path.of(args[0]).toAbsolutePath().normalize();
    Files.createDirectories(root);
    gtpName = args[1];
    parameters.put(WRN, Double.parseDouble(args[2]));
    parameters.put(PDA, Double.parseDouble(args[3]));
    for (int i = 4; i < args.length; i++) {
      String flag = args[i];
      if (flag.startsWith("game-values=")) {
        for (String value : flag.substring("game-values=".length()).split(",")) {
          gameValues.add(Double.parseDouble(value));
        }
      } else if (flag.startsWith("reject-set=")) {
        rejectSet = Double.parseDouble(flag.substring("reject-set=".length()));
      } else if (flag.equals("reject-param-restore")) {
        rejectParamRestore = true;
      } else {
        throw new IllegalArgumentException("unknown flag " + flag);
      }
    }
    incarnation = newIncarnation();
    write(incarnation.resolve("identity.txt"), "pid=" + ProcessHandle.current().pid() + "\n");
    write(root.resolve("latest.txt"), incarnation.toString());
    startWatchdog();
    ScheduledExecutorService analysis =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "toolbar-peer-analysis");
              thread.setDaemon(true);
              return thread;
            });
    analysis.scheduleAtFixedRate(
        EngineGameToolbarGtpPeer::emitAnalysis, 50, 60, TimeUnit.MILLISECONDS);
    try (BufferedReader input =
            new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        BufferedWriter peerOutput =
            new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8))) {
      output = peerOutput;
      String line;
      while ((line = input.readLine()) != null) {
        append(incarnation.resolve("commands.log"), line + "\n");
        Matcher matcher = COMMAND.matcher(line.trim());
        if (line.isBlank() || !matcher.matches()) continue;
        String id = matcher.group(1) == null ? "" : matcher.group(1);
        String command = matcher.group(2);
        String arguments = matcher.group(3).trim();
        synchronized (OUTPUT_LOCK) {
          // KataGo ends a running kata-analyze when any further command arrives.
          analyzing = false;
        }
        Reply reply = handle(command, arguments);
        if (reply.hold() != null) hold(reply.hold(), line);
        respond(reply.success(), id, reply.payload());
        if (command.equals("kata-analyze") && reply.success()) {
          synchronized (OUTPUT_LOCK) {
            visits = 0;
            analyzing = true;
          }
        }
        if (command.equals("quit")) break;
      }
    } finally {
      analysis.shutdownNow();
    }
    System.exit(0);
  }

  private record Reply(boolean success, String payload, String hold) {}

  private static Reply ok(String payload) {
    return new Reply(true, payload, null);
  }

  private static Reply handle(String command, String arguments) {
    switch (command) {
      case "protocol_version":
        return ok("2");
      case "name":
        return ok(gtpName);
      case "version":
        return ok("1.16.4");
      case "list_commands":
        return ok(String.join("\n", COMMANDS));
      case "known_command":
        return ok(Boolean.toString(COMMANDS.contains(arguments)));
      case "boardsize":
        synchronized (OUTPUT_LOCK) {
          boardSize = Integer.parseInt(arguments);
          stones.clear();
          moves.clear();
        }
        return ok("");
      case "clear_board":
        synchronized (OUTPUT_LOCK) {
          stones.clear();
          moves.clear();
        }
        return ok("");
      case "play":
        return play(arguments);
      case "undo":
        synchronized (OUTPUT_LOCK) {
          if (!moves.isEmpty()) stones.remove(moves.remove(moves.size() - 1));
        }
        return ok("");
      case "kata-get-param":
        {
          Double value = parameters.get(arguments);
          String hold = Files.exists(root.resolve("hold-get-param")) ? "get-param" : null;
          return new Reply(true, value == null ? "0" : plain(value), hold);
        }
      case "kata-set-param":
        return setParameter(arguments);
      case "kata-get-rules":
        return ok(rules);
      case "kata-set-rules":
        return setRules(arguments);
      default:
        // stop, komi, kata-analyze, time settings, getpda and other optional commands.
        return ok("");
    }
  }

  private static Reply play(String arguments) {
    String[] words = arguments.split("\\s+");
    if (words.length != 2) return new Reply(false, "wrong argument count", null);
    String point = words[1].toUpperCase(Locale.ROOT);
    synchronized (OUTPUT_LOCK) {
      if (!point.equals("PASS")) {
        if (!stones.add(point)) return new Reply(false, "illegal move", null);
        moves.add(point);
      }
    }
    return ok("");
  }

  private static Reply setParameter(String arguments) {
    String[] words = arguments.split("\\s+");
    if (words.length != 2) return new Reply(false, "wrong argument count", null);
    String name = words[0];
    double value = Double.parseDouble(words[1]);
    boolean tracked = name.equals(WRN) || name.equals(PDA);
    boolean restore = tracked && armedParameters.contains(name) && !isGameValue(value);
    if (tracked && rejectSet != null && same(value, rejectSet)) {
      return new Reply(false, "rejected by fixture", null);
    }
    if (restore && rejectParamRestore) {
      return new Reply(false, "restore rejected by fixture", null);
    }
    if (tracked && isGameValue(value)) armedParameters.add(name);
    parameters.put(name, value);
    String hold =
        restore && Files.exists(root.resolve("hold-param-restore")) ? "param-restore" : null;
    return new Reply(true, "", hold);
  }

  private static Reply setRules(String arguments) {
    String requested = arguments;
    if (!requested.startsWith("{")) {
      requested = requested.equalsIgnoreCase("japanese") ? JAPANESE_RULES : CHINESE_RULES;
    }
    boolean match = similar(requested, CHINESE_RULES);
    boolean restore = rulesArmed && !match;
    if (match) rulesArmed = true;
    rules = requested;
    String hold =
        restore && Files.exists(root.resolve("hold-rules-restore")) ? "rules-restore" : null;
    return new Reply(true, "", hold);
  }

  private static void hold(String label, String line) throws IOException, InterruptedException {
    Path request = root.resolve("hold-" + label);
    if (!Files.deleteIfExists(request)) return;
    Path release = root.resolve("release-" + label);
    write(root.resolve("held-" + label + ".txt"), "command=" + line + "\n");
    long deadline = System.currentTimeMillis() + HOLD_LIMIT_MILLIS;
    while (!Files.exists(release) && System.currentTimeMillis() < deadline) Thread.sleep(20);
    boolean released = Files.deleteIfExists(release);
    write(
        root.resolve("released-" + label + ".txt"),
        released ? "released\n" : "hold-limit-expired\n");
  }

  private static void emitAnalysis() {
    synchronized (OUTPUT_LOCK) {
      if (!analyzing || output == null) return;
      String move = firstEmptyPoint();
      visits += 25;
      try {
        output.write(
            "info move "
                + move
                + " visits "
                + visits
                + " winrate 0.5 scoreMean 0.0 scoreStdev 1.0 prior 0.1 lcb 0.5 order 0 pv "
                + move
                + "\n");
        output.flush();
      } catch (IOException closed) {
        analyzing = false;
      }
    }
  }

  private static String firstEmptyPoint() {
    int[] preferred = {3, boardSize - 4};
    for (int x : preferred) {
      for (int y : preferred) {
        String point = point(x, y);
        if (!stones.contains(point)) return point;
      }
    }
    for (int y = 0; y < boardSize; y++) {
      for (int x = 0; x < boardSize; x++) {
        String point = point(x, y);
        if (!stones.contains(point)) return point;
      }
    }
    return "pass";
  }

  private static String point(int x, int y) {
    return "" + COLUMNS.charAt(x) + (y + 1);
  }

  private static void respond(boolean success, String id, String payload) throws IOException {
    synchronized (OUTPUT_LOCK) {
      output.write(success ? '=' : '?');
      output.write(id);
      if (!payload.isEmpty()) output.write(" " + payload);
      output.write("\n\n");
      output.flush();
    }
  }

  private static boolean isGameValue(double value) {
    for (double gameValue : gameValues) {
      if (same(value, gameValue)) return true;
    }
    return false;
  }

  private static boolean same(double left, double right) {
    return Math.abs(left - right) <= 1e-9;
  }

  private static boolean similar(String left, String right) {
    try {
      return new JSONObject(left).similar(new JSONObject(right));
    } catch (JSONException notJson) {
      return left.equals(right);
    }
  }

  private static String plain(double value) {
    return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
  }

  private static Path newIncarnation() throws IOException {
    for (int ordinal = 1; ordinal < 1000; ordinal++) {
      Path directory = root.resolve(String.format(Locale.ROOT, "incarnation-%03d", ordinal));
      try {
        return Files.createDirectory(directory);
      } catch (FileAlreadyExistsException taken) {
        // An earlier process of this participant owns the ordinal.
      }
    }
    throw new IOException("incarnation ordinals exhausted");
  }

  private static void startWatchdog() {
    Thread watchdog =
        new Thread(
            () -> {
              try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(290));
              } catch (InterruptedException interrupted) {
                return;
              }
              Runtime.getRuntime().halt(70);
            },
            "toolbar-peer-deadline");
    watchdog.setDaemon(true);
    watchdog.start();
  }

  private static void write(Path path, String text) throws IOException {
    Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
    Files.writeString(temporary, text, StandardCharsets.UTF_8);
    Files.move(
        temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }

  private static void append(Path path, String text) throws IOException {
    Files.writeString(
        path, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
  }
}
