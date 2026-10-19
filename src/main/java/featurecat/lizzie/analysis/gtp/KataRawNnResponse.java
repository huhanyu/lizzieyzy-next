package featurecat.lizzie.analysis.gtp;

import java.util.ArrayList;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.BooleanSupplier;

/**
 * Incremental reader for one KataGo {@code kata-raw-nn} response, fed one output line at a time.
 *
 * <p>The response is a {@code = symmetry N} header, scalar lines such as {@code whiteWin 0.42},
 * then a {@code policy} block and a {@code whiteOwnership} block of one line per board row. This
 * class only parses: it has no engine or UI state, so the format is tested in isolation (see the
 * engine-session design in docs/specs).
 */
public final class KataRawNnResponse {
  private final int width;
  private final int height;
  private final ArrayList<Double> policyPermille = new ArrayList<>();
  private final ArrayList<Double> ownership = new ArrayList<>();
  private boolean readingPolicy;
  private boolean readingOwnership;
  private boolean completeReported;

  public KataRawNnResponse(int width, int height) {
    this.width = width;
    this.height = height;
  }

  /** The symmetry announced by a {@code = symmetry N} response header, if this is one. */
  public static OptionalInt headerSymmetry(String line) {
    if (line == null || !line.startsWith("=")) {
      return OptionalInt.empty();
    }
    String[] params = line.trim().split(" ");
    if (params.length == 3 && params[1].startsWith("symmetry")) {
      try {
        return OptionalInt.of(Integer.parseInt(params[2]));
      } catch (NumberFormatException malformed) {
        return OptionalInt.empty();
      }
    }
    return OptionalInt.empty();
  }

  /** The value of a scalar line such as {@code whiteWin 0.42}, if {@code line} is that line. */
  public static OptionalDouble scalar(String line, String name) {
    if (line == null || !line.startsWith(name)) {
      return OptionalDouble.empty();
    }
    String[] params = line.trim().split(" ");
    if (params.length < 2) {
      return OptionalDouble.empty();
    }
    try {
      return OptionalDouble.of(Double.parseDouble(params[1]));
    } catch (NumberFormatException malformed) {
      return OptionalDouble.empty();
    }
  }

  /**
   * Feeds one body line. Policy rows are stored in permille, ownership rows for the side to play
   * (white ownership negated when {@code blackToPlay} is true, evaluated per row). Lines whose
   * value count is not the board width are ignored; unparsable values count as 0.
   *
   * @return true exactly once, when the last ownership row arrives
   */
  public boolean accept(String line, BooleanSupplier blackToPlay) {
    if (line.startsWith("policyPass")) {
      // The pass probability is not a board row (on a 2-wide board it used to be read as one).
      return false;
    }
    if (line.startsWith("policy")) {
      readingPolicy = true;
      readingOwnership = false;
    }
    if (line.startsWith("whiteOwnership")) {
      readingPolicy = false;
      readingOwnership = true;
    }
    if (readingPolicy) {
      String[] values = line.trim().split("\\s+");
      if (values.length == width) {
        for (String value : values) {
          policyPermille.add(parseOrZero(value) * 1000.0);
        }
      }
    }
    if (readingOwnership) {
      String[] values = line.trim().split("\\s+");
      if (values.length == width) {
        boolean negate = blackToPlay.getAsBoolean();
        for (String value : values) {
          double whiteOwnership = parseOrZero(value);
          ownership.add(negate ? -whiteOwnership : whiteOwnership);
        }
      }
      if (!completeReported && ownership.size() == width * height) {
        completeReported = true;
        readingOwnership = false;
        return true;
      }
    }
    return false;
  }

  /** Policy so far, in permille, row by row; the list keeps filling as rows arrive. */
  public ArrayList<Double> policyPermille() {
    return policyPermille;
  }

  /** Ownership so far for the side to play; the list keeps filling as rows arrive. */
  public ArrayList<Double> ownershipForSideToPlay() {
    return ownership;
  }

  private static double parseOrZero(String value) {
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException malformed) {
      return 0.0;
    }
  }
}
