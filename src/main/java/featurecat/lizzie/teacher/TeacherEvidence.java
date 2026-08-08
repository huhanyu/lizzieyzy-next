package featurecat.lizzie.teacher;

import featurecat.lizzie.analysis.MoveData;
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.BoardHistoryNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;

/** Immutable KataGo evidence copied from the SGF tree before an AI request starts. */
public final class TeacherEvidence {
  static final int MAX_CANDIDATES = 3;
  static final int MAX_PV_MOVES = 12;
  static final int MAX_RANGE_POSITIONS = 40;

  private TeacherEvidence() {}

  public static Optional<Position> current(BoardHistoryNode node) {
    return position(node);
  }

  public static Range mainLine(BoardHistoryNode root, int firstMove, int lastMove) {
    if (root == null) {
      return new Range(Collections.emptyList(), 0, 0);
    }
    int normalizedFirst = Math.max(1, firstMove);
    int normalizedLast = Math.max(normalizedFirst, lastMove);
    ArrayList<Position> available = new ArrayList<>();
    BoardHistoryNode parent = root;
    while (parent != null && parent.next().isPresent()) {
      BoardHistoryNode child = parent.next().get();
      int moveNumber = child.getData().moveNumber;
      if (moveNumber >= normalizedFirst && moveNumber <= normalizedLast) {
        position(parent).ifPresent(available::add);
      }
      if (moveNumber > normalizedLast) {
        break;
      }
      parent = child;
    }
    List<Position> selected = selectKeyPositions(available, MAX_RANGE_POSITIONS);
    return new Range(selected, available.size(), Math.max(0, available.size() - selected.size()));
  }

  public static Range wholeGame(BoardHistoryNode root) {
    return mainLine(root, 1, Integer.MAX_VALUE);
  }

  static Optional<Position> position(BoardHistoryNode parent) {
    if (parent == null || parent.getData() == null) {
      return Optional.empty();
    }
    BoardData data = parent.getData();
    List<MoveData> moves = stableCopy(data.bestMoves);
    if (moves.isEmpty()) {
      return Optional.empty();
    }

    ArrayList<Candidate> candidates = new ArrayList<>();
    for (MoveData move : moves) {
      Candidate candidate = candidate(candidates.size() + 1, move);
      if (candidate != null) {
        candidates.add(candidate);
      }
      if (candidates.size() >= MAX_CANDIDATES) {
        break;
      }
    }
    if (candidates.isEmpty()) {
      return Optional.empty();
    }

    String actualMove = actualMove(parent.next().orElse(null));
    OptionalDouble actualLoss = OptionalDouble.empty();
    if (!actualMove.isEmpty()) {
      Candidate best = candidates.get(0);
      for (MoveData move : moves) {
        if (move != null
            && actualMove.equalsIgnoreCase(normalizeCoordinate(move.coordinate))
            && Double.isFinite(move.winrate)
            && Double.isFinite(best.winrate)) {
          actualLoss = OptionalDouble.of(Math.max(0.0, best.winrate - move.winrate));
          break;
        }
      }
    }

    return Optional.of(
        new Position(
            data.moveNumber,
            data.blackToPlay ? "B" : "W",
            data.getPlayouts(),
            actualMove,
            actualLoss,
            candidates));
  }

  private static Candidate candidate(int rank, MoveData move) {
    if (move == null || normalizeCoordinate(move.coordinate).isEmpty()) {
      return null;
    }
    ArrayList<String> variation = new ArrayList<>();
    if (move.variation != null) {
      for (String coordinate : move.variation) {
        String normalized = normalizeCoordinate(coordinate);
        if (!normalized.isEmpty()) {
          variation.add(normalized);
        }
        if (variation.size() >= MAX_PV_MOVES) {
          break;
        }
      }
    }
    return new Candidate(
        rank,
        normalizeCoordinate(move.coordinate),
        finiteOrNaN(move.winrate),
        finiteOrNaN(move.scoreMean),
        Math.max(0, move.playouts),
        variation);
  }

  private static String actualMove(BoardHistoryNode child) {
    if (child == null || child.getData() == null) {
      return "";
    }
    BoardData data = child.getData();
    if (data.isPassNode()) {
      return "pass";
    }
    if (data.lastMove.isEmpty()) {
      return "";
    }
    int[] move = data.lastMove.get();
    return normalizeCoordinate(Board.convertCoordinatesToName(move[0], move[1]));
  }

  private static List<MoveData> stableCopy(List<MoveData> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyList();
    }
    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        return new ArrayList<>(source);
      } catch (RuntimeException concurrentUpdate) {
        Thread.yield();
      }
    }
    return Collections.emptyList();
  }

  private static List<Position> selectKeyPositions(List<Position> positions, int limit) {
    if (positions.size() <= limit) {
      return Collections.unmodifiableList(new ArrayList<>(positions));
    }

    ArrayList<Position> ranked = new ArrayList<>(positions);
    ranked.sort(
        Comparator.comparingDouble(TeacherEvidence::importance)
            .reversed()
            .thenComparingInt(position -> position.moveNumber));
    LinkedHashSet<Position> selected = new LinkedHashSet<>();
    selected.add(positions.get(0));
    selected.add(positions.get(positions.size() - 1));
    for (Position position : ranked) {
      selected.add(position);
      if (selected.size() >= limit) {
        break;
      }
    }
    ArrayList<Position> chronological = new ArrayList<>(selected);
    chronological.sort(Comparator.comparingInt(position -> position.moveNumber));
    return Collections.unmodifiableList(chronological);
  }

  private static double importance(Position position) {
    if (position.actualWinrateLoss.isPresent()) {
      return position.actualWinrateLoss.getAsDouble();
    }
    return position.candidates.isEmpty() ? 0.0 : position.candidates.get(0).visits / 1_000_000.0;
  }

  private static String normalizeCoordinate(String coordinate) {
    return coordinate == null ? "" : coordinate.trim().toUpperCase(Locale.ROOT);
  }

  private static double finiteOrNaN(double value) {
    return Double.isFinite(value) ? value : Double.NaN;
  }

  public static final class Position {
    public final int moveNumber;
    public final String toPlay;
    public final int playouts;
    public final String actualMove;
    public final OptionalDouble actualWinrateLoss;
    public final List<Candidate> candidates;

    Position(
        int moveNumber,
        String toPlay,
        int playouts,
        String actualMove,
        OptionalDouble actualWinrateLoss,
        Collection<Candidate> candidates) {
      this.moveNumber = Math.max(0, moveNumber);
      this.toPlay = "W".equals(toPlay) ? "W" : "B";
      this.playouts = Math.max(0, playouts);
      this.actualMove = actualMove == null ? "" : actualMove;
      this.actualWinrateLoss =
          actualWinrateLoss == null ? OptionalDouble.empty() : actualWinrateLoss;
      this.candidates =
          Collections.unmodifiableList(
              new ArrayList<>(candidates == null ? List.of() : candidates));
    }
  }

  public static final class Candidate {
    public final int rank;
    public final String coordinate;
    public final double winrate;
    public final double scoreLead;
    public final int visits;
    public final List<String> variation;

    Candidate(
        int rank,
        String coordinate,
        double winrate,
        double scoreLead,
        int visits,
        Collection<String> variation) {
      this.rank = rank;
      this.coordinate = coordinate;
      this.winrate = winrate;
      this.scoreLead = scoreLead;
      this.visits = visits;
      this.variation =
          Collections.unmodifiableList(new ArrayList<>(variation == null ? List.of() : variation));
    }
  }

  public static final class Range {
    public final List<Position> positions;
    public final int analyzedPositions;
    public final int omittedPositions;

    Range(List<Position> positions, int analyzedPositions, int omittedPositions) {
      this.positions = Collections.unmodifiableList(new ArrayList<>(positions));
      this.analyzedPositions = Math.max(0, analyzedPositions);
      this.omittedPositions = Math.max(0, omittedPositions);
    }

    public boolean isEmpty() {
      return positions.isEmpty();
    }
  }
}
