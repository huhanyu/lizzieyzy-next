package featurecat.lizzie.gui;

import featurecat.lizzie.analysis.MoveData;
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.Stone;
import java.util.*;

/** Detached primary-analysis candidates belonging to the captured position, in engine order. */
final class BoardDiagramCandidates {
  record Candidate(int x, int y, double winrate, int order, int visits) {}

  private BoardDiagramCandidates() {}

  static List<Candidate> capture(
      int width, int height, BoardData data, boolean alwaysBlack, int limit) {
    if (limit < 5 || limit > 10) throw new IllegalArgumentException("Candidate count must be 5–10");
    List<Candidate> result = new ArrayList<>();
    Set<Integer> marked = new HashSet<>();
    for (String key : List.of("LB", "TR", "SQ", "CR", "MA")) {
      for (String entry : data.getProperties().getOrDefault(key, "").split(",")) {
        String point = entry.split(":", 2)[0];
        if (point.length() != 2) continue;
        int x = BoardDiagram.coordinate(point.charAt(0)),
            y = BoardDiagram.coordinate(point.charAt(1));
        if (x >= 0 && y >= 0 && x < width && y < height) marked.add(x * height + y);
      }
    }
    synchronized (data) {
      if (data.bestMoves == null) return List.of();
      for (MoveData move : data.bestMoves) {
        if (move == null
            || move.playouts <= 0
            || move.order < 0
            || !Double.isFinite(move.winrate)
            || move.winrate < 0
            || move.winrate > 100) continue;
        if (move.coordinate == null || !move.coordinate.matches("(?i)[a-hj-z]+[1-9][0-9]*"))
          continue;
        int rowStart = 0;
        while (rowStart < move.coordinate.length()
            && Character.isLetter(move.coordinate.charAt(rowStart))) rowStart++;
        try {
          if (Integer.parseInt(move.coordinate.substring(rowStart)) > height) continue;
        } catch (NumberFormatException ex) {
          continue;
        }
        var coords = Board.asCoordinates(move.coordinate, height);
        if (coords.isEmpty()) continue;
        int x = coords.get()[0], y = coords.get()[1];
        if (x < 0
            || y < 0
            || x >= width
            || y >= height
            || data.stones[x * height + y] != Stone.EMPTY
            || marked.contains(x * height + y)) continue;
        result.add(
            new Candidate(
                x,
                y,
                alwaysBlack && !data.blackToPlay ? 100 - move.winrate : move.winrate,
                move.order,
                move.allocationVisits()));
      }
    }
    result.sort(
        Comparator.comparingInt(Candidate::order)
            .thenComparing(Comparator.comparingInt(Candidate::visits).reversed()));
    Set<String> seen = new HashSet<>();
    return result.stream().filter(c -> seen.add(c.x() + ":" + c.y())).limit(limit).toList();
  }
}
