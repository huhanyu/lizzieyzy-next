package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HumanSlStrength40Test {
  @TempDir Path cache;

  static JSONObject game() {
    JSONArray moves = new JSONArray();
    for (int i = 0; i < 12; i++) moves.put(new JSONArray().put(i % 2 == 0 ? "B" : "W").put("pass"));
    return new JSONObject()
        .put("moves", moves)
        .put("rules", "chinese")
        .put("komi", 7.5)
        .put("boardXSize", 19)
        .put("boardYSize", 19);
  }

  static JSONObject response(JSONObject query) {
    JSONArray policy = new JSONArray();
    for (int i = 0; i < 361; i++) policy.put(0.);
    policy.put(1.);
    return new JSONObject()
        .put("id", query.getString("id"))
        .put("isDuringSearch", false)
        .put("rootInfo", new JSONObject().put("scoreLead", 0).put("winrate", .5))
        .put(
            "moveInfos",
            new JSONArray()
                .put(
                    new JSONObject()
                        .put("move", "pass")
                        .put("order", 0)
                        .put("scoreMean", 0)
                        .put("winrate", .5)
                        .put("prior", 1)))
        .put("humanPolicy", policy);
  }

  @Test
  void profileQueriesMatchTrainingRecipe() {
    JSONObject main = HumanSlStrengthSession.request(game(), 7, "rank_5d");
    JSONObject side = HumanSlStrengthSession.request(game(), 7, "rank_9d");
    assertEquals(7, main.getJSONArray("moves").length());
    assertEquals(500, main.getInt("maxVisits"));
    assertEquals(1, side.getInt("maxVisits"));
    assertEquals(
        .5, main.getJSONObject("overrideSettings").getDouble("humanSLRootExploreProbWeightless"));
    assertEquals(
        0, side.getJSONObject("overrideSettings").getDouble("humanSLRootExploreProbWeightless"));
    assertEquals(2, side.getJSONObject("overrideSettings").getInt("rootNumSymmetriesToSample"));
    assertFalse(main.has("analyzeTurns"));
  }

  @Test
  void sgfKomiUsesTrainingNormalizationNotGuiReviewSettings() {
    assertEquals(7.5, HumanSlStrengthSession.trainingKomi("375", "chinese"));
    assertEquals(6.5, HumanSlStrengthSession.trainingKomi("650", "japanese"));
    assertEquals(0, HumanSlStrengthSession.trainingKomi("0", "chinese"));
    assertEquals(7.5, HumanSlStrengthSession.trainingKomi("", "chinese"));
    assertThrows(
        IllegalArgumentException.class,
        () -> HumanSlStrengthSession.trainingKomi("NaN", "chinese"));
  }

  @Test
  void runsAllPositionsAndProfilesThenReusesCache() throws Exception {
    AtomicInteger queries = new AtomicInteger(), progress = new AtomicInteger();
    List<HumanSlStrength40.Side> first =
        HumanSlStrengthSession.run(
            game(),
            q -> {
              queries.incrementAndGet();
              return response(q);
            },
            cache,
            "test-engine",
            () -> false,
            progress::set);
    assertEquals(39, queries.get());
    assertEquals(13, progress.get());
    assertEquals(40, first.get(0).features().length);
    assertEquals(6, first.get(0).samples());
    assertEquals(0, first.get(0).features()[37]);
    assertEquals(1, first.get(0).features()[38]);
    List<HumanSlStrength40.Side> replay =
        HumanSlStrengthSession.run(
            game(),
            q -> {
              fail("Should use validated cache");
              return null;
            },
            cache,
            "test-engine",
            () -> false,
            i -> {});
    assertEquals(first.get(0).rank(), replay.get(0).rank());
    JSONObject otherKomi = game().put("komi", 0.);
    HumanSlStrengthSession.run(
        otherKomi,
        q -> {
          queries.incrementAndGet();
          return response(q);
        },
        cache,
        "test-engine",
        () -> false,
        i -> {});
    assertEquals(78, queries.get());
  }

  @Test
  void doesNotCacheErrorsOrInventMissingProfiles() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            HumanSlStrengthSession.run(
                game(),
                q -> response(q).put("error", "human model missing"),
                cache,
                "bad",
                () -> false,
                i -> {}));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            HumanSlStrengthSession.run(
                game(),
                q -> {
                  JSONObject r = response(q);
                  r.remove("humanPolicy");
                  return r;
                },
                cache,
                "bad",
                () -> false,
                i -> {}));
  }

  @Test
  void cancellationStopsBeforeQuery() {
    assertThrows(
        CancellationException.class,
        () ->
            HumanSlStrengthSession.run(
                game(),
                q -> {
                  fail("Cancelled query must not run");
                  return null;
                },
                cache,
                "cancel",
                () -> true,
                i -> {}));
  }

  @Test
  void rejectsUnsupportedSetupAndNonalternatingHistoriesBeforeCompute() {
    JSONObject setup =
        game().put("initialStones", new JSONArray().put(new JSONArray().put("B").put("D4")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            HumanSlStrengthSession.run(
                setup,
                q -> {
                  fail("Unsupported setup must not be searched");
                  return null;
                },
                cache,
                "bad",
                () -> false,
                i -> {}));
    JSONObject nonalternating = game();
    nonalternating.getJSONArray("moves").getJSONArray(1).put(0, "B");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            HumanSlStrengthSession.run(
                nonalternating,
                q -> {
                  fail("Nonalternating record must not be searched");
                  return null;
                },
                cache,
                "bad",
                () -> false,
                i -> {}));
  }

  @Test
  void insufficientCoverageIsNaNNotZero() {
    JSONArray policy = new JSONArray();
    for (int i = 0; i < 362; i++) policy.put(.001);
    List<JSONObject> candidates = List.of(new JSONObject().put("move", "pass").put("scoreMean", 0));
    double[] values = HumanSlStrength40.human(candidates, policy, "pass", 19);
    assertTrue(Double.isNaN(values[1]));
    assertEquals(.001, values[2]);
    assertEquals(0, values[3]);
  }

  @Test
  void privateRealGameParityWhenProvided() throws Exception {
    String path = System.getProperty("lizzie.strength.humansl40.fixture", "");
    org.junit.jupiter.api.Assumptions.assumeFalse(path.isBlank());
    JSONObject fixture = new JSONObject(Files.readString(Path.of(path)));
    List<HumanSlStrength40.Side> sides =
        HumanSlStrength40.evaluate(
            fixture.getJSONArray("moves"),
            fixture.getJSONArray("base_analyses"),
            fixture.getJSONObject("human_policies"),
            19);
    for (int i = 0; i < 2; i++) {
      JSONArray expected = fixture.getJSONArray("expected_features").getJSONArray(i);
      double[] actual = sides.get(i).features();
      for (int j = 0; j < 40; j++) {
        if (expected.isNull(j)) assertTrue(Double.isNaN(actual[j]), "feature " + j);
        else
          assertEquals(expected.getDouble(j), actual[j], 1e-9, HumanSlStrength40.featureNames()[j]);
      }
      assertEquals(fixture.getJSONArray("expected_ranks").getDouble(i), sides.get(i).rank(), 2e-5);
    }
  }
}
