package featurecat.lizzie.analysis;

import featurecat.lizzie.ConfigTestHelper;
import featurecat.lizzie.Lizzie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Explicit developer-only real-engine smoke runner; private inputs remain outside Git. */
public final class HumanSlStrengthLiveProbe {
  public static void main(String[] args) throws Exception {
    Path fixture = Path.of(args[0]), human = Path.of(args[1]), judge = Path.of(args[2]);
    Path out = Path.of(args[3]);
    Files.createDirectories(out);
    Lizzie.config = ConfigTestHelper.createForTests(out);
    if (!HumanSlStrengthSession.fileHash(human).equals(HumanSlStrength40.HUMAN_SHA)
        || !HumanSlStrengthSession.fileHash(judge).equals(HumanSlStrength40.JUDGE_SHA)) {
      throw new IllegalArgumentException("Network hash mismatch");
    }
    JSONObject raw = new JSONObject(Files.readString(fixture));
    JSONArray prefix = new JSONArray(), moves = raw.getJSONArray("moves");
    for (int i = 0; i < Math.min(12, moves.length()); i++) prefix.put(moves.getJSONArray(i));
    JSONObject game =
        new JSONObject()
            .put("moves", prefix)
            .put("rules", "chinese")
            .put("komi", 0)
            .put("boardXSize", 19)
            .put("boardYSize", 19);
    Path config = out.resolve("standard.cfg");
    try (var input =
        HumanSlStrength40.class.getResourceAsStream("/models/strength/humansl40_analysis.cfg")) {
      Files.copy(input, config, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
    String command =
        "/opt/homebrew/bin/katago analysis -model \""
            + judge
            + "\" -config \""
            + config
            + "\" -override-config numAnalysisThreads=1,numSearchThreadsPerAnalysisThread=1,nnMaxBatchSize=32,nnCacheSizePowerOfTwo=20 -quit-without-waiting";
    long started = System.nanoTime();
    List<HumanSlStrength40.Side> result;
    try (HumanSlAnalysisRunner runner = new HumanSlAnalysisRunner(command, human)) {
      JSONObject version =
          runner.request(
              new JSONObject().put("id", "version").put("action", "query_version"),
              Duration.ofSeconds(180));
      System.out.println("ENGINE " + version);
      result =
          HumanSlStrengthSession.run(
              game,
              runner,
              out.resolve("cache"),
              version.toString(),
              () -> false,
              n -> System.out.println("POSITION " + n + "/13"));
      List<HumanSlStrength40.Side> replay =
          HumanSlStrengthSession.run(
              game,
              q -> {
                throw new AssertionError("Cache reuse requested the engine");
              },
              out.resolve("cache"),
              version.toString(),
              () -> false,
              n -> {});
      for (int i = 0; i < result.size(); i++)
        if (result.get(i).rank() != replay.get(i).rank())
          throw new AssertionError("Cache replay mismatch");
    }
    JSONArray sides = new JSONArray();
    for (HumanSlStrength40.Side side : result)
      sides.put(
          new JSONObject()
              .put("side", side.color())
              .put("samples", side.samples())
              .put("rank", side.rank())
              .put("feature_count", side.features().length));
    JSONObject evidence =
        new JSONObject()
            .put("positions", 13)
            .put("queries", 39)
            .put("maxVisits", 500)
            .put("sides", sides)
            .put("cache_replay_exact", true)
            .put("engine_closed", true)
            .put("elapsed_seconds", (System.nanoTime() - started) / 1e9);
    Files.writeString(out.resolve("live-evidence.json"), evidence.toString(2));
    System.out.println("PASS " + evidence);
  }
}
