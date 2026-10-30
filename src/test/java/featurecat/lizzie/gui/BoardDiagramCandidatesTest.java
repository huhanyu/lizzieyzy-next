package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.*;

import featurecat.lizzie.analysis.MoveData;
import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.Stone;
import java.awt.Color;
import java.nio.file.Path;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class BoardDiagramCandidatesTest {
  @Test
  void teachingMarksAndStonesAreNotCoveredAndRanksAreNotRenumbered() {
    BoardData data = BoardData.empty(9, 9);
    data.stones[0] = Stone.BLACK;
    data.getProperties().put("LB", "dd:A");
    data.bestMoves =
        List.of(move("D6", 65, 0, 200), move("E5", 64, 1, 100), move("A9", 62, 2, 100));
    var result = BoardDiagramCandidates.capture(9, 9, data, false, 5);
    assertEquals(1, result.size());
    assertEquals(1, result.get(0).order());
    var plain =
        BoardDiagram.capture(9, 9, data, false, 0, false, false)
            .render(600, result, "Black winrate");
    // Corner stone remains dark; the candidate is on a different empty intersection.
    double step = 600 / 9.3;
    int left = (int) Math.round((600 - 8 * step) / 2);
    int top = (int) Math.round((600 - 8 * step) / 2);
    assertEquals(Color.BLACK.getRGB(), plain.getRGB(left, top));
  }

  @Test
  void filtersInvalidResultsOrdersDeduplicatesAndFreezesValues() {
    BoardData data = BoardData.empty(19, 19);
    data.stones[0] = Stone.BLACK;
    data.bestMoves =
        new ArrayList<>(
            List.of(
                move("D4", 61.2, 2, 100),
                move("Q16", 63.8, 0, 200),
                move("D4", 59, 3, 10),
                move("A19", 50, 0, 100),
                move("pass", 50, 0, 100),
                move("Q99", 50, 0, 100),
                move("I4", 50, 0, 100),
                move("B2", Double.NaN, 0, 100),
                move("C2", 101, 0, 100),
                move("E2", 50, 0, 0)));
    var result = BoardDiagramCandidates.capture(19, 19, data, false, 5);
    assertEquals(2, result.size());
    assertEquals(15, result.get(0).x());
    assertEquals(3, result.get(0).y());
    assertEquals(63.8, result.get(0).winrate());
    assertEquals(61.2, result.get(1).winrate());
    data.bestMoves.get(1).winrate = 1;
    data.bestMoves.clear();
    assertEquals(63.8, result.get(0).winrate());
    assertThrows(UnsupportedOperationException.class, () -> result.clear());
  }

  @Test
  void whiteToPlayIsFlippedOnlyWhenBlackPerspectiveIsSelected() {
    BoardData data = BoardData.empty(9, 9);
    data.blackToPlay = false;
    data.bestMoves = List.of(move("E5", 72.4, 0, 100));
    assertEquals(72.4, BoardDiagramCandidates.capture(9, 9, data, false, 5).get(0).winrate(), 1e-9);
    assertEquals(27.6, BoardDiagramCandidates.capture(9, 9, data, true, 5).get(0).winrate(), 1e-9);
  }

  @Test
  void limitsToFiveAndDoesNotInventMissingAnalysis() {
    BoardData data = BoardData.empty(9, 9);
    assertTrue(BoardDiagramCandidates.capture(9, 9, data, false, 5).isEmpty());
    data.bestMoves = new ArrayList<>();
    for (int i = 0; i < 8; i++) data.bestMoves.add(move("A" + (i + 1), 55, i, 10));
    assertEquals(5, BoardDiagramCandidates.capture(9, 9, data, false, 5).size());
    assertEquals(8, BoardDiagramCandidates.capture(9, 9, data, false, 10).size());
    assertThrows(
        IllegalArgumentException.class,
        () -> BoardDiagramCandidates.capture(9, 9, data, false, 11));
  }

  @Test
  void emptyDiagramHasNoInkInCoordinateMargins() {
    var image =
        BoardDiagram.capture(19, 19, BoardData.empty(19, 19), false, 0, false, false).render(600);
    int margin = (int) ((600 - (18 * 600 / 19.3)) / 2) - 2;
    for (int x = 0; x < 600; x++)
      for (int y = 0; y < margin; y++) {
        assertEquals(Color.WHITE.getRGB(), image.getRGB(x, y));
        assertEquals(Color.WHITE.getRGB(), image.getRGB(y, x));
        assertEquals(Color.WHITE.getRGB(), image.getRGB(x, 599 - y));
        assertEquals(Color.WHITE.getRGB(), image.getRGB(599 - y, x));
      }
  }

  @Test
  void candidateImageIsEntirelyGrayscaleAndProducesAnIllustrativePreview() throws Exception {
    BoardData data = BoardData.empty(9, 9);
    data.stones[2 * 9 + 2] = Stone.BLACK;
    data.stones[3 * 9 + 2] = Stone.WHITE;
    data.bestMoves = new ArrayList<>();
    String[] points = {"E5", "F5", "D4", "F4", "E4", "G4", "D6", "E6", "F6", "G6"};
    for (int i = 0; i < points.length; i++)
      data.bestMoves.add(move(points[i], 63.8 - i * 0.7, i, 200 - i));
    var candidates = BoardDiagramCandidates.capture(9, 9, data, false, 10);
    var diagram = BoardDiagram.capture(9, 9, data, false, 0, false, false);
    var image = diagram.render(600, candidates, "Black winrate - example data");
    for (int x = 0; x < 600; x++)
      for (int y = 0; y < 600; y++) {
        Color c = new Color(image.getRGB(x, y));
        assertEquals(c.getRed(), c.getGreen());
        assertEquals(c.getRed(), c.getBlue());
      }
    ImageIO.write(
        diagram.render(3600, candidates, "黑方胜率 · 候选点显示示例（示意数据）"),
        "png",
        Path.of("target/board-candidates-example.png").toFile());
  }

  private static MoveData move(String coordinate, double winrate, int order, int visits) {
    MoveData move = new MoveData();
    move.coordinate = coordinate;
    move.winrate = winrate;
    move.order = order;
    move.playouts = visits;
    return move;
  }
}
