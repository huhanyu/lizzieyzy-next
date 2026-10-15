package featurecat.lizzie.analysis;

import featurecat.lizzie.ConfigTestHelper;
import featurecat.lizzie.Lizzie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;

/** Developer-only real policy supplement against a private frozen analysis fixture. */
public final class HumanSlStrengthReuseLiveProbe {
  public static void main(String[] args) throws Exception {
    Path fixture = Path.of(args[0]),
        human = Path.of(args[1]),
        engine = Path.of(args[2]),
        out = Path.of(args[3]);
    Files.createDirectories(out);
    Lizzie.config = ConfigTestHelper.createForTests(out);
    if (!HumanSlStrengthSession.fileHash(human).equals(HumanSlStrength40.HUMAN_SHA))
      throw new IllegalArgumentException("Wrong human network");
    JSONObject raw = new JSONObject(Files.readString(fixture));
    JSONArray moves = new JSONArray(), base = new JSONArray();
    for (int i = 0; i < 12; i++) moves.put(raw.getJSONArray("moves").getJSONArray(i));
    for (int i = 0; i < 13; i++) base.put(raw.getJSONArray("base_analyses").getJSONObject(i));
    String before = base.toString();
    JSONObject game =
        new JSONObject()
            .put("moves", moves)
            .put("rules", "chinese")
            .put("komi", 0)
            .put("boardXSize", 19)
            .put("boardYSize", 19);
    String command = HumanSlStrengthReuseSession.policyCommand(engine, human, out);
    String fingerprint = HumanSlStrengthReuseSession.fingerprint(engine, out);
    AtomicInteger queries = new AtomicInteger();
    long started = System.nanoTime();
    JSONObject policies;
    try (HumanSlAnalysisRunner runner = new HumanSlAnalysisRunner(command, human)) {
      policies =
          HumanSlStrengthReuseSession.prefetchPolicies(
              game,
              q -> {
                if (q.getInt("maxVisits") != 1) throw new AssertionError("Unexpected main search");
                queries.incrementAndGet();
                return runner.request(q, Duration.ofSeconds(180));
              },
              out.resolve("cache"),
              fingerprint,
              () -> false,
              n -> System.out.println("POLICY " + n + "/12"));
    }
    if (!before.equals(base.toString())) throw new AssertionError("Ordinary analysis was changed");
    var extracted = HumanSlStrength40.extract(moves, base, policies, 19, true);
    double rank = HumanSlStrength40.predict(extracted);
    if (!Double.isFinite(rank)) throw new AssertionError("No prediction");
    JSONObject replay =
        HumanSlStrengthReuseSession.prefetchPolicies(
            game,
            q -> {
              throw new AssertionError("Cache replay started an engine");
            },
            out.resolve("cache"),
            fingerprint,
            () -> false,
            n -> {});
    if (rank != HumanSlStrength40.predict(HumanSlStrength40.extract(moves, base, replay, 19, true)))
      throw new AssertionError("Replay mismatch");
    JSONObject evidence =
        new JSONObject()
            .put("policy_queries", queries.get())
            .put("maxVisits", 1)
            .put("bounded_position_concurrency", 4)
            .put("ordinary_analysis_queries", 0)
            .put("ordinary_analysis_unchanged", true)
            .put("cache_replay_exact", true)
            .put("engine_closed", true)
            .put("finite_prediction", true)
            .put("elapsed_seconds", (System.nanoTime() - started) / 1e9);
    Files.writeString(out.resolve("reuse-live-evidence.json"), evidence.toString(2));
    System.out.println("PASS " + evidence);
  }
}
