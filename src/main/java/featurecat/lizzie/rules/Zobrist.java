package featurecat.lizzie.rules;

import java.util.Random;

/** Used to maintain zobrist hashes for ko detection */
public class Zobrist {
  private static long[] blackZobrist;
  private static long[] whiteZobrist;

  /** Opaque identity-preserving snapshot of the coordinate hash tables. */
  static final class TableSnapshot {
    private final long[] black;
    private final long[] white;

    private TableSnapshot(long[] black, long[] white) {
      this.black = black;
      this.white = white;
    }
  }

  // initialize zobrist hashing
  static {
    init();
  }

  // hash to be used to compare two board states
  private long zhash;

  public Zobrist() {
    zhash = 0;
  }

  public Zobrist(long zhash) {
    this.zhash = zhash;
  }

  /**
   * @return a copy of this zobrist
   */
  public Zobrist clone() {
    return new Zobrist(zhash);
  }

  public static synchronized void init() {

    Random random = new Random();
    blackZobrist = new long[Board.boardWidth * Board.boardHeight];
    whiteZobrist = new long[Board.boardWidth * Board.boardHeight];

    for (int i = 0; i < blackZobrist.length; i++) {
      blackZobrist[i] = random.nextLong();
      whiteZobrist[i] = random.nextLong();
    }
  }

  static synchronized TableSnapshot captureTables() {
    return new TableSnapshot(blackZobrist, whiteZobrist);
  }

  static synchronized void restoreTables(TableSnapshot snapshot) {
    if (snapshot == null || snapshot.black == null || snapshot.white == null) {
      throw new IllegalArgumentException("Missing Zobrist table snapshot.");
    }
    blackZobrist = snapshot.black;
    whiteZobrist = snapshot.white;
  }

  /**
   * Call this method to alter the current zobrist hash for this stone
   *
   * @param x x coordinate -- must be valid
   * @param y y coordinate -- must be valid
   * @param color color of the stone to alter (for adding or removing a stone color)
   */
  public void toggleStone(int x, int y, Stone color) {
    switch (color) {
      case BLACK:
        zhash ^= blackZobrist[Board.getIndex(x, y)];
        break;
      case WHITE:
        zhash ^= whiteZobrist[Board.getIndex(x, y)];
        break;
      default:
    }
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Zobrist && (((Zobrist) o).zhash == zhash);
  }

  @Override
  public int hashCode() {
    return (int) zhash;
  }

  @Override
  public String toString() {
    return "" + zhash;
  }
}
