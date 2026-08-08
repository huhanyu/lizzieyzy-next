package featurecat.lizzie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.gui.JFontButton;
import featurecat.lizzie.gui.LizzieFrame;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class EngineStartupStatusTest {
  @Test
  void listenersReceiveCurrentAndFutureStates() {
    EngineStartupStatus status = new EngineStartupStatus();
    List<EngineStartupStatus.Snapshot> updates = new ArrayList<>();

    status.addListener(updates::add);
    status.checking("checking", "Checking");
    status.needsRepair("repair", "Repair", "Missing engine");
    status.failed("failed", "Failed", "Process error");
    status.ready();

    assertEquals(5, updates.size());
    assertEquals(EngineStartupStatus.State.READY, updates.get(0).state);
    assertEquals(EngineStartupStatus.State.CHECKING, updates.get(1).state);
    assertFalse(updates.get(1).isActionable());
    assertTrue(updates.get(2).isActionable());
    assertTrue(updates.get(3).isActionable());
    assertEquals(EngineStartupStatus.State.READY, status.snapshot().state);
  }

  @Test
  void readyStatusPublishesTerminalUiAndCommentOnEdt() throws Exception {
    CountingLizzieFrame frame = allocate(CountingLizzieFrame.class);
    CountingLayeredPane basePanel = new CountingLayeredPane();
    CountingPanel mainPanel = new CountingPanel();
    JFontButton statusButton = new JFontButton();
    basePanel.add(statusButton, Integer.valueOf(12));
    statusButton.setBounds(10, 10, 240, 32);
    statusButton.setText("using existing cache");
    statusButton.setVisible(true);
    setField(LizzieFrame.class, frame, "basePanel", basePanel);
    setField(LizzieFrame.class, frame, "mainPanel", mainPanel);
    setField(LizzieFrame.class, frame, "engineStartupStatusButton", statusButton);
    setField(LizzieFrame.class, frame, "redrawWinratePaneOnly", true);
    basePanel.resetCounts();
    frame.repaintCount = 0;

    EngineStartupStatus status = new EngineStartupStatus();
    status.checking("engine.starting", "using existing cache");
    status.addListener(snapshot -> invokeStatusUpdate(frame, snapshot));
    status.ready();
    SwingUtilities.invokeAndWait(() -> {});

    assertFalse(statusButton.isVisible());
    assertFalse(
        (Boolean) getField(LizzieFrame.class, frame, "redrawWinratePaneOnly"),
        "READY must invalidate a pending winrate-only repaint");
    assertTrue(mainPanel.repaintCount > 0, "READY must repaint the cached main panel");
    assertTrue(frame.repaintCount > 0, "READY must repaint the containing frame");
    assertTrue(frame.refreshCount > 0, "READY must refresh after engine startup");
    assertTrue(
        frame.commentRefreshCount > 0,
        "READY refresh must republish the comment panel after engine startup");
    assertTrue(basePanel.revalidateCount > 0, "READY must publish the hidden notice layout");
    assertTrue(basePanel.repaintCount > 0, "READY must repaint the cleared notice region");
    assertTrue(basePanel.revalidatedOnEdt, "READY layout publication must run on EDT");
    assertTrue(basePanel.repaintedOnEdt, "READY repaint publication must run on EDT");
  }

  private static void invokeStatusUpdate(
      LizzieFrame frame, EngineStartupStatus.Snapshot snapshot) {
    try {
      Method update =
          LizzieFrame.class.getDeclaredMethod(
              "updateEngineStartupStatus", EngineStartupStatus.Snapshot.class);
      update.setAccessible(true);
      update.invoke(frame, snapshot);
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError(failure);
    }
  }

  private static void setField(Class<?> declaringType, Object target, String name, Object value)
      throws Exception {
    Field field = declaringType.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
  private static Object getField(Class<?> declaringType, Object target, String name)
      throws Exception {
    Field field = declaringType.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }
  @SuppressWarnings("unchecked")
  private static <T> T allocate(Class<T> type) throws Exception {
    Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    return (T) ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
  }

  private static final class CountingLayeredPane extends JLayeredPane {
    private int repaintCount;
    private int revalidateCount;
    private boolean revalidatedOnEdt;
    private boolean repaintedOnEdt;

    private void resetCounts() {
      repaintCount = 0;
      revalidateCount = 0;
      revalidatedOnEdt = false;
      repaintedOnEdt = false;
    }

    @Override
    public void revalidate() {
      revalidateCount++;
      revalidatedOnEdt |= SwingUtilities.isEventDispatchThread();
      super.revalidate();
    }

    @Override
    public void repaint() {
      repaintCount++;
      repaintedOnEdt |= SwingUtilities.isEventDispatchThread();
    }
  }
  private static final class CountingPanel extends javax.swing.JPanel {
    private int repaintCount;

    @Override
    public void repaint() {
      repaintCount++;
    }
  }

  private static final class CountingLizzieFrame extends LizzieFrame {
    private int refreshCount;
    private int commentRefreshCount;
    private int repaintCount;

    @Override
    public void refresh() {
      refreshCount++;
      appendComment();
    }

    @Override
    public void appendComment() {
      commentRefreshCount++;
    }

    @Override
    public void repaint() {
      repaintCount++;
    }
  }

  @Test
  void missingEngineRemainsActionableUnlessUserExplicitlySelectedNoEngine() {
    assertTrue(Lizzie.shouldOfferEngineRepair(false, false));
    assertFalse(Lizzie.shouldOfferEngineRepair(false, true));
    assertFalse(Lizzie.shouldOfferEngineRepair(true, false));
  }
}
