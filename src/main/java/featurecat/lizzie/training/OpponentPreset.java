package featurecat.lizzie.training;

/** User-facing HumanSL opponent styles. */
public enum OpponentPreset {
  RANK,
  MODERN_PRO,
  ONLINE_9D;

  public String profileFor(int rank, boolean dan) {
    switch (this) {
      case MODERN_PRO:
        return "proyear_2023";
      case ONLINE_9D:
        return "rank_9d";
      case RANK:
      default:
        int bounded = dan ? Math.max(1, Math.min(9, rank)) : Math.max(1, Math.min(20, rank));
        return "rank_" + bounded + (dan ? "d" : "k");
    }
  }

  public int recommendedVisits() {
    return this == MODERN_PRO ? 128 : 64;
  }

  public int rootSymmetries() {
    return this == MODERN_PRO ? 2 : 1;
  }
}
