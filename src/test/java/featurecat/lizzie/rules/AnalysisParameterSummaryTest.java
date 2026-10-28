package featurecat.lizzie.rules;

import static org.junit.jupiter.api.Assertions.*;

import featurecat.lizzie.AppLocale;
import featurecat.lizzie.ExtraMode;
import featurecat.lizzie.Lizzie;
import java.util.ResourceBundle;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AnalysisParameterSummaryTest {
  @ParameterizedTest
  @CsvSource({"0,0", "2,0", "0,0.04", "2,0.1", "-2,0.04"})
  void ordinarySummaryShowsEachNonzeroRecordedParameter(double pda, double wrn) throws Exception {
    withBoard(
        () -> {
          BoardData data = analysis();
          data.pda = pda;
          data.wrn = wrn;
          data.wrnKnown = true;
          SGFParser.appendComment();
          assertParameters(data.comment, pda, wrn);
          String first = data.comment;
          SGFParser.appendComment();
          assertEquals(first, data.comment, "regeneration replaces the automatic summary");
          assertTrue(data.comment.startsWith("Personal note"));
        });
  }

  @Test
  void unknownBreadthDoesNotBorrowCurrentEngineOrStaleNumericValue() throws Exception {
    withBoard(
        () -> {
          BoardData data = analysis();
          data.pda = -1;
          data.wrn = 0.1;
          data.wrnKnown = false;
          Lizzie.leelaz.wrn = 0.7;
          SGFParser.appendComment();
          assertParameters(data.comment, -1, 0);
        });
  }

  @Test
  void humanGameSummaryUsesRecordedBreadthIndependentlyOfPda() throws Exception {
    withBoard(
        () -> {
          BoardData data = analysis();
          data.wrn = 0.04;
          data.wrnKnown = true;
          Lizzie.board.isGameBoard = true;
          SGFParser.appendComment();
          assertParameters(data.comment, 0, 0.04);
          assertTrue(data.comment.startsWith("Personal note"));
          String first = data.comment;
          SGFParser.appendComment();
          assertEquals(first, data.comment);
        });
  }

  @Test
  void engineGameSummaryUsesSameKnownParameterRule() throws Exception {
    withBoard(
        () -> {
          BoardData data = analysis();
          data.pda = -2;
          data.wrn = 0.1;
          data.wrnKnown = true;
          Lizzie.board
              .getHistory()
              .getGameInfo()
              .attachEngineGameRecordContext(
                  featurecat.lizzie.enginegame.EngineGameRecordContext.saveFormattingMarker());
          SGFParser.appendComment(true);
          assertParameters(data.comment, -2, 0.1);
          assertTrue(data.comment.contains("Personal note"), data.comment);
          String first = data.comment;
          SGFParser.appendComment();
          assertEquals(first, data.comment);
          // Engine-game export associates the previous position's summary with this move.
          BoardData previous = Lizzie.board.getHistory().getStart().getData();
          previous.isKataData = true;
          previous.engineName = "Previous";
          previous.setPlayouts(99);
          previous.pda = 2;
          previous.wrn = 0.04;
          previous.wrnKnown = true;
          previous.comment = "Root note";
          for (int round = 0; round < 2; round++) {
            try {
              BoardHistoryList restored = SGFParser.parseSgf(SGFParser.saveToString(false), true);
              BoardData saved = restored.getStart().next().orElseThrow().getData();
              assertTrue(saved.comment.contains("Personal note"), saved.comment);
              assertParameters(saved.comment, 2, 0.04);
              assertFalse(saved.comment.contains("Root note"));
              assertEquals(1, saved.comment.split("WRN:", -1).length - 1);
              Lizzie.board.setHistory(restored);
              Lizzie.board
                  .getHistory()
                  .getGameInfo()
                  .attachEngineGameRecordContext(
                      featurecat.lizzie.enginegame.EngineGameRecordContext.saveFormattingMarker());
            } catch (java.io.IOException failure) {
              throw new java.io.UncheckedIOException(failure);
            }
          }
        });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void engineGameExportKeepsPredecessorSummariesAfterChineseScoreModeChanges(boolean originalMode)
      throws Exception {
    try (RulesLayerTestHarness ignored = RulesLayerTestHarness.open()) {
      ResourceBundle previous = Lizzie.resourceBundle;
      try {
        Lizzie.resourceBundle = AppLocale.SIMPLIFIED_CHINESE.loadBundle();
        Lizzie.config.showKataGoScoreLeadWithKomi = originalMode;
        Lizzie.board
            .getHistory()
            .getGameInfo()
            .attachEngineGameRecordContext(
                featurecat.lizzie.enginegame.EngineGameRecordContext.saveFormattingMarker());
        BoardHistoryNode[] nodes = new BoardHistoryNode[4];
        String[] summaries = new String[4];
        nodes[0] = Lizzie.board.getHistory().getStart();
        for (int i = 0; i < nodes.length; i++) {
          if (i > 0) {
            Lizzie.board.getHistory().place(i, i, i % 2 == 1 ? Stone.BLACK : Stone.WHITE);
            nodes[i] = Lizzie.board.getHistory().getCurrentHistoryNode();
          }
          BoardData data = nodes[i].getData();
          data.isKataData = true;
          data.engineName = "Engine" + i;
          data.setPlayouts(100 + i);
          data.winrate = 51 + i;
          data.scoreMean = i + 0.5;
          data.pda = i + 1;
          data.wrn = (i + 1) * 0.1;
          data.wrnKnown = true;
          summaries[i] = SGFParser.formatCommentPk(nodes[i]);
          data.comment = "Personal note " + i + "\n\n" + summaries[i];
        }
        Lizzie.config.showKataGoScoreLeadWithKomi = !originalMode;
        String rootComment = nodes[0].getData().comment;
        nodes[0].getData().comment = "";
        String rootSummary = SGFParser.formatCommentPk(nodes[0]);
        nodes[0].getData().comment = rootComment;
        // Saving must not mutate the historical summaries or move another node's personal note.
        for (int round = 0; round < 2; round++) {
          BoardHistoryList restored = SGFParser.parseSgf(SGFParser.saveToString(false), true);
          BoardHistoryNode saved = restored.getStart();
          for (int i = 1; i < nodes.length; i++) {
            saved = saved.next().orElseThrow();
            String expectedSummary = i == 1 ? rootSummary : summaries[i - 1];
            assertEquals(
                "Personal note " + i + "\n\n" + expectedSummary,
                saved.getData().comment,
                "move " + i + " score mode " + originalMode);
            assertParameters(saved.getData().comment, i, i * 0.1);
            assertEquals(
                "Personal note " + i + "\n\n" + summaries[i],
                nodes[i].getData().comment,
                "export leaves stored comments untouched");
          }
        }
      } finally {
        Lizzie.resourceBundle = previous;
      }
    }
  }

  @Test
  void secondaryExportUsesSecondaryParametersAndPreservesPersonalComment() throws Exception {
    try (RulesLayerTestHarness ignored = RulesLayerTestHarness.open()) {
      ResourceBundle previous = Lizzie.resourceBundle;
      try {
        Lizzie.resourceBundle = AppLocale.ENGLISH.loadBundle();
        Lizzie.config.extraMode = ExtraMode.Double_Engine;
        Lizzie.config.appendWinrateToComment = true;
        Lizzie.board.getHistory().place(2, 2, Stone.BLACK);
        BoardData data = Lizzie.board.getData();
        data.comment = "Personal note";
        data.isKataData2 = true;
        data.engineName2 = "Secondary";
        data.setPlayouts2(123);
        data.pda = 3;
        data.wrn = 0.8;
        data.wrnKnown = true;
        data.pda2 = 2;
        data.wrn2 = 0.1;
        data.wrnKnown2 = true;
        BoardHistoryList restored = SGFParser.parseSgf(SGFParser.saveToString(false), true);
        String comment = restored.getStart().next().orElseThrow().getData().comment;
        assertParameters(comment, 2, 0.1);
        assertFalse(comment.contains("0.8"));
        assertTrue(comment.startsWith("Personal note"));
        Lizzie.board.setHistory(restored);
        BoardHistoryList reopened = SGFParser.parseSgf(SGFParser.saveToString(false), true);
        assertEquals(comment, reopened.getStart().next().orElseThrow().getData().comment);
      } finally {
        Lizzie.resourceBundle = previous;
      }
    }
  }

  @Test
  void humanGameSaveRetainsNotesOnTheSummaryDestination() throws Exception {
    try (RulesLayerTestHarness ignored = RulesLayerTestHarness.open()) {
      BoardData analyzed = analysis();
      analyzed.wrn = 0.04;
      analyzed.wrnKnown = true;
      Lizzie.board.getHistory().place(1, 1, Stone.WHITE);
      Lizzie.board.getData().comment = "Destination note";
      Lizzie.board.isGameBoard = true;
      for (int round = 0; round < 2; round++) {
        BoardHistoryList restored = SGFParser.parseSgf(SGFParser.saveToString(false), true);
        BoardData destination =
            restored.getStart().next().orElseThrow().next().orElseThrow().getData();
        assertTrue(destination.comment.contains("Destination note"), destination.comment);
        assertParameters(destination.comment, 0, 0.04);
        Lizzie.board.setHistory(restored);
      }
    }
  }

  @Test
  void publicAnalysisClearingRemovesBothSlotsWithoutTouchingAdjacentNodes() throws Exception {
    try (RulesLayerTestHarness ignored = RulesLayerTestHarness.open()) {
      BoardData parent = analysis();
      parent.wrn = 0.04;
      parent.wrnKnown = true;
      parent.isKataData2 = true;
      parent.engineName2 = "Secondary";
      parent.wrn2 = 0.1;
      parent.wrnKnown2 = true;
      parent.setPlayouts2(100);
      Lizzie.board.getHistory().place(1, 1, Stone.WHITE);
      BoardHistoryNode current = Lizzie.board.getHistory().getCurrentHistoryNode();
      current.getData().sync(parent);
      Lizzie.board.clearBestMovesInfomation(current);
      Lizzie.board.clearbestmovesInfomation2(current);
      assertFalse(current.getData().wrnKnown);
      assertFalse(current.getData().wrnKnown2);
      assertEquals(0, current.getData().wrn);
      assertEquals(0, current.getData().wrn2);
      assertTrue(parent.wrnKnown);
      assertTrue(parent.wrnKnown2);
      Lizzie.board.clearBestMovesInfomationAfter(Lizzie.board.getHistory().getStart());
      Lizzie.board.clearBestMovesInfomationAfter2(Lizzie.board.getHistory().getStart());
      assertFalse(parent.wrnKnown);
      assertFalse(parent.wrnKnown2);
      assertFalse(SGFParser.saveToString(false).contains("wrn="));
    }
  }

  private static BoardData analysis() {
    Lizzie.board.getHistory().place(2, 2, Stone.BLACK);
    BoardData data = Lizzie.board.getData();
    data.isKataData = true;
    data.engineName = "Primary";
    data.setPlayouts(123);
    data.comment = "Personal note";
    return data;
  }

  private static void assertParameters(String comment, double pda, double wrn) {
    String pdaLabel = Lizzie.resourceBundle.getString("SGFParse.pda");
    String wrnLabel = Lizzie.resourceBundle.getString("SGFParse.wrn");
    assertEquals(pda != 0, comment.contains(pdaLabel), comment);
    assertEquals(wrn != 0, comment.contains(wrnLabel), comment);
    if (pda != 0) assertTrue(comment.contains(pdaLabel + pda), comment);
    if (wrn != 0) assertTrue(comment.contains(wrnLabel + wrn), comment);
  }

  private static void withBoard(Runnable assertion) throws Exception {
    try (RulesLayerTestHarness ignored = RulesLayerTestHarness.open()) {
      ResourceBundle previous = Lizzie.resourceBundle;
      try {
        Lizzie.resourceBundle = AppLocale.ENGLISH.loadBundle();
        SwingUtilities.invokeAndWait(assertion);
      } finally {
        Lizzie.resourceBundle = previous;
      }
    }
  }
}
