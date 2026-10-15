package featurecat.lizzie.analysis;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.BoardHistoryNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONObject;

/** Reuses frozen primary analysis; the supplementary engine supplies only human policies. */
public final class HumanSlStrengthReuseSession {
  private HumanSlStrengthReuseSession() {}

  public record Capture(
      BoardHistoryNode start,
      JSONObject game,
      JSONArray base,
      PlayerStrengthEstimator.Report ordinary) {
    public Capture {
      game = new JSONObject(game.toString());
      base = new JSONArray(base.toString());
    }

    @Override
    public JSONObject game() {
      return new JSONObject(game.toString());
    }

    @Override
    public JSONArray base() {
      return new JSONArray(base.toString());
    }
  }

  public static Capture capture() {
    return capture(210, true);
  }

  public static Capture preparationSnapshot() {
    return capture(Integer.MAX_VALUE, false);
  }

  private static Capture capture(int limit, boolean requireAnalysis) {
    if (Board.boardWidth != 19 || Board.boardHeight != 19)
      throw new IllegalArgumentException("HumanSL40.boardSize");
    BoardHistoryNode start = Lizzie.board.getHistory().getStart(), end = start;
    JSONArray base =
        new JSONArray().put(requireAnalysis ? position(start.getData()) : JSONObject.NULL);
    int count = 0;
    Optional<BoardHistoryNode> next = end.next(true);
    while (next.isPresent() && count < limit) {
      end = next.get();
      if (!end.getData().isHistoryActionNode())
        throw new IllegalArgumentException("HumanSL40.setupUnsupported");
      base.put(requireAnalysis ? position(end.getData()) : JSONObject.NULL);
      count++;
      next = end.next(true);
    }
    JSONObject game =
        AnalysisRequestBuilder.buildRequest("strength40-capture", end, 1, false, false, false);
    JSONArray initial = game.optJSONArray("initialStones");
    if (initial != null && !initial.isEmpty())
      throw new IllegalArgumentException("HumanSL40.setupUnsupported");
    game.remove("initialStones");
    game.remove("analyzeTurns");
    validateHistory(game);
    if (requireAnalysis) validateGame(game, base);
    PlayerStrengthEstimator.Report ordinary =
        requireAnalysis
            ? PlayerStrengthEstimator.estimate(
                start, PlayerStrengthEstimator.StrengthModel.XGBOOST20TUN)
            : null;
    return new Capture(start, game, base, ordinary);
  }

  static Object position(BoardData data) {
    // clone() shares MoveData objects, so serialize under the payload-commit lock too.
    synchronized (data) {
      return positionSnapshot(data.clone());
    }
  }

  private static Object positionSnapshot(BoardData data) {
    if (!data.isKataData
        || !data.hasPrimaryAnalysisPayload()
        || !Double.isFinite(data.scoreMean)
        || !Double.isFinite(data.winrate)
        || data.winrate < 0
        || data.winrate > 100) return JSONObject.NULL;
    List<MoveData> searched = new ArrayList<>();
    if (data.bestMoves != null) searched.addAll(data.bestMoves);
    if (data.bestMovesOutOfRange != null) searched.addAll(data.bestMovesOutOfRange);
    var unique = new LinkedHashMap<String, MoveData>();
    for (MoveData move : searched) {
      if (move == null
          || !move.isKataData
          || !Double.isFinite(move.scoreMean)
          || move.coordinate == null) continue;
      unique.putIfAbsent(move.coordinate.toUpperCase(Locale.ROOT), move);
    }
    searched = new ArrayList<>(unique.values());
    searched.sort(Comparator.comparingInt(move -> move.order));
    JSONArray candidates = new JSONArray();
    for (MoveData move : searched) {
      double score = move.scoreMean;
      if (move.scoreMeanIsBlackPerspective && !data.blackToPlay) score = -score;
      candidates.put(
          new JSONObject()
              .put("move", move.coordinate)
              .put("order", move.order)
              .put("scoreMean", score)
              .put("prior", Math.max(0, move.policy) / 100.0));
    }
    double score = data.scoreMean;
    if (data.scoreMeanIsBlackPerspective && !data.blackToPlay) score = -score;
    return new JSONObject()
        .put(
            "rootInfo",
            new JSONObject().put("scoreMean", score).put("winrate", data.winrate / 100.0))
        .put("moveInfos", candidates);
  }

  static boolean hasAnalysis(JSONArray base, int turn) {
    JSONObject before = base.optJSONObject(turn), after = base.optJSONObject(turn + 1);
    return before != null && after != null && !before.getJSONArray("moveInfos").isEmpty();
  }

  static List<Integer> validateGame(JSONObject game, JSONArray base) {
    JSONArray moves = game.getJSONArray("moves");
    if (game.getInt("boardXSize") != 19
        || game.getInt("boardYSize") != 19
        || moves.length() > 210
        || base.length() != moves.length() + 1
        || (game.optJSONArray("initialStones") != null
            && !game.getJSONArray("initialStones").isEmpty()))
      throw new IllegalArgumentException("HumanSL40.setupUnsupported");
    int black = 0, white = 0;
    List<Integer> turns = new ArrayList<>();
    for (int i = 0; i < moves.length(); i++) {
      String color = moves.getJSONArray(i).getString(0);
      if ((!color.equals("B") && !color.equals("W"))
          || (i > 0 && color.equals(moves.getJSONArray(i - 1).getString(0))))
        throw new IllegalArgumentException("HumanSL40.setupUnsupported");
      if (hasAnalysis(base, i)) {
        turns.add(i);
        if (color.equals("B")) black++;
        else white++;
      }
    }
    if (black < 6 || white < 6) throw new IllegalArgumentException("HumanSL40.needAnalysis");
    return turns;
  }

  private static void validateHistory(JSONObject game) {
    JSONArray moves = game.getJSONArray("moves");
    if (game.getInt("boardXSize") != 19 || game.getInt("boardYSize") != 19 || moves.isEmpty())
      throw new IllegalArgumentException("HumanSL40.needGame");
    for (int i = 0; i < moves.length(); i++) {
      String color = moves.getJSONArray(i).getString(0);
      if ((!color.equals("B") && !color.equals("W"))
          || (i > 0 && color.equals(moves.getJSONArray(i - 1).getString(0))))
        throw new IllegalArgumentException("HumanSL40.setupUnsupported");
    }
  }

  public static JSONObject request(JSONObject game, int turn, String profile) {
    JSONObject query = HumanSlStrengthSession.request(game, turn, profile);
    query.put("id", "strength40-policy-" + profile + "-" + turn).put("maxVisits", 1);
    query.getJSONObject("overrideSettings").put("humanSLRootExploreProbWeightless", 0.0);
    return query;
  }

  public static int positionCount(Capture capture) {
    return validateGame(capture.game(), capture.base()).size();
  }

  public static JSONObject policies(
      JSONObject game,
      JSONArray base,
      HumanSlStrengthSession.Query engine,
      Path cache,
      String engineFingerprint,
      BooleanSupplier cancelled,
      IntConsumer progress)
      throws Exception {
    return collectPolicies(
        game, validateGame(game, base), engine, cache, engineFingerprint, cancelled, progress, 1);
  }

  public static JSONObject prefetchPolicies(
      JSONObject game,
      HumanSlStrengthSession.Query engine,
      Path cache,
      String fingerprint,
      BooleanSupplier cancelled,
      IntConsumer progress)
      throws Exception {
    validateHistory(game);
    List<Integer> turns =
        java.util.stream.IntStream.range(0, game.getJSONArray("moves").length()).boxed().toList();
    return collectPolicies(game, turns, engine, cache, fingerprint, cancelled, progress, 4);
  }

  private static JSONObject collectPolicies(
      JSONObject game,
      List<Integer> turns,
      HumanSlStrengthSession.Query engine,
      Path cache,
      String engineFingerprint,
      BooleanSupplier cancelled,
      IntConsumer progress,
      int concurrency)
      throws Exception {
    int positions = game.getJSONArray("moves").length() + 1;
    JSONObject policies = new JSONObject();
    for (String profile : HumanSlStrength40.PROFILES) {
      JSONArray array = new JSONArray();
      for (int i = 0; i < positions; i++) array.put(JSONObject.NULL);
      policies.put(profile, array);
    }
    Files.createDirectories(cache);
    int done = 0;
    var pool =
        java.util.concurrent.Executors.newFixedThreadPool(
            concurrency,
            task -> {
              Thread thread = new Thread(task, "strength40-policy-query");
              thread.setDaemon(true);
              return thread;
            });
    try {
      // A bounded window lets KataGo batch different positions without queuing the whole game.
      for (int offset = 0; offset < turns.size(); offset += concurrency) {
        List<java.util.concurrent.Future<?>> batch = new ArrayList<>();
        for (int index = offset; index < Math.min(turns.size(), offset + concurrency); index++) {
          int turn = turns.get(index);
          batch.add(
              pool.submit(
                  () -> {
                    try {
                      for (String profile : HumanSlStrength40.PROFILES) {
                        if (cancelled.getAsBoolean()) throw new CancellationException();
                        JSONObject query = request(game, turn, profile);
                        String key =
                            HumanSlStrengthSession.hash(
                                ("strength40-policy-v2|"
                                        + engineFingerprint
                                        + "|"
                                        + HumanSlStrength40.HUMAN_SHA
                                        + "|"
                                        + canonical(query))
                                    .getBytes(StandardCharsets.UTF_8));
                        Path file = cache.resolve(key + ".json");
                        JSONArray policy = null;
                        if (Files.isRegularFile(file)) {
                          try {
                            JSONObject cached = new JSONObject(Files.readString(file));
                            policy = validatePolicy(cached, query.getString("id"));
                          } catch (IOException | RuntimeException invalidCache) {
                            policy = null;
                          }
                        }
                        if (policy == null) {
                          JSONObject response = engine.request(query);
                          if (cancelled.getAsBoolean()) throw new CancellationException();
                          policy = validatePolicy(response, query.getString("id"));
                          JSONObject value =
                              new JSONObject()
                                  .put("id", query.getString("id"))
                                  .put("humanPolicy", policy);
                          Path temporary = Files.createTempFile(cache, key, ".tmp");
                          try {
                            Files.writeString(temporary, value.toString());
                            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                          } finally {
                            Files.deleteIfExists(temporary);
                          }
                        }
                        synchronized (policies) {
                          policies.getJSONArray(profile).put(turn, policy);
                        }
                      }
                      return null;
                    } catch (Exception error) {
                      throw new java.util.concurrent.CompletionException(error);
                    }
                  }));
        }
        for (var future : batch) {
          try {
            future.get();
          } catch (java.util.concurrent.ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof java.util.concurrent.CompletionException
                && cause.getCause() != null) cause = cause.getCause();
            if (cause instanceof Exception failure) throw failure;
            throw error;
          }
          progress.accept(++done);
        }
      }
    } finally {
      pool.shutdownNow();
      if (!pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS))
        throw new IOException("HumanSL policy queries did not terminate");
    }
    return policies;
  }

  private static JSONArray validatePolicy(JSONObject response, String id) {
    if (response.has("error")
        || response.has("warning")
        || response.optBoolean("isDuringSearch")
        || !id.equals(response.optString("id")))
      throw new IllegalArgumentException("HumanSL40.invalidResponse");
    // The dedicated policy engine uses the verified human network as its main model too.
    JSONArray policy = response.optJSONArray("humanPolicy");
    if (policy == null) policy = response.optJSONArray("policy");
    if (policy == null || policy.length() != 362)
      throw new IllegalArgumentException("HumanSL40.invalidResponse");
    double total = 0;
    for (int i = 0; i < policy.length(); i++) {
      double p = policy.getDouble(i);
      if (!Double.isFinite(p) || (p < 0 && p != -1) || p > 1)
        throw new IllegalArgumentException("HumanSL40.invalidResponse");
      total += Math.max(0, p);
    }
    if (Math.abs(total - 1) > .001) throw new IllegalArgumentException("HumanSL40.invalidResponse");
    return policy;
  }

  public static PlayerStrengthEstimator.Report report(Capture captured, JSONObject policies) {
    JSONArray base = captured.base();
    JSONObject game = captured.game();
    JSONObject prefix = new JSONObject();
    for (String profile : HumanSlStrength40.PROFILES) {
      JSONArray source = policies.getJSONArray(profile), selected = new JSONArray();
      for (int i = 0; i < base.length(); i++) selected.put(source.get(i));
      prefix.put(profile, selected);
    }
    return PlayerStrengthEstimator.humanSlReport(
        captured.ordinary(),
        HumanSlStrength40.extract(game.getJSONArray("moves"), base, prefix, 19, true));
  }

  public static boolean stillCurrent(Capture captured) {
    if (Lizzie.board == null || Lizzie.board.getHistory().getStart() != captured.start())
      return false;
    try {
      Capture current = captured.ordinary() == null ? preparationSnapshot() : capture();
      return canonical(current.game()).equals(canonical(captured.game()));
    } catch (RuntimeException changed) {
      return false;
    }
  }

  public static String policyCommand(Path engine, Path human, Path cache) throws IOException {
    Files.createDirectories(cache);
    Path config = cache.resolve("policy-analysis.cfg");
    try (var resource =
        HumanSlStrength40.class.getResourceAsStream("/models/strength/humansl40_policy.cfg")) {
      if (resource == null) throw new IOException("Missing HumanSL policy configuration");
      Files.copy(resource, config, StandardCopyOption.REPLACE_EXISTING);
    }
    List<String> command =
        List.of(
            engine.toAbsolutePath().toString(),
            "analysis",
            "-model",
            human.toAbsolutePath().toString(),
            "-config",
            config.toString(),
            "-override-config",
            "logDir=" + cache.resolve("engine-logs"),
            "-quit-without-waiting");
    return command.stream()
        .map(HumanSlStrengthReuseSession::quoteCommandArgument)
        .collect(Collectors.joining(" "));
  }

  static String quoteCommandArgument(String argument) {
    // Utils.splitCommand is not a shell: backslashes are literal, including Windows paths.
    // Alternate quote groups for a literal double quote instead of adding an escape character.
    return "\"" + argument.replace("\"", "\"'\"'\"") + "\"";
  }

  public static String fingerprint(Path engine, Path cache) throws IOException {
    return HumanSlStrengthSession.fileHash(engine)
        + "|"
        + HumanSlStrengthSession.fileHash(cache.resolve("policy-analysis.cfg"));
  }

  static String canonical(Object value) {
    if (value instanceof JSONObject object) {
      return object.keySet().stream()
          .sorted()
          .map(k -> JSONObject.quote(k) + ":" + canonical(object.get(k)))
          .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }
    if (value instanceof JSONArray array) {
      List<String> parts = new ArrayList<>();
      for (int i = 0; i < array.length(); i++) parts.add(canonical(array.get(i)));
      return String.join(",", parts).transform(s -> "[" + s + "]");
    }
    return value instanceof String ? JSONObject.quote((String) value) : String.valueOf(value);
  }
}
