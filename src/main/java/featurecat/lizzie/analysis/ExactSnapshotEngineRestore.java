package featurecat.lizzie.analysis;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.BoardHistoryList;
import featurecat.lizzie.rules.BoardHistoryNode;
import featurecat.lizzie.rules.Stone;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Restores an engine from one immutable snapshot plan. */
public final class ExactSnapshotEngineRestore {
  private static final long DELETE_RETRY_DELAY_MILLIS = 250L;
  private static final int DELETE_RETRY_LIMIT = 20;
  private static final int SGF_EXTENDED_COORD_THRESHOLD = 52;
  private static final String SGF_COORD_ALPHABET =
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static final ScheduledExecutorService DELETE_EXECUTOR =
      Executors.newSingleThreadScheduledExecutor(ExactSnapshotEngineRestore::newCleanupThread);

  private ExactSnapshotEngineRestore() {}

  public static Optional<PreparedRestore> prepare(
      Leelaz engine, BoardHistoryNode target, boolean resumePonder) {
    return RestorePlan.capture(
            engine, target, resumePonder, null, Leelaz.ExactSnapshotRestoreOwner.ORDINARY, null)
        .map(PreparedRestore::new);
  }

  public static Optional<PreparedRestore> prepare(
      Leelaz engine, BoardHistoryNode target, boolean resumePonder, double komi) {
    return RestorePlan.capture(
            engine, target, resumePonder, komi, Leelaz.ExactSnapshotRestoreOwner.ORDINARY, null)
        .map(PreparedRestore::new);
  }

  static LifecycleRestoreHandoff prepareLifecycleHandoff(
      Leelaz existingReservationEngine, Leelaz targetEngine, Leelaz proposedMirrorEngine) {
    return new LifecycleRestoreHandoff(
        existingReservationEngine, targetEngine, proposedMirrorEngine);
  }

  private static Leelaz freezeLifecycleMirror(Leelaz targetEngine, Leelaz proposedMirrorEngine) {
    RestorePlan.requireEngine(targetEngine);
    if (proposedMirrorEngine == null) {
      return null;
    }
    if (targetEngine == proposedMirrorEngine) {
      throw new IllegalArgumentException("proposedMirrorEngine");
    }
    Leelaz primary = Lizzie.leelaz;
    Leelaz secondary = Lizzie.leelaz2;
    boolean currentPair =
        (targetEngine == primary && proposedMirrorEngine == secondary)
            || (targetEngine == secondary && proposedMirrorEngine == primary);
    boolean futurePair =
        targetEngine != primary
            && targetEngine != secondary
            && (proposedMirrorEngine == primary || proposedMirrorEngine == secondary);
    return currentPair || futurePair ? proposedMirrorEngine : null;
  }

  static Optional<PreparedRestore> prepareForForeground(
      Leelaz engine, BoardHistoryNode target, Double komi, Object owner) {
    return RestorePlan.capture(
            engine, target, false, komi, Leelaz.ExactSnapshotRestoreOwner.FOREGROUND, owner)
        .map(PreparedRestore::new);
  }

  static PreparedRestore prepareForReadBoardGma(Leelaz engine, BoardData snapshotData) {
    return new PreparedRestore(
        RestorePlan.capture(engine, snapshotData, Leelaz.ExactSnapshotRestoreOwner.READ_BOARD_GMA));
  }

  static PreparedRestore prepareForReadBoardGma(
      Leelaz engine, BoardData snapshotData, double komi) {
    return new PreparedRestore(
        RestorePlan.capture(
            engine, snapshotData, Leelaz.ExactSnapshotRestoreOwner.READ_BOARD_GMA, komi));
  }

  public static PreparedRestore prepareCurrentPosition(Leelaz engine, BoardData positionData) {
    return new PreparedRestore(
        RestorePlan.capture(engine, positionData, Leelaz.ExactSnapshotRestoreOwner.ORDINARY));
  }

  static PreparedRestore prepareCurrentPosition(
      Leelaz engine, BoardData positionData, double komi) {
    return new PreparedRestore(
        RestorePlan.capture(engine, positionData, Leelaz.ExactSnapshotRestoreOwner.ORDINARY, komi));
  }

  private static BoardData materializeCurrentPosition(BoardData sourceData) {
    if (sourceData == null) {
      throw new IllegalArgumentException("sourceData");
    }
    BoardData snapshot =
        BoardData.snapshot(
            sourceData.stones.clone(),
            Optional.empty(),
            Stone.EMPTY,
            sourceData.blackToPlay,
            sourceData.zobrist == null ? null : sourceData.zobrist.clone(),
            sourceData.moveNumber,
            sourceData.moveNumberList == null ? null : sourceData.moveNumberList.clone(),
            sourceData.blackCaptures,
            sourceData.whiteCaptures,
            sourceData.winrate,
            sourceData.getPlayouts());
    snapshot.moveMNNumber = sourceData.moveMNNumber;
    snapshot.comment = sourceData.comment;
    snapshot.komi = sourceData.komi;
    snapshot.setProperties(sourceData.getProperties());
    int[] boardSize = resolveSnapshotBoardSize(sourceData);
    snapshot.addProperty("SZ", formatBoardSizeTag(boardSize[0], boardSize[1]));
    return snapshot;
  }

  private static Completion execute(RestorePlan plan, boolean clearCapturedTargets) {
    if (clearCapturedTargets) {
      for (Leelaz targetEngine : plan.targetEngines) {
        sendCapturedRestoreCommand(plan, targetEngine, "clear_board", "preclear");
      }
    }
    Path sgfFile = writeSnapshotSgf(plan);
    RestoreLifecycle lifecycle = new RestoreLifecycle(sgfFile);
    try {
      try {
        plan.engine.withExactSnapshotRestoreAdmission(
            plan.admission,
            () ->
                plan.engine.loadSgfForExactSnapshotRestore(
                    sgfFile,
                    plan.mirrorEngine,
                    plan.admission,
                    lifecycle::onLoadSgfConsumed,
                    lifecycle::onLoadDispatchStarted));
      } catch (RuntimeException failure) {
        lifecycle.failBeforeLoadDispatch();
        throw failure;
      }
      for (TailAction action : plan.tail) {
        for (Leelaz targetEngine : plan.targetEngines) {
          sendCapturedRestoreCommand(plan, targetEngine, action.command, "tail");
        }
      }
      for (Leelaz targetEngine : plan.targetEngines) {
        targetEngine.width = plan.boardWidth;
        targetEngine.height = plan.boardHeight;
        if (plan.komi != null) {
          targetEngine.komi = plan.komi.floatValue();
        }
      }
      return new Completion(plan.resumePonder);
    } finally {
      lifecycle.finishTailReplay();
    }
  }

  private static void sendCapturedRestoreCommand(
      RestorePlan plan, Leelaz target, String command, String phase) {
    final RuntimeException[] failure = new RuntimeException[1];
    target.withExactSnapshotRestoreAdmission(
        plan.admission,
        () -> {
          if (!target.sendCommandToCapturedRestoreTarget(command, plan.admission)) {
            failure[0] =
                new IllegalStateException(
                    "Exact snapshot restore " + phase + " command was rejected: " + command);
          }
        });
    if (failure[0] != null) {
      throw failure[0];
    }
  }

  private static Path writeSnapshotSgf(RestorePlan plan) {
    Path sgfFile = null;
    try {
      sgfFile = Files.createTempFile("lizzie-snapshot-", ".sgf");
      String sgf = buildSnapshotSgf(plan);
      Files.writeString(sgfFile, sgf);
      if (TrialDiag.ENABLED) {
        System.out.println("[trial-sgf] " + sgf);
      }
      return sgfFile;
    } catch (IOException ex) {
      deleteFailedSnapshotFile(sgfFile, ex);
      throw new IllegalStateException("Failed to build snapshot SGF for engine restore", ex);
    } catch (RuntimeException ex) {
      deleteFailedSnapshotFile(sgfFile, ex);
      throw ex;
    }
  }

  private static void deleteFailedSnapshotFile(Path sgfFile, Throwable failure) {
    if (sgfFile == null) {
      return;
    }
    try {
      Files.deleteIfExists(sgfFile);
    } catch (IOException cleanupFailure) {
      failure.addSuppressed(cleanupFailure);
      deleteSnapshotFile(sgfFile, 0);
    }
  }

  private static String buildSnapshotSgf(RestorePlan plan) {
    StringBuilder builder = new StringBuilder();
    builder.append("(;FF[4]GM[1]CA[UTF-8]");
    builder.append("SZ[").append(formatBoardSizeTag(plan.boardWidth, plan.boardHeight)).append(']');
    if (plan.komi != null) {
      builder.append("KM[").append(formatKomi(plan.komi)).append(']');
    }
    builder.append("PL[").append(plan.snapshotData.blackToPlay ? "B" : "W").append(']');
    appendStones(
        builder, plan.snapshotData.stones, Stone.BLACK, "AB", plan.boardWidth, plan.boardHeight);
    appendStones(
        builder, plan.snapshotData.stones, Stone.WHITE, "AW", plan.boardWidth, plan.boardHeight);
    builder.append(')');
    return builder.toString();
  }

  private static void appendStones(
      StringBuilder builder,
      Stone[] stones,
      Stone targetColor,
      String propertyName,
      int boardWidth,
      int boardHeight) {
    for (int index = 0; index < stones.length; index++) {
      if (stones[index] != targetColor) {
        continue;
      }
      int[] coord = toBoardCoord(index, boardHeight);
      builder
          .append(propertyName)
          .append('[')
          .append(formatSgfCoord(coord[0], coord[1], boardWidth, boardHeight))
          .append(']');
    }
  }

  private static String formatKomi(double komi) {
    return String.format(java.util.Locale.US, "%.1f", komi);
  }

  private static Double captureKomi(Leelaz engine, BoardData snapshotData) {
    if (snapshotData != null && snapshotData.komi != -999) {
      return snapshotData.komi;
    }
    if (engine != null && !Float.isNaN(engine.komi)) {
      return (double) engine.komi;
    }
    return null;
  }

  private static Double captureHistoryKomi() {
    Board board = Lizzie.board;
    if (board == null) {
      return null;
    }
    BoardHistoryList history = board.getHistory();
    if (history == null || history.getGameInfo() == null) {
      return null;
    }
    return history.getGameInfo().getKomi();
  }

  private static int[] resolveSnapshotBoardSize(BoardData snapshotData) {
    int boardArea = snapshotData.stones == null ? 0 : snapshotData.stones.length;
    int[] boardSizeFromProperty = parseBoardSizeTagValue(snapshotData.getProperty("SZ"));
    if (boardSizeFromProperty != null) {
      int parsedBoardArea = boardSizeFromProperty[0] * boardSizeFromProperty[1];
      if (boardArea == 0 || parsedBoardArea == boardArea) {
        return boardSizeFromProperty;
      }
    }
    if (boardArea <= 0) {
      throw new IllegalStateException("Snapshot data does not contain board stones.");
    }
    int[] boardSizeFromCurrentBoard = resolveBoardSizeFromCurrentBoard(boardArea);
    if (boardSizeFromCurrentBoard != null) {
      return boardSizeFromCurrentBoard;
    }
    return inferBoardSizeFromArea(boardArea);
  }

  private static int[] resolveBoardSizeFromCurrentBoard(int boardArea) {
    int currentBoardWidth = Board.boardWidth;
    int currentBoardHeight = Board.boardHeight;
    if (currentBoardWidth <= 0 || currentBoardHeight <= 0) {
      return null;
    }
    if (currentBoardWidth * currentBoardHeight != boardArea) {
      return null;
    }
    return new int[] {currentBoardWidth, currentBoardHeight};
  }

  private static int[] parseBoardSizeTagValue(String rawBoardSizeValue) {
    if (rawBoardSizeValue == null) {
      return null;
    }
    String boardSizeValue = rawBoardSizeValue.split(",")[0].trim();
    if (boardSizeValue.isEmpty()) {
      return null;
    }
    int split = boardSizeValue.indexOf(':');
    try {
      if (split >= 0) {
        int boardWidth = Integer.parseInt(boardSizeValue.substring(0, split));
        int boardHeight = Integer.parseInt(boardSizeValue.substring(split + 1));
        if (boardWidth > 0 && boardHeight > 0) {
          return new int[] {boardWidth, boardHeight};
        }
        return null;
      }
      int boardSize = Integer.parseInt(boardSizeValue);
      if (boardSize <= 0) {
        return null;
      }
      return new int[] {boardSize, boardSize};
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static int[] inferBoardSizeFromArea(int boardArea) {
    int boardHeight = (int) Math.ceil(Math.sqrt(boardArea));
    while (boardHeight <= boardArea && boardArea % boardHeight != 0) {
      boardHeight++;
    }
    if (boardHeight > boardArea) {
      return new int[] {boardArea, 1};
    }
    int boardWidth = boardArea / boardHeight;
    return new int[] {boardWidth, boardHeight};
  }

  private static int[] toBoardCoord(int index, int boardHeight) {
    int y = index % boardHeight;
    int x = (index - y) / boardHeight;
    return new int[] {x, y};
  }

  private static String formatSgfCoord(int x, int y, int boardWidth, int boardHeight) {
    if (boardWidth >= SGF_EXTENDED_COORD_THRESHOLD || boardHeight >= SGF_EXTENDED_COORD_THRESHOLD) {
      return x + "_" + y;
    }
    return "" + SGF_COORD_ALPHABET.charAt(x) + SGF_COORD_ALPHABET.charAt(y);
  }

  private static String formatBoardSizeTag(int boardWidth, int boardHeight) {
    return boardWidth == boardHeight ? String.valueOf(boardWidth) : boardWidth + ":" + boardHeight;
  }

  private static void scheduleDelete(Path sgfFile, int attempt) {
    DELETE_EXECUTOR.schedule(
        () -> deleteSnapshotFile(sgfFile, attempt),
        DELETE_RETRY_DELAY_MILLIS,
        TimeUnit.MILLISECONDS);
  }

  private static void deleteSnapshotFile(Path sgfFile, int attempt) {
    try {
      Files.deleteIfExists(sgfFile);
      return;
    } catch (IOException ex) {
      if (attempt + 1 >= DELETE_RETRY_LIMIT) {
        sgfFile.toFile().deleteOnExit();
        return;
      }
    }
    scheduleDelete(sgfFile, attempt + 1);
  }

  private static Thread newCleanupThread(Runnable runnable) {
    Thread thread = new Thread(runnable, "lizzie-snapshot-sgf-cleanup");
    thread.setDaemon(true);
    return thread;
  }

  public static final class Completion {
    private final boolean resumePonder;

    private Completion(boolean resumePonder) {
      this.resumePonder = resumePonder;
    }

    public boolean shouldResumePonder() {
      return resumePonder;
    }
  }

  public static final class PreparedRestore {
    private final RestorePlan plan;
    private final AtomicBoolean executed = new AtomicBoolean(false);

    private PreparedRestore(RestorePlan plan) {
      this.plan = plan;
    }

    public Completion execute() {
      return execute(false);
    }

    public Completion executeAfterCapturedTargetClear() {
      return execute(true);
    }

    private Completion execute(boolean clearCapturedTargets) {
      if (!executed.compareAndSet(false, true)) {
        throw new IllegalStateException("Exact snapshot restore has already been executed");
      }
      return ExactSnapshotEngineRestore.execute(plan, clearCapturedTargets);
    }

    OptionalDouble capturedKomi() {
      return plan.komi == null ? OptionalDouble.empty() : OptionalDouble.of(plan.komi);
    }
  }

  static final class LifecycleRestoreHandoff {
    private final Leelaz existingReservationEngine;
    private final Leelaz targetEngine;
    private final Leelaz mirrorEngine;
    private Leelaz.ExactSnapshotRestoreAdmission rootReplayAdmission;
    private final AtomicBoolean rootReplayExecuted = new AtomicBoolean(false);

    private LifecycleRestoreHandoff(
        Leelaz existingReservationEngine, Leelaz targetEngine, Leelaz proposedMirrorEngine) {
      this.targetEngine = targetEngine;
      this.mirrorEngine = freezeLifecycleMirror(targetEngine, proposedMirrorEngine);
      this.existingReservationEngine =
          normalizeExistingReservationEngine(
              existingReservationEngine, targetEngine, proposedMirrorEngine, mirrorEngine);
    }

    private static Leelaz normalizeExistingReservationEngine(
        Leelaz existingReservationEngine,
        Leelaz targetEngine,
        Leelaz proposedMirrorEngine,
        Leelaz mirrorEngine) {
      if (existingReservationEngine == null) {
        return null;
      }
      if (existingReservationEngine == proposedMirrorEngine && mirrorEngine == null) {
        return null;
      }
      if (existingReservationEngine == mirrorEngine && existingReservationEngine != targetEngine) {
        throw new IllegalArgumentException(
            "existingReservationEngine must not be the restore mirror");
      }
      if (existingReservationEngine == targetEngine
          || existingReservationEngine == Lizzie.leelaz
          || existingReservationEngine == Lizzie.leelaz2) {
        return existingReservationEngine;
      }
      throw new IllegalArgumentException("existingReservationEngine");
    }

    Optional<PreparedRestore> prepare(BoardHistoryNode target, boolean resumePonder, double komi) {
      Optional<PreparedRestore> preparedRestore =
          RestorePlan.capture(
                  targetEngine,
                  mirrorEngine,
                  target,
                  resumePonder,
                  komi,
                  Leelaz.ExactSnapshotRestoreOwner.LIFECYCLE,
                  this,
                  mirrorEngine != null && mirrorEngine == existingReservationEngine)
              .map(PreparedRestore::new);
      if (preparedRestore.isEmpty()) {
        prepareRootReplay();
      }
      return preparedRestore;
    }

    void prepareRootReplay() {
      if (rootReplayAdmission == null) {
        rootReplayAdmission =
            targetEngine.captureExactSnapshotRestoreAdmission(
                Leelaz.ExactSnapshotRestoreOwner.LIFECYCLE,
                this,
                mirrorEngine,
                mirrorEngine != null && mirrorEngine == existingReservationEngine);
      }
    }

    void executeRootReplay(Runnable replay) {
      if (rootReplayAdmission == null) {
        throw new IllegalStateException("Lifecycle root replay was not prepared");
      }
      if (!rootReplayExecuted.compareAndSet(false, true)) {
        throw new IllegalStateException("Lifecycle root replay has already been executed");
      }
      targetEngine.requireExactSnapshotRestoreAdmission(rootReplayAdmission);
      if (mirrorEngine != null) {
        mirrorEngine.requireExactSnapshotRestoreAdmission(rootReplayAdmission);
      }
      targetEngine.withExactSnapshotRestoreAdmission(
          rootReplayAdmission,
          () -> {
            if (mirrorEngine == null) {
              replay.run();
            } else {
              mirrorEngine.withExactSnapshotRestoreAdmission(rootReplayAdmission, replay);
            }
          });
    }

    Leelaz existingReservationEngine() {
      return existingReservationEngine;
    }

    Leelaz targetEngine() {
      return targetEngine;
    }

    Leelaz mirrorEngine() {
      return mirrorEngine;
    }

    boolean includesReservationEngine(Leelaz engine) {
      return engine != null && (engine == existingReservationEngine || engine == targetEngine);
    }
  }

  private static final class RestorePlan {
    private final Leelaz engine;
    private final Leelaz mirrorEngine;
    private final List<Leelaz> targetEngines;
    private final BoardData snapshotData;
    private final int boardWidth;
    private final int boardHeight;
    private final Double komi;
    private final List<TailAction> tail;
    private final boolean resumePonder;
    private final Leelaz.ExactSnapshotRestoreAdmission admission;

    private RestorePlan(
        Leelaz engine,
        Leelaz mirrorEngine,
        BoardData snapshotData,
        List<TailAction> tail,
        boolean resumePonder,
        Double komiOverride,
        Leelaz.ExactSnapshotRestoreAdmission admission) {
      this.engine = engine;
      this.mirrorEngine = mirrorEngine;
      this.targetEngines = mirrorEngine == null ? List.of(engine) : List.of(engine, mirrorEngine);
      this.snapshotData = snapshotData.clone();
      int[] boardSize = resolveSnapshotBoardSize(this.snapshotData);
      this.boardWidth = boardSize[0];
      this.boardHeight = boardSize[1];
      this.komi = komiOverride == null ? captureKomi(engine, this.snapshotData) : komiOverride;
      this.tail = List.copyOf(tail);
      this.resumePonder = resumePonder;
      this.admission = admission;
    }

    private static Optional<RestorePlan> capture(
        Leelaz engine, BoardHistoryNode target, boolean resumePonder) {
      return capture(
          engine,
          target,
          resumePonder,
          null,
          Leelaz.ExactSnapshotRestoreOwner.ORDINARY,
          null,
          false);
    }

    private static Optional<RestorePlan> capture(
        Leelaz engine,
        BoardHistoryNode target,
        boolean resumePonder,
        Double komiOverride,
        Leelaz.ExactSnapshotRestoreOwner owner,
        Object ownerIdentity) {
      return capture(engine, target, resumePonder, komiOverride, owner, ownerIdentity, false);
    }

    private static Optional<RestorePlan> capture(
        Leelaz engine,
        BoardHistoryNode target,
        boolean resumePonder,
        Double komiOverride,
        Leelaz.ExactSnapshotRestoreOwner owner,
        Object ownerIdentity,
        boolean mirrorLifecycleOwnedByOperation) {
      return capture(
          engine,
          captureMirrorEngine(engine),
          target,
          resumePonder,
          komiOverride,
          owner,
          ownerIdentity,
          mirrorLifecycleOwnedByOperation);
    }

    private static Optional<RestorePlan> capture(
        Leelaz engine,
        Leelaz mirrorEngine,
        BoardHistoryNode target,
        boolean resumePonder,
        Double komiOverride,
        Leelaz.ExactSnapshotRestoreOwner owner,
        Object ownerIdentity,
        boolean mirrorLifecycleOwnedByOperation) {
      requireEngine(engine);
      if (target == null) {
        throw new IllegalArgumentException("target");
      }
      SnapshotAnchor anchor = findSnapshotAnchor(target);
      if (anchor == null) {
        return Optional.empty();
      }
      BoardData snapshotData =
          anchor.data.isSnapshotNode() ? anchor.data : materializeCurrentPosition(anchor.data);
      int[] boardSize = resolveSnapshotBoardSize(snapshotData);
      List<TailAction> tail = captureTail(target, anchor.node, boardSize[0], boardSize[1]);
      Leelaz.ExactSnapshotRestoreAdmission admission =
          engine.captureExactSnapshotRestoreAdmission(
              owner, ownerIdentity, mirrorEngine, mirrorLifecycleOwnedByOperation);
      Double capturedKomi = komiOverride == null ? captureHistoryKomi() : komiOverride;
      return Optional.of(
          new RestorePlan(
              engine, mirrorEngine, snapshotData, tail, resumePonder, capturedKomi, admission));
    }

    private static RestorePlan capture(
        Leelaz engine, BoardData snapshotData, Leelaz.ExactSnapshotRestoreOwner owner) {
      return capture(engine, snapshotData, owner, null);
    }

    private static RestorePlan capture(
        Leelaz engine,
        BoardData sourceData,
        Leelaz.ExactSnapshotRestoreOwner owner,
        Double komiOverride) {
      requireEngine(engine);
      if (sourceData == null) {
        throw new IllegalArgumentException("positionData");
      }
      BoardData snapshotData =
          sourceData.isSnapshotNode() ? sourceData : materializeCurrentPosition(sourceData);
      Leelaz mirrorEngine = captureMirrorEngine(engine);
      return new RestorePlan(
          engine,
          mirrorEngine,
          snapshotData,
          Collections.emptyList(),
          engine.isPonderingOrWasPonderingBeforeTracking(),
          komiOverride,
          engine.captureExactSnapshotRestoreAdmission(owner, null, mirrorEngine));
    }

    private static void requireEngine(Leelaz engine) {
      if (engine == null) {
        throw new IllegalArgumentException("engine");
      }
    }

    private static SnapshotAnchor findSnapshotAnchor(BoardHistoryNode target) {
      for (BoardHistoryNode node = target; node != null; node = node.previous().orElse(null)) {
        BoardData data = node.getData();
        if (data != null && (node.hasRemovedStone() || isUsableSnapshotAnchor(node, data))) {
          return new SnapshotAnchor(node, data);
        }
      }
      return null;
    }

    private static boolean isUsableSnapshotAnchor(BoardHistoryNode node, BoardData data) {
      if (!data.isSnapshotNode()) {
        return false;
      }
      if (node.previous().isPresent()
          || data.moveNumber > 0
          || data.lastMove.isPresent()
          || !data.blackToPlay) {
        return true;
      }
      for (Stone stone : data.stones) {
        if (stone.isBlack() || stone.isWhite()) {
          return true;
        }
      }
      return false;
    }

    private static Leelaz captureMirrorEngine(Leelaz engine) {
      return engine.resolveLoadSgfMirrorEngine();
    }

    private static List<TailAction> captureTail(
        BoardHistoryNode target, BoardHistoryNode snapshotAnchor, int boardWidth, int boardHeight) {
      List<TailAction> reversedTail = new ArrayList<>();
      for (BoardHistoryNode node = target;
          node != snapshotAnchor;
          node = node.previous().orElse(null)) {
        if (node == null) {
          throw new IllegalStateException("Snapshot anchor is not an ancestor of history target.");
        }
        TailAction.from(node.getData(), boardWidth, boardHeight).ifPresent(reversedTail::add);
      }
      Collections.reverse(reversedTail);
      return reversedTail;
    }
  }

  private static final class SnapshotAnchor {
    private final BoardHistoryNode node;
    private final BoardData data;

    private SnapshotAnchor(BoardHistoryNode node, BoardData data) {
      this.node = node;
      this.data = data;
    }
  }

  private static final class TailAction {
    private final String command;

    private TailAction(String command) {
      this.command = command;
    }

    private static Optional<TailAction> from(BoardData data, int boardWidth, int boardHeight) {
      if (data == null || data.dummy) {
        return Optional.empty();
      }
      String color = data.lastMoveColor.isBlack() ? "B" : "W";
      if (data.isPassNode()) {
        return Optional.of(new TailAction("play " + color + " pass"));
      }
      if (!data.isMoveNode() || !data.lastMove.isPresent()) {
        return Optional.empty();
      }
      int[] move = data.lastMove.get();
      String coordinate =
          boardWidth > 25 || boardHeight > 25
              ? String.format(java.util.Locale.ENGLISH, "(%d,%d)", move[0], move[1])
              : Board.coordsAsName(move[0]) + (boardHeight - move[1]);
      return Optional.of(new TailAction("play " + color + " " + coordinate));
    }
  }

  private static final class RestoreLifecycle {
    private final Path sgfFile;
    private final AtomicBoolean loadSgfConsumed = new AtomicBoolean(false);
    private final AtomicBoolean loadDispatchStarted = new AtomicBoolean(false);
    private final AtomicBoolean tailReplayFinished = new AtomicBoolean(false);
    private final AtomicBoolean deleteStarted = new AtomicBoolean(false);

    private RestoreLifecycle(Path sgfFile) {
      this.sgfFile = sgfFile;
    }

    private void onLoadSgfConsumed() {
      loadSgfConsumed.set(true);
      tryDelete();
    }

    private void onLoadDispatchStarted() {
      loadDispatchStarted.set(true);
    }

    private void failBeforeLoadDispatch() {
      if (!loadDispatchStarted.get()) {
        loadSgfConsumed.set(true);
        tryDelete();
      }
    }

    private void finishTailReplay() {
      tailReplayFinished.set(true);
      tryDelete();
    }

    private void tryDelete() {
      if (!loadSgfConsumed.get() || !tailReplayFinished.get()) {
        return;
      }
      if (!deleteStarted.compareAndSet(false, true)) {
        return;
      }
      deleteSnapshotFile(sgfFile, 0);
    }
  }
}
