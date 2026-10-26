package featurecat.lizzie.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.Leelaz;
import featurecat.lizzie.analysis.MoveData;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BoardDataWrnMetadataTest {
  @Test
  void fullAdoptionCapturesEachSourceWhileRejectedAndOwnershipOnlyFramesKeepMetadata()
      throws Exception {
    try (var ignored = RulesLayerTestHarness.open()) {
      Lizzie.config.enableLizzieCache = true;
      Leelaz first = Lizzie.leelaz;
      Leelaz second = new Leelaz("");
      first.wrn = 0.04;
      second.wrn = 0.1;
      BoardData data = Lizzie.board.getData();
      Object primarySource = new Object();
      Object secondarySource = new Object();
      assertEquals(
          BoardData.AnalysisAdoption.FULL, adopt(data, first, 1000, null, primarySource, false));
      assertEquals(
          BoardData.AnalysisAdoption.FULL, adopt(data, second, 2000, null, secondarySource, true));
      assertWrn(data, 0.04, 0.1);
      assertWrn(SGFParser.parseSgf(SGFParser.saveToString(false), true).getData(), 0.04, 0.1);
      BoardData historical = data.clone();

      first.wrn = 0;
      second.wrn = 0.7;
      for (boolean secondary : List.of(false, true)) {
        Leelaz engine = secondary ? second : first;
        assertEquals(
            BoardData.AnalysisAdoption.REJECTED,
            adopt(data, engine, 10, null, new Object(), secondary));
        assertEquals(
            BoardData.AnalysisAdoption.OWNERSHIP_ONLY,
            adopt(data, engine, 10, List.of(0.5, -0.5), new Object(), secondary));
      }
      assertWrn(data, 0.04, 0.1);
      assertEquals(List.of(0.5, -0.5), data.estimateArray);
      assertEquals(List.of(0.5, -0.5), data.estimateArray2);

      data.isChanged = true;
      assertEquals(
          BoardData.AnalysisAdoption.FULL, adopt(data, first, 10, null, new Object(), false));
      assertWrn(data, 0, 0.1);
      assertWrn(SGFParser.parseSgf(SGFParser.saveToString(false), true).getData(), 0, 0.1);
      assertWrn(historical, 0.04, 0.1);
    }
  }

  @Test
  void clonePayloadCopySyncAndBothClearOperationsPreserveKnownZeroAndUnknown() throws Exception {
    try (var ignored = RulesLayerTestHarness.open()) {
      BoardData source =
          SGFParser.parseSgf(
                  "(;SZ[5]C[source]LZ[Main 45 0 2.5 1.2 -2 wrn=0]"
                      + "LZ2[Second 55 0 -2.5 1.3 1 wrn=0.1])",
                  true)
              .getData();
      BoardData clone = source.clone();
      BoardData copy = BoardData.empty(5, 5);
      copy.comment = "target comment";
      copy.copyAnalysisPayloadFrom(source);
      assertEquals("target comment", copy.comment);
      BoardData restored = BoardData.empty(5, 5);
      restored.sync(source);
      assertEquals("source", restored.comment);
      source.clearAnalysisPayloadState();
      assertUnknown(source);
      assertWrn(clone, 0, 0.1);
      assertWrn(copy, 0, 0.1);
      assertWrn(restored, 0, 0.1);
      restored.tryToClearBestMoves();
      assertUnknown(restored);

      BoardData legacy =
          SGFParser.parseSgf("(;SZ[5]LZ[Main 45 0 2.5 1.2 -2]LZ2[Second 55 0 -2.5 1.3 1])", true)
              .getData();
      copy.copyAnalysisPayloadFrom(legacy);
      clone.sync(legacy);
      assertUnknown(copy);
      assertUnknown(clone);
    }
  }

  @Test
  void fullPayloadWithoutExplicitEngineDoesNotBorrowForegroundWrn() throws Exception {
    try (var ignored = RulesLayerTestHarness.open()) {
      BoardData data = Lizzie.board.getData();
      Lizzie.leelaz.wrn = 0.8;
      assertTrue(data.tryToSetBestMovesWithStatus(moves(100), "detached", false, 100, null, true));
      data.tryToSetBestMoves2(moves(100), "detached-2", false, 100, null);
      assertUnknown(data);
    }
  }

  private static BoardData.AnalysisAdoption adopt(
      BoardData data,
      Leelaz engine,
      int visits,
      List<Double> ownership,
      Object source,
      boolean secondary) {
    return data.adoptOrdinaryAnalysis(
        moves(visits), "KataGo", engine, visits, visits, ownership, source, secondary, false);
  }

  private static List<MoveData> moves(int visits) {
    MoveData move = new MoveData();
    move.coordinate = "C3";
    move.variation = List.of("C3", "D4");
    move.playouts = visits;
    move.winrate = 55;
    move.isKataData = true;
    return new ArrayList<>(List.of(move));
  }

  private static void assertWrn(BoardData data, double primary, double secondary) {
    assertTrue(data.wrnKnown);
    assertTrue(data.wrnKnown2);
    assertEquals(primary, data.wrn);
    assertEquals(secondary, data.wrn2);
  }

  private static void assertUnknown(BoardData data) {
    assertFalse(data.wrnKnown);
    assertFalse(data.wrnKnown2);
    assertEquals(0, data.wrn);
    assertEquals(0, data.wrn2);
  }
}
