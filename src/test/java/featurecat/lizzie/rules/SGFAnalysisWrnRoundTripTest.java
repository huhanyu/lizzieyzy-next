package featurecat.lizzie.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Lizzie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SGFAnalysisWrnRoundTripTest {
  @TempDir Path directory;

  @ParameterizedTest
  @CsvSource({"LZ,0.04,true", "LZOP,0.04,true", "LZ,NaN,false", "LZOP,3,false"})
  void detachedDualSlotsKeepWrnAndAllAnalysisFieldsAcrossTwoAdoptSaveParseRounds(
      String tag, String wrnToken, boolean known) throws Exception {
    try (var ignored = RulesLayerTestHarness.open()) {
      Lizzie.board.setHistory(
          SGFParser.parseSgf("(;SZ[5]C[live]LZ[Live 45 7 0 0 0 wrn=0.7])", true));
      BoardHistoryList live = Lizzie.board.getHistory();
      Lizzie.leelaz.wrn = 0.9;
      String sgf =
          "(;SZ[5]C[manual comment]"
              + tag
              + "[Main 45 12.3k 2.5 1.2 -2 rootVisits=12345 wrn="
              + wrnToken
              + "\n"
              + "move C3 visits 50000 winrate 5500 scoreMean 2.5 order 10 edgeVisits 0 pv C3 D4"
              + " ownership 0.2 -0.3]"
              + tag
              + "2[Second 55 23.5k -2.5 1.3 1 wrn=0.1 rootVisits=23456\n"
              + "move D4 visits 60000 winrate 4500 scoreMean -2.5 order 3 edgeVisits 17 pv D4 C3"
              + " ownership -0.4 0.5])";
      BoardHistoryList detached = SGFParser.parseSgf(sgf, true);
      assertSame(live, Lizzie.board.getHistory());
      assertEquals("live", live.getData().comment);
      assertEquals(0.7, live.getData().wrn);
      assertEquals(0.9, Lizzie.leelaz.wrn);
      assertDualAnalysis(detached.getStart().getData(), known);
      for (int round = 0; round < 2; round++) {
        Lizzie.board.setHistory(detached);
        String saved = SGFParser.saveToString(false);
        Path file = directory.resolve(tag + "-analysis-" + round + ".sgf");
        Files.writeString(file, saved);
        detached = SGFParser.parseSgf(Files.readString(file), true);
        assertNotNull(detached);
        assertDualAnalysis(detached.getStart().getData(), known);
        assertEquals(known ? 2 : 1, occurrences(saved, " wrn="));
        assertEquals(1, occurrences(detached.getStart().getData().comment, "manual comment"));
        assertEquals(0.9, Lizzie.leelaz.wrn);
      }
    }
  }

  @ParameterizedTest
  @CsvSource({
    "missing,false,0",
    "empty,false,0",
    "0,true,0",
    "0.04,true,0.04",
    "0.1,true,0.1",
    "2,true,2",
    "-0.1,false,0",
    "2.01,false,0",
    "NaN,false,0",
    "Infinity,false,0",
    "-Infinity,false,0",
    "bad,false,0"
  })
  void headerOnlyKnownZeroUnknownAndInvalidExtensionsSurviveTwoRounds(
      String token, boolean known, double value) throws Exception {
    try (var ignored = RulesLayerTestHarness.open()) {
      for (String tag : List.of("LZ", "LZOP", "LZ2", "LZOP2")) {
        boolean secondary = tag.endsWith("2");
        String extension =
            token.equals("missing") ? "" : " wrn=" + (token.equals("empty") ? "" : token);
        BoardHistoryList detached =
            SGFParser.parseSgf(
                "(;SZ[5]C[keep me]"
                    + tag
                    + "[KataGo 45 12.3k 2.5 1.2 -2 rootVisits=12345"
                    + extension
                    + "])",
                true);
        for (int round = 0; round < 2; round++) {
          assertHeaderOnly(detached.getData(), secondary, known, value);
          Lizzie.board.setHistory(detached);
          String saved = SGFParser.saveToString(false);
          assertEquals(known ? 1 : 0, occurrences(saved, " wrn="));
          detached = SGFParser.parseSgf(saved, true);
          assertNotNull(detached);
          assertHeaderOnly(detached.getData(), secondary, known, value);
          assertTrue(detached.getData().comment.contains("keep me"));
        }
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " 2.5", " 2.5 1.2", " 2.5 1.2 -2"})
  void legacyShortAndSixSlotHeadersRemainUnknownWithoutInventedExtension(String scalars)
      throws Exception {
    try (var ignored = RulesLayerTestHarness.open()) {
      BoardHistoryList detached =
          SGFParser.parseSgf(
              "(;SZ[5]LZ[Main 45 12" + scalars + "]LZ2[Second 55 23" + scalars + "])", true);
      for (int round = 0; round < 2; round++) {
        assertUnknown(detached.getData());
        int slots = scalars.isEmpty() ? 3 : 3 + scalars.trim().split(" ").length;
        assertEquals(slots, detached.getData().analysisHeaderSlots);
        assertEquals(slots, detached.getData().analysisHeaderSlots2);
        Lizzie.board.setHistory(detached);
        String saved = SGFParser.saveToString(false);
        assertFalse(saved.contains("wrn="));
        detached = SGFParser.parseSgf(saved, true);
        assertUnknown(detached.getData());
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void setupTransferClearsSourceAndDoesNotLeakWrnIntoAdjacentNodes(boolean sameNode)
      throws Exception {
    try (var ignored = RulesLayerTestHarness.open()) {
      String sgf =
          "(;SZ[5];B[aa]C[analysis owner]"
              + "LZ[Main 45 120 2.5 1.2 -2 wrn=0.04]"
              + "LZ2[Second 55 230 -2.5 1.3 1 wrn=0]"
              + (sameNode ? "" : ";")
              + "AB[bb];W[cc])";
      BoardHistoryList detached = SGFParser.parseSgf(sgf, true);
      assertSetupOwnership(detached, sameNode);
      for (int round = 0; round < 2; round++) {
        Lizzie.board.setHistory(detached);
        String saved = SGFParser.saveToString(false);
        assertEquals(2, occurrences(saved, " wrn="));
        detached = SGFParser.parseSgf(saved, true);
        assertSetupOwnership(detached, sameNode);
      }
    }
  }

  private static void assertDualAnalysis(BoardData data, boolean known) {
    assertEquals(known, data.wrnKnown);
    assertTrue(data.wrnKnown2);
    assertEquals(known ? 0.04 : 0, data.wrn);
    assertEquals(0.1, data.wrn2);
    assertEquals("Main", data.engineName);
    assertEquals("Second", data.engineName2);
    assertEquals(12345, data.getPlayouts());
    assertEquals(23456, data.getPlayouts2());
    assertEquals(12345, data.rootVisits);
    assertEquals(23456, data.rootVisits2);
    assertEquals(2.5, data.scoreMean);
    assertEquals(-2.5, data.scoreMean2);
    assertEquals(1.2, data.scoreStdev);
    assertEquals(1.3, data.scoreStdev2);
    assertEquals(-2, data.pda);
    assertEquals(1, data.pda2);
    var primary = data.bestMoves.get(0);
    var secondary = data.bestMoves2.get(0);
    assertEquals(50000, primary.playouts);
    assertEquals(60000, secondary.playouts);
    assertEquals(10, primary.order);
    assertEquals(3, secondary.order);
    assertEquals(0, primary.edgeVisits);
    assertEquals(17, secondary.edgeVisits);
    assertEquals(List.of("C3", "D4"), primary.variation);
    assertEquals(List.of("D4", "C3"), secondary.variation);
    assertEquals(List.of(0.2, -0.3), data.estimateArray);
    assertEquals(List.of(-0.4, 0.5), data.estimateArray2);
    assertTrue(data.comment.contains("manual comment"));
  }

  private static void assertHeaderOnly(
      BoardData data, boolean secondary, boolean known, double value) {
    assertEquals(known, secondary ? data.wrnKnown2 : data.wrnKnown);
    assertEquals(value, secondary ? data.wrn2 : data.wrn);
    assertEquals(12345, secondary ? data.getPlayouts2() : data.getPlayouts());
    assertEquals(12345, secondary ? data.rootVisits2 : data.rootVisits);
    assertEquals(2.5, secondary ? data.scoreMean2 : data.scoreMean);
    assertEquals(1.2, secondary ? data.scoreStdev2 : data.scoreStdev);
    assertEquals(-2, secondary ? data.pda2 : data.pda);
    assertTrue((secondary ? data.bestMoves2 : data.bestMoves).isEmpty());
  }

  private static void assertSetupOwnership(BoardHistoryList history, boolean sameNode) {
    BoardHistoryNode move = history.getStart().next().orElseThrow();
    BoardHistoryNode setup = move.next().orElseThrow();
    BoardData owner = (sameNode ? setup : move).getData();
    assertTrue(setup.getData().isSnapshotNode());
    assertTrue(owner.wrnKnown);
    assertTrue(owner.wrnKnown2);
    assertEquals(0.04, owner.wrn);
    assertEquals(0, owner.wrn2);
    assertTrue(owner.comment.contains("analysis owner"));
    assertUnknown((sameNode ? move : setup).getData());
    assertUnknown(history.getStart().getData());
    assertUnknown(setup.next().orElseThrow().getData());
  }

  private static void assertUnknown(BoardData data) {
    assertFalse(data.wrnKnown);
    assertFalse(data.wrnKnown2);
    assertEquals(0, data.wrn);
    assertEquals(0, data.wrn2);
  }

  private static int occurrences(String text, String token) {
    return (text.length() - text.replace(token, "").length()) / token.length();
  }
}
