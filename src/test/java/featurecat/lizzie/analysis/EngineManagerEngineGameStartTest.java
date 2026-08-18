package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.gui.LizzieFrame;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;

class EngineManagerEngineGameStartTest {
  @Test
  void startNewEngineGameDoesNotEnterPreGameWhenLifecycleTransitionIsRejected() throws Exception {
    LizzieFrame previousFrame = Lizzie.frame;
    Leelaz previousEngine = Lizzie.leelaz;
    boolean previousEngineGame = EngineManager.isEngineGame;
    boolean previousPreEngineGame = EngineManager.isPreEngineGame;
    try {
      RejectingLifecycleLeelaz engine = new RejectingLifecycleLeelaz();
      CountingLeaseEngineManager manager = new CountingLeaseEngineManager(List.of(engine));
      Lizzie.frame = allocate(SilentFrame.class);
      Lizzie.leelaz = engine;
      EngineManager.isEngineGame = false;
      EngineManager.isPreEngineGame = false;

      manager.startNewEngineGame(true);

      assertFalse(EngineManager.isPreEngineGame);
      assertFalse(EngineManager.isEngineGame);
      assertTrue(engine.stoppedPondering);
      assertEqualsOneLeaseConflict(manager);
    } finally {
      Lizzie.frame = previousFrame;
      Lizzie.leelaz = previousEngine;
      EngineManager.isEngineGame = previousEngineGame;
      EngineManager.isPreEngineGame = previousPreEngineGame;
    }
  }

  private static void assertEqualsOneLeaseConflict(CountingLeaseEngineManager manager) {
    if (manager.leaseConflictCount != 1) {
      throw new AssertionError("expected one lease conflict, got " + manager.leaseConflictCount);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T allocate(Class<T> type) throws Exception {
    Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    unsafeField.setAccessible(true);
    sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
    return (T) unsafe.allocateInstance(type);
  }

  private static final class CountingLeaseEngineManager extends EngineManager {
    private int leaseConflictCount;

    private CountingLeaseEngineManager(List<Leelaz> engines) {
      super(engines);
    }

    @Override
    protected void showForegroundEngineLeaseInUse() {
      leaseConflictCount++;
    }
  }

  private static final class RejectingLifecycleLeelaz extends Leelaz {
    private boolean stoppedPondering;

    private RejectingLifecycleLeelaz() throws Exception {
      super("");
    }

    @Override
    public void notPondering() {
      stoppedPondering = true;
    }

    @Override
    public synchronized boolean beginExclusiveGtpLifecycleTransition() {
      return false;
    }
  }

  private static final class SilentFrame extends LizzieFrame {
    @Override
    public void addInput(boolean shouldAdd) {}

    @Override
    public void setResult(String result) {}
  }
}
