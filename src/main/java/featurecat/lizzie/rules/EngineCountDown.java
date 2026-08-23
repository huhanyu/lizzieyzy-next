package featurecat.lizzie.rules;

import featurecat.lizzie.analysis.Leelaz;
import java.util.Locale;

public class EngineCountDown {
  public volatile boolean isPlayBlack;

  private int countDownMoves;
  private int countDownTimes;

  private int currentCountDownMoves;
  private int currentCountDownTimes;

  private int MainSeconds;
  private int countDownSeconds;

  private int currentMainSeconds;
  private int currentCountDownSeconds;

  private float MainSecondsF;
  private float countDownSecondsF;

  private float currentMainSecondsF;
  private float currentCountDownSecondsF;

  private float fischerIcrementSeconds;
  private float MainSecondsLimit;
  private float MaxSecondsPerMove;

  private Leelaz engine;
  private String color;
  private int tempTimes;
  private TimeType type;

  enum TimeType {
    Canadian_byoyomi,
    kata_None,
    kata_Absolute,
    kata_Canadian_byoyomi,
    kata_Traditional_byoyomi,
    kata_Fisher,
    kata_Fisher_capped,
  }

  private static final class TimeLeftValue {
    private final int integralSeconds;
    private final float fractionalSeconds;
    private final int moves;
    private final boolean fractional;

    private TimeLeftValue(int seconds, int moves) {
      this.integralSeconds = Math.max(0, seconds);
      this.fractionalSeconds = 0F;
      this.moves = moves;
      this.fractional = false;
    }

    private TimeLeftValue(float seconds, int moves) {
      this.integralSeconds = 0;
      this.fractionalSeconds = Math.max(0F, seconds);
      this.moves = moves;
      this.fractional = true;
    }

    private String command(String color) {
      return "time_left "
          + color
          + " "
          + (fractional
              ? String.format(Locale.ENGLISH, "%.2f", fractionalSeconds)
              : Integer.toString(integralSeconds))
          + " "
          + moves;
    }

    private void send(Leelaz engine, String color, boolean isDuringMove) {
      if (fractional) {
        engine.timeLeft(color, fractionalSeconds, moves, isDuringMove);
      } else {
        engine.timeLeft(color, integralSeconds, moves, isDuringMove);
      }
    }
  }

  public synchronized boolean setEngineCountDown(String line, Leelaz engine) {
    if (line == null || engine == null) {
      return false;
    }
    line = line.trim().toLowerCase(Locale.ENGLISH);
    String[] params = line.split("\\s+");
    int paramsLength = params.length;
    if (paramsLength >= 2) {
      if (params[0].equals("time_settings") && paramsLength == 4) {
        try {
          MainSeconds = Integer.parseInt(params[1]);
          countDownSeconds = Integer.parseInt(params[2]);
          countDownMoves = Integer.parseInt(params[3]);
          this.engine = engine;
          this.type = TimeType.Canadian_byoyomi;
          return true;
        } catch (Exception e) {
          e.printStackTrace();
          return false;
        }
      } else {
        if (params[0].equals("kata-time_settings")) {
          if (params[1].equals("none") && paramsLength == 2) {
            this.engine = engine;
            this.type = TimeType.kata_None;
            return true;
          }

          if (params[1].equals("absolute") && paramsLength == 3) {
            try {
              MainSecondsF = Float.parseFloat(params[2]);
              this.engine = engine;
              this.type = TimeType.kata_Absolute;
              return true;
            } catch (Exception e) {
              e.printStackTrace();
              return false;
            }
          }

          if (params[1].equals("canadian") && paramsLength == 5) {
            try {
              MainSecondsF = Float.parseFloat(params[2]);
              countDownSecondsF = Float.parseFloat(params[3]);
              countDownMoves = Integer.parseInt(params[4]);
              this.engine = engine;
              this.type = TimeType.kata_Canadian_byoyomi;
              return true;
            } catch (Exception e) {
              e.printStackTrace();
              return false;
            }
          }

          if (params[1].equals("byoyomi") && paramsLength == 5) {
            try {
              MainSecondsF = Float.parseFloat(params[2]);
              countDownSecondsF = Float.parseFloat(params[3]);
              countDownTimes = Integer.parseInt(params[4]);
              this.engine = engine;
              this.type = TimeType.kata_Traditional_byoyomi;
              return true;
            } catch (Exception e) {
              e.printStackTrace();
              return false;
            }
          }

          if (params[1].equals("fischer") && paramsLength == 4) {
            try {
              MainSecondsF = Float.parseFloat(params[2]);
              fischerIcrementSeconds = Float.parseFloat(params[3]);
              this.engine = engine;
              this.type = TimeType.kata_Fisher;
              return true;
            } catch (Exception e) {
              e.printStackTrace();
              return false;
            }
          }

          if (params[1].equals("fischer-capped") && paramsLength == 6) {
            try {
              MainSecondsF = Float.parseFloat(params[2]);
              fischerIcrementSeconds = Float.parseFloat(params[3]);
              MainSecondsLimit = Float.parseFloat(params[4]);
              MaxSecondsPerMove = Float.parseFloat(params[5]);
              if (MainSecondsLimit > 0) {
                if (MainSecondsLimit < MainSecondsF) return false;
              }
              this.engine = engine;
              this.type = TimeType.kata_Fisher_capped;
              return true;
            } catch (Exception e) {
              e.printStackTrace();
              return false;
            }
          }
        }
      }
    }
    return false;
  }

  public synchronized void initialize(boolean isPlayBlack) {
    this.isPlayBlack = isPlayBlack;
    color = isPlayBlack ? "B" : "W";
    if (type == TimeType.Canadian_byoyomi) {
      currentMainSeconds = MainSeconds;
      currentCountDownSeconds = countDownSeconds;
      currentCountDownMoves = countDownMoves;
    } else if (type == TimeType.kata_Canadian_byoyomi) {
      currentMainSecondsF = MainSecondsF;
      currentCountDownSecondsF = countDownSecondsF;
      currentCountDownMoves = countDownMoves;
    } else if (type == TimeType.kata_Traditional_byoyomi) {
      currentMainSecondsF = MainSecondsF;
      currentCountDownSecondsF = countDownSecondsF;
      currentCountDownTimes = countDownTimes;
    } else if (type == TimeType.kata_Fisher) {
      currentMainSecondsF = MainSecondsF;
    } else if (type == TimeType.kata_Fisher_capped) {
      currentMainSecondsF = MainSecondsF;
    } else if (type == TimeType.kata_Absolute) {
      currentMainSecondsF = MainSecondsF;
    }
  }

  /**
   * Advances this clock to the next move boundary and returns the exact GTP synchronization
   * command. The caller owns command delivery; no engine I/O occurs while the clock monitor is
   * held.
   */
  public synchronized String claimTimeLeftCommand() {
    TimeLeftValue value = claimTimeLeftValue();
    return value == null ? null : value.command(color);
  }

  private TimeLeftValue claimTimeLeftValue() {
    if (engine == null || color == null || type == null || type == TimeType.kata_None) return null;
    if (type == TimeType.Canadian_byoyomi) {
      if (currentMainSeconds <= 0) {
        currentCountDownMoves--;
        if (currentCountDownMoves <= 0) {
          currentCountDownMoves = countDownMoves;
          currentCountDownSeconds = countDownSeconds;
        }
        return new TimeLeftValue(currentCountDownSeconds, currentCountDownMoves);
      } else {
        return new TimeLeftValue(currentMainSeconds, 0);
      }
    } else if (type == TimeType.kata_Canadian_byoyomi) {
      if (currentMainSecondsF <= 0) {
        currentCountDownMoves--;
        if (currentCountDownMoves <= 0) {
          currentCountDownMoves = countDownMoves;
          currentCountDownSecondsF = countDownSecondsF;
        }
        return new TimeLeftValue(currentCountDownSecondsF, currentCountDownMoves);
      } else {
        return new TimeLeftValue(currentMainSecondsF, 0);
      }
    } else if (type == TimeType.kata_Traditional_byoyomi) {
      if (currentMainSecondsF <= 0) {
        currentCountDownSecondsF = countDownSecondsF;
        return new TimeLeftValue(currentCountDownSecondsF, currentCountDownTimes);
      } else {
        return new TimeLeftValue(currentMainSecondsF, 0);
      }
    } else if (type == TimeType.kata_Fisher) {
      currentMainSecondsF = currentMainSecondsF + fischerIcrementSeconds;
      return new TimeLeftValue(currentMainSecondsF, 0);
    } else if (type == TimeType.kata_Fisher_capped) {
      currentMainSecondsF = currentMainSecondsF + fischerIcrementSeconds;
      if (MainSecondsLimit > 0)
        currentMainSecondsF = Math.min(currentMainSecondsF, MainSecondsLimit);
      float thisMoveTime = currentMainSecondsF;
      if (MaxSecondsPerMove > 0) thisMoveTime = Math.min(MaxSecondsPerMove, thisMoveTime);
      return new TimeLeftValue(thisMoveTime, 0);
    } else if (type == TimeType.kata_Absolute) {
      return new TimeLeftValue(currentMainSecondsF, 0);
    }
    return null;
  }

  /** Legacy non-engine-game delivery path. Engine games use the exact post-move permit. */
  public void sendTimeLeft(boolean isDuringMove) {
    TimeLeftValue value;
    Leelaz target;
    String targetColor;
    synchronized (this) {
      value = claimTimeLeftValue();
      target = engine;
      targetColor = color;
    }
    if (value == null || target == null || targetColor == null) {
      return;
    }
    value.send(target, targetColor, isDuringMove);
  }

  /** Returns whether this frozen clock belongs to the expected participant and color. */
  public synchronized boolean belongsTo(Leelaz expectedEngine, boolean expectedBlack) {
    return engine == expectedEngine
        && color != null
        && isPlayBlack == expectedBlack
        && color.equals(expectedBlack ? "B" : "W");
  }

  public synchronized void countDownCentiseconds() {
    if (type == TimeType.kata_None) {
      return;
    } else if (type == TimeType.Canadian_byoyomi) {
      tempTimes++;
      if (tempTimes >= 100) {
        tempTimes = 0;
        if (currentMainSeconds > 0) {
          currentMainSeconds--;
        } else if (currentCountDownSeconds > 0) {
          currentCountDownSeconds--;
        }
      }
    } else if (type == TimeType.kata_Canadian_byoyomi) {
      if (currentMainSecondsF > 0) {
        currentMainSecondsF = currentMainSecondsF - 0.01F;
      } else {
        if (currentCountDownSecondsF > 0) {
          currentCountDownSecondsF = currentCountDownSecondsF - 0.01F;
        }
      }
    } else if (type == TimeType.kata_Traditional_byoyomi) {
      if (currentMainSecondsF > 0) {
        currentMainSecondsF = currentMainSecondsF - 0.01F;
      } else {
        if (currentCountDownSecondsF > 0) {
          currentCountDownSecondsF = currentCountDownSecondsF - 0.01F;
        } else if (currentCountDownTimes > 0) {
          currentCountDownTimes--;
          // A tick may race terminal retirement. It must only advance private memory; the next
          // exact post-move turn (if any) owns the sole time_left write.
          currentCountDownSecondsF = countDownSecondsF;
        }
      }
    } else if (type == TimeType.kata_Fisher) {
      if (currentMainSecondsF > 0) {
        currentMainSecondsF = currentMainSecondsF - 0.01F;
      }
    } else if (type == TimeType.kata_Fisher_capped) {
      if (currentMainSecondsF > 0) {
        currentMainSecondsF = currentMainSecondsF - 0.01F;
      }
    }
    if (type == TimeType.kata_Absolute) {
      if (currentMainSecondsF > 0) {
        currentMainSecondsF = currentMainSecondsF - 0.01F;
      } else currentMainSecondsF = 0;
    }
  }
}
