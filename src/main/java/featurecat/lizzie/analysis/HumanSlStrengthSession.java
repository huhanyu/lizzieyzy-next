package featurecat.lizzie.analysis;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.BoardHistoryNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import org.json.JSONArray;
import org.json.JSONObject;

/** Runs a frozen game snapshot, with a private per-query cache separate from board analysis. */
public final class HumanSlStrengthSession {
  @FunctionalInterface
  public interface Query {
    JSONObject request(JSONObject query) throws Exception;
  }

  private HumanSlStrengthSession() {}

  public static JSONObject snapshot() {
    if (Board.boardWidth != 19 || Board.boardHeight != 19)
      throw new IllegalArgumentException("HumanSL 40 requires a 19x19 game");
    BoardHistoryNode start = Lizzie.board.getHistory().getStart();
    BoardHistoryNode end = start;
    int count = 0;
    Optional<BoardHistoryNode> next = end.next(true);
    while (next.isPresent() && count < 210) {
      end = next.get();
      if (!end.getData().isHistoryActionNode())
        throw new IllegalArgumentException(
            "Setup/edit nodes are not supported by this experimental model");
      count++;
      next = end.next(true);
    }
    JSONObject query =
        AnalysisRequestBuilder.buildRequest("snapshot", end, 500, false, false, false);
    if (query.optJSONArray("initialStones") != null
        && !query.getJSONArray("initialStones").isEmpty()) {
      throw new IllegalArgumentException(
          "Handicap/setup positions are not validated for HumanSL 40");
    }
    String rule = start.getData().getProperty("RU");
    if (rule == null || rule.isBlank()) rule = "Chinese";
    String lower = rule.toLowerCase(java.util.Locale.ROOT);
    if (lower.contains("chinese") || lower.contains("中国")) rule = "chinese";
    else if (lower.contains("japanese") || lower.contains("日本")) rule = "japanese";
    else if (lower.contains("korean")) rule = "korean";
    else if (lower.contains("tromp")) rule = "tromp-taylor";
    else if (lower.contains("aga")) rule = "aga";
    else throw new IllegalArgumentException("Unsupported SGF rules: " + rule);
    query.put("rules", rule);
    String komi = start.getData().getProperty("KM");
    query.put("komi", trainingKomi(komi, rule));
    if (query.getJSONArray("moves").length() < 12)
      throw new IllegalArgumentException("At least 12 game moves are required");
    return query;
  }

  static double trainingKomi(String value, String rules) {
    double komi = value == null || value.isBlank() ? 7.5 : Double.parseDouble(value);
    if (!Double.isFinite(komi)) throw new IllegalArgumentException("Invalid SGF komi");
    if (Math.abs(komi) > 100) {
      komi /=
          Math.abs(komi) <= 400 && !rules.equals("japanese") && !rules.equals("korean") ? 50 : 100;
    }
    return komi;
  }

  public static JSONObject request(JSONObject game, int turn, String profile) {
    JSONObject query = new JSONObject(game.toString());
    JSONArray moves = game.getJSONArray("moves"), prefix = new JSONArray();
    if (turn < 0 || turn > moves.length()) throw new IllegalArgumentException("Invalid turn");
    for (int i = 0; i < turn; i++) prefix.put(moves.getJSONArray(i));
    query.put("moves", prefix);
    query.remove("analyzeTurns");
    query.put("id", "strength40-" + profile + "-" + turn);
    query.put("maxVisits", "rank_5d".equals(profile) ? 500 : 1);
    query.put("includePolicy", true);
    query.put("includeOwnership", false);
    query.put("includePVVisits", false);
    query.put("includeMovesOwnership", false);
    query.put("analysisPVLen", 12);
    query.put(
        "overrideSettings",
        new JSONObject()
            .put("reportAnalysisWinratesAs", "SIDETOMOVE")
            .put("humanSLProfile", profile)
            .put("ignorePreRootHistory", false)
            .put("rootNumSymmetriesToSample", 2)
            .put("humanSLRootExploreProbWeightless", "rank_5d".equals(profile) ? .5 : 0.)
            .put("humanSLCpuctPermanent", 2.));
    return query;
  }

  public static List<HumanSlStrength40.Side> run(
      JSONObject snapshot,
      Query engine,
      Path cache,
      String engineFingerprint,
      BooleanSupplier cancelled,
      IntConsumer progress)
      throws Exception {
    JSONObject game = new JSONObject(snapshot.toString());
    if (game.getInt("boardXSize") != 19
        || game.getInt("boardYSize") != 19
        || game.has("initialStones"))
      throw new IllegalArgumentException("Unsupported board/setup snapshot");
    JSONArray gameMoves = game.getJSONArray("moves");
    for (int i = 0; i < gameMoves.length(); i++) {
      String color = gameMoves.getJSONArray(i).getString(0);
      if ((!color.equals("B") && !color.equals("W"))
          || (i > 0 && color.equals(gameMoves.getJSONArray(i - 1).getString(0)))) {
        throw new IllegalArgumentException(
            "Nonalternating history is not validated for HumanSL 40");
      }
    }
    int positions = gameMoves.length() + 1;
    if (positions > 211) throw new IllegalArgumentException("Maximum 210 moves");
    JSONArray base = new JSONArray();
    JSONObject policies = new JSONObject();
    for (String profile : HumanSlStrength40.PROFILES) policies.put(profile, new JSONArray());
    Files.createDirectories(cache);
    for (int turn = 0; turn < positions; turn++) {
      for (String profile : new String[] {"rank_5d", "rank_1d", "rank_9d"}) {
        if (cancelled.getAsBoolean()) throw new CancellationException();
        JSONObject query = request(game, turn, profile);
        // Canonical JSON-independent input fingerprint, including rules, komi,
        // position history, all profile controls, network hashes, and engine/config identity.
        String key =
            hash(
                ("strength40-v1|"
                        + engineFingerprint
                        + "|"
                        + game.get("rules")
                        + "|"
                        + game.getDouble("komi")
                        + "|"
                        + game.getInt("boardXSize")
                        + "|"
                        + query.getJSONArray("moves")
                        + "|"
                        + turn
                        + "|"
                        + profile
                        + "|"
                        + HumanSlStrength40.JUDGE_SHA
                        + "|"
                        + HumanSlStrength40.HUMAN_SHA)
                    .getBytes(StandardCharsets.UTF_8));
        Path file = cache.resolve(key + ".json");
        JSONObject response = null;
        if (Files.isRegularFile(file)) {
          try {
            response = new JSONObject(Files.readString(file));
            validate(response, query.getString("id"));
          } catch (IOException | RuntimeException invalidCache) {
            response = null;
          }
        }
        if (response == null) {
          response = engine.request(query);
          if (cancelled.getAsBoolean()) throw new CancellationException();
          validate(response, query.getString("id"));
          Path temporary = Files.createTempFile(cache, key, ".tmp");
          try {
            Files.writeString(temporary, response.toString());
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
          } finally {
            Files.deleteIfExists(temporary);
          }
        }
        policies.getJSONArray(profile).put(response.getJSONArray("humanPolicy"));
        if ("rank_5d".equals(profile)) base.put(response);
      }
      progress.accept(turn + 1);
    }
    return HumanSlStrength40.evaluate(
        game.getJSONArray("moves"), base, policies, game.getInt("boardXSize"));
  }

  private static void validate(JSONObject response, String id) {
    if (response.has("error")
        || response.has("warning")
        || response.optBoolean("isDuringSearch")
        || !id.equals(response.optString("id"))
        || !response.has("rootInfo")
        || !response.has("moveInfos")
        || !response.has("humanPolicy")) {
      throw new IllegalArgumentException("Incomplete, incompatible, or failed HumanSL response");
    }
  }

  public static String fileHash(Path path) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (var stream = Files.newInputStream(path)) {
        byte[] buffer = new byte[65536];
        int n;
        while ((n = stream.read(buffer)) >= 0) digest.update(buffer, 0, n);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public static String hash(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public static List<HumanSlStrength40.Side> run(
      JSONObject snapshot,
      HumanSlAnalysisRunner runner,
      Path cache,
      String engineFingerprint,
      BooleanSupplier cancelled,
      IntConsumer progress)
      throws Exception {
    return run(
        snapshot,
        query -> runner.request(query, Duration.ofSeconds(180)),
        cache,
        engineFingerprint,
        cancelled,
        progress);
  }
}
