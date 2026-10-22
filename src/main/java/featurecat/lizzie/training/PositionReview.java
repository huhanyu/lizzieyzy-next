package featurecat.lizzie.training;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * What the coach engine found in one position: KataGo's values for the moves it searched, and how
 * often players of the opponent's rank play each move. Leads are in points for the player to move.
 *
 * @param rootLead KataGo's score lead for the player to move
 * @param scoreStdev the HumanSL model's score standard deviation, NaN when the engine has none
 * @param candidates the searched moves, best first
 * @param humanShares the HumanSL policy: move name, such as "Q16" or "pass", to probability
 */
public record PositionReview(
    double rootLead,
    double scoreStdev,
    List<Candidate> candidates,
    Map<String, Double> humanShares) {

  /** Fewer visits than this give a move's value too much noise to judge it by. */
  static final int MIN_RELIABLE_VISITS = 16;

  /** A move KataGo searched, with its score lead for the player to move. */
  public record Candidate(String move, int visits, double scoreLead) {}

  public PositionReview {
    candidates = List.copyOf(candidates);
    humanShares = Map.copyOf(humanShares);
  }

  /** KataGo's choice: the first candidate. */
  public Optional<Candidate> best() {
    return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(0));
  }

  /**
   * The lead after {@code move} from this search, or NaN when the search did not look at it closely
   * enough, so that the position after it has to be searched instead.
   */
  public double leadAfter(String move) {
    String name = normalize(move);
    Optional<Candidate> best = best();
    if (best.isEmpty()) {
      return Double.NaN;
    }
    if (normalize(best.get().move()).equals(name)) {
      return best.get().scoreLead();
    }
    int needed = Math.max(MIN_RELIABLE_VISITS, best.get().visits() / 20);
    for (Candidate candidate : candidates) {
      if (normalize(candidate.move()).equals(name)) {
        return candidate.visits() >= needed ? candidate.scoreLead() : Double.NaN;
      }
    }
    return Double.NaN;
  }

  /** How often players of the rank play {@code move}, 0 to 1, or NaN when unknown. */
  public double humanShare(String move) {
    Double share = humanShares.get(normalize(move));
    return share == null ? (humanShares.isEmpty() ? Double.NaN : 0.0) : share;
  }

  /** The move players of the rank play most often here, or null when unknown. */
  public String commonMove() {
    String common = null;
    double highest = -1.0;
    for (Map.Entry<String, Double> share : humanShares.entrySet()) {
      if (share.getValue() > highest
          || (share.getValue() == highest && share.getKey().compareTo(common) < 0)) {
        common = share.getKey();
        highest = share.getValue();
      }
    }
    return common;
  }

  static String normalize(String move) {
    String trimmed = move == null ? "" : move.trim();
    return "pass".equalsIgnoreCase(trimmed) ? "pass" : trimmed.toUpperCase(Locale.ROOT);
  }
}
