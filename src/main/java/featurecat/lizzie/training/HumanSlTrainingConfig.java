package featurecat.lizzie.training;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/** Immutable configuration for one AI coaching game. */
public final class HumanSlTrainingConfig {
  public enum PlayerColor {
    RANDOM,
    BLACK,
    WHITE
  }

  public final TrainingMode mode;
  public final OpponentPreset opponentPreset;
  public final int rank;
  public final boolean danRank;
  public final PlayerColor playerColor;
  public final int moveTimeSeconds;
  public final int handicap;
  public final double komi;
  public final boolean fromCurrentPosition;

  private HumanSlTrainingConfig(Builder builder) {
    mode = Objects.requireNonNull(builder.mode, "mode");
    opponentPreset = Objects.requireNonNull(builder.opponentPreset, "opponentPreset");
    rank = builder.danRank ? Math.max(1, Math.min(9, builder.rank)) : Math.max(1, Math.min(20, builder.rank));
    danRank = builder.danRank;
    playerColor = Objects.requireNonNull(builder.playerColor, "playerColor");
    moveTimeSeconds = Math.max(2, builder.moveTimeSeconds);
    handicap = Math.max(0, Math.min(9, builder.handicap));
    komi = builder.komi;
    fromCurrentPosition = builder.fromCurrentPosition;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String humanSlProfile() {
    return opponentPreset.profileFor(rank, danRank);
  }

  public int analysisVisits() {
    return opponentPreset.recommendedVisits();
  }

  public int rootSymmetries() {
    return opponentPreset.rootSymmetries();
  }

  public boolean resolveHumanIsBlack() {
    if (playerColor == PlayerColor.BLACK) {
      return true;
    }
    if (playerColor == PlayerColor.WHITE) {
      return false;
    }
    return ThreadLocalRandom.current().nextBoolean();
  }

  public static final class Builder {
    private TrainingMode mode = TrainingMode.POST_GAME_REVIEW;
    private OpponentPreset opponentPreset = OpponentPreset.RANK;
    private int rank = 3;
    private boolean danRank = true;
    private PlayerColor playerColor = PlayerColor.RANDOM;
    private int moveTimeSeconds = 10;
    private int handicap;
    private double komi = 7.5;
    private boolean fromCurrentPosition;

    public Builder mode(TrainingMode value) {
      mode = value;
      return this;
    }

    public Builder opponentPreset(OpponentPreset value) {
      opponentPreset = value;
      return this;
    }

    public Builder rank(int value, boolean isDan) {
      rank = value;
      danRank = isDan;
      return this;
    }

    public Builder playerColor(PlayerColor value) {
      playerColor = value;
      return this;
    }

    public Builder moveTimeSeconds(int value) {
      moveTimeSeconds = value;
      return this;
    }

    public Builder handicap(int value) {
      handicap = value;
      return this;
    }

    public Builder komi(double value) {
      komi = value;
      return this;
    }

    public Builder fromCurrentPosition(boolean value) {
      fromCurrentPosition = value;
      return this;
    }

    public HumanSlTrainingConfig build() {
      return new HumanSlTrainingConfig(this);
    }
  }
}
