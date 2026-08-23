package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.DefaultKeyboardFocusManager;
import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class LizzieFrameRestartInteractionGateTest {

  @Test
  void gateBlocksAndRestoresDesktopEntryPointState() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
    JFrame frame = new JFrame();
    JPanel board = new JPanel();
    AtomicInteger boardMutations = new AtomicInteger();
    AtomicInteger navigationMutations = new AtomicInteger();
    AtomicInteger menuMutations = new AtomicInteger();
    AtomicInteger fileOpenMutations = new AtomicInteger();
    AtomicInteger dragDropMutations = new AtomicInteger();
    AtomicInteger dialogMutations = new AtomicInteger();
    board.setFocusable(true);
    board.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent event) {
            boardMutations.incrementAndGet();
            board.requestFocusInWindow();
          }
        });
    board
        .getInputMap(JPanel.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "navigate");
    board
        .getActionMap()
        .put(
            "navigate",
            new AbstractAction() {
              @Override
              public void actionPerformed(java.awt.event.ActionEvent event) {
                navigationMutations.incrementAndGet();
              }
            });
    board.setTransferHandler(
        new TransferHandler() {
          @Override
          public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.stringFlavor);
          }

          @Override
          public boolean importData(TransferSupport support) {
            dragDropMutations.incrementAndGet();
            return true;
          }
        });
    TransferHandler dragDropEntry = board.getTransferHandler();
    JMenuBar menuBar = new JMenuBar();
    JMenu menu = new JMenu("actions");
    JMenuItem menuAction = new JMenuItem("menu");
    menuAction.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK));
    menuAction.addActionListener(event -> menuMutations.incrementAndGet());
    JMenuItem fileOpen = new JMenuItem("open");
    fileOpen.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
    fileOpen.addActionListener(event -> fileOpenMutations.incrementAndGet());
    menu.add(menuAction);
    menu.add(fileOpen);
    menuBar.add(menu);
    List<JDialog> settingsDialogs = new ArrayList<>();
    List<JButton> settingsMutations = new ArrayList<>();
    for (String title : List.of("board-size", "komi", "rules")) {
      JDialog dialog = new JDialog(frame, title);
      JButton mutation = new JButton("apply");
      mutation.addActionListener(event -> dialogMutations.incrementAndGet());
      dialog.add(mutation);
      settingsDialogs.add(dialog);
      settingsMutations.add(mutation);
    }
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            frame.setJMenuBar(menuBar);
            frame.add(board);
            board.setPreferredSize(new java.awt.Dimension(240, 160));
            frame.pack();
            frame.setLocation(100, 100);
            frame.setVisible(true);
            int dialogX = 380;
            for (JDialog dialog : settingsDialogs) {
              dialog.pack();
              dialog.setLocation(dialogX, 100);
              dialog.setVisible(true);
              dialogX += 120;
            }
          });

      LizzieFrame.RestartInteractionGate gate = LizzieFrame.beginRestartInteractionGate(frame);
      assertFalse(frame.isEnabled());
      settingsDialogs.forEach(dialog -> assertFalse(dialog.isEnabled()));
      assertTrue(boardMutations.get() == 0);
      assertTrue(navigationMutations.get() == 0);
      assertTrue(menuMutations.get() == 0);
      assertTrue(fileOpenMutations.get() == 0);
      assertNull(board.getTransferHandler());
      assertTrue(dragDropMutations.get() == 0);
      assertTrue(dialogMutations.get() == 0);

      postKeyEvent(board, KeyEvent.VK_RIGHT, 0);
      postKeyEvent(board, KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK);
      postKeyEvent(board, KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK);
      assertTrue(navigationMutations.get() == 0);
      assertTrue(menuMutations.get() == 0);
      assertTrue(fileOpenMutations.get() == 0);

      gate.close();
      gate.close();
      assertTrue(frame.isEnabled());
      settingsDialogs.forEach(dialog -> assertTrue(dialog.isEnabled()));
      assertSame(dragDropEntry, board.getTransferHandler());

      SwingUtilities.invokeAndWait(
          () -> {
            board.dispatchEvent(
                new MouseEvent(
                    board,
                    MouseEvent.MOUSE_CLICKED,
                    System.currentTimeMillis(),
                    0,
                    board.getWidth() / 2,
                    board.getHeight() / 2,
                    1,
                    false,
                    MouseEvent.BUTTON1));
            board.getActionMap().get("navigate").actionPerformed(null);
            menuAction.doClick();
            fileOpen.doClick();
            for (JButton mutation : settingsMutations) {
              mutation.doClick();
            }
            assertTrue(
                board
                    .getTransferHandler()
                    .importData(
                        new TransferHandler.TransferSupport(
                            board, new StringSelection("sgf-file"))));
          });
      assertTrue(boardMutations.get() == 1);
      assertTrue(navigationMutations.get() == 1);
      assertTrue(menuMutations.get() == 1);
      assertTrue(fileOpenMutations.get() == 1);
      assertTrue(dragDropMutations.get() == 1);
      assertTrue(dialogMutations.get() == 3);
    } finally {
      SwingUtilities.invokeAndWait(
          () -> {
            for (JDialog dialog : settingsDialogs) {
              dialog.dispose();
            }
            frame.dispose();
          });
    }
  }

  private static void postKeyEvent(Component source, int keyCode, int modifiers)
      throws Exception {
    EventQueue queue = Toolkit.getDefaultToolkit().getSystemEventQueue();
    queue.postEvent(
        new KeyEvent(
            source,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            modifiers,
            keyCode,
            KeyEvent.CHAR_UNDEFINED));
    SwingUtilities.invokeAndWait(() -> {});
  }

  @Test
  void gateDisablesExistingOwnedWindowsAndRestoresOriginalStateExactlyOnce() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
    JFrame frame = new JFrame();
    JDialog enabledDialog = new JDialog(frame);
    JDialog disabledDialog = new JDialog(frame);
    JDialog nestedDialog = new JDialog(enabledDialog);
    List<Boolean> mutationsOnEdt = new ArrayList<>();
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            frame.setEnabled(true);
            enabledDialog.setEnabled(true);
            disabledDialog.setEnabled(false);
            nestedDialog.setEnabled(true);
            frame.addPropertyChangeListener(
                "enabled", event -> mutationsOnEdt.add(SwingUtilities.isEventDispatchThread()));
            enabledDialog.addPropertyChangeListener(
                "enabled", event -> mutationsOnEdt.add(SwingUtilities.isEventDispatchThread()));
            disabledDialog.addPropertyChangeListener(
                "enabled", event -> mutationsOnEdt.add(SwingUtilities.isEventDispatchThread()));
            nestedDialog.addPropertyChangeListener(
                "enabled", event -> mutationsOnEdt.add(SwingUtilities.isEventDispatchThread()));
          });

      LizzieFrame.RestartInteractionGate gate = LizzieFrame.beginRestartInteractionGate(frame);

      assertFalse(frame.isEnabled());
      assertFalse(enabledDialog.isEnabled());
      assertFalse(disabledDialog.isEnabled());
      assertFalse(nestedDialog.isEnabled());

      gate.close();
      gate.close();

      assertTrue(frame.isEnabled());
      assertTrue(enabledDialog.isEnabled());
      assertFalse(disabledDialog.isEnabled());
      assertTrue(nestedDialog.isEnabled());
      assertTrue(mutationsOnEdt.stream().allMatch(Boolean::booleanValue));
    } finally {
      SwingUtilities.invokeAndWait(
          () -> {
            nestedDialog.dispose();
            disabledDialog.dispose();
            enabledDialog.dispose();
            frame.dispose();
          });
    }
  }

  @Test
  void gateRollsBackAlreadyDisabledWindowsWhenAnOwnedWindowFails() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
    JFrame frame = new JFrame();
    FailingDisableDialog dialog = new FailingDisableDialog(frame);
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            frame.setEnabled(true);
            dialog.setEnabled(true);
          });

      assertThrows(
          IllegalStateException.class, () -> LizzieFrame.beginRestartInteractionGate(frame));

      assertTrue(frame.isEnabled());
      assertTrue(dialog.isEnabled());
    } finally {
      SwingUtilities.invokeAndWait(
          () -> {
            dialog.dispose();
            frame.dispose();
          });
    }
  }

  @Test
  void gateRollsBackEveryPartialMutationWhenDirectEdtDisableThrowsError() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
    KeyboardFocusManager previousFocusManager =
        KeyboardFocusManager.getCurrentKeyboardFocusManager();
    InspectingKeyboardFocusManager focusManager = new InspectingKeyboardFocusManager();
    InspectingKeyboardFocusManager.install(focusManager);
    JFrame frame = new JFrame();
    AssertionError disableFailure = new AssertionError("controlled disable error");
    AssertionError restoreFailure = new AssertionError("controlled rollback error");
    ErrorOnDisableDialog dialog =
        new ErrorOnDisableDialog(frame, disableFailure, restoreFailure);
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            frame.setEnabled(true);
            dialog.setEnabled(true);
            dialog.arm();
          });
      int dispatcherCount = focusManager.dispatcherCount();
      AtomicReference<AssertionError> observed = new AtomicReference<>();

      SwingUtilities.invokeAndWait(
          () ->
              observed.set(
                  assertThrows(
                      AssertionError.class,
                      () -> LizzieFrame.beginRestartInteractionGate(frame))));

      assertSame(disableFailure, observed.get());
      assertEquals(1, disableFailure.getSuppressed().length);
      assertSame(restoreFailure, disableFailure.getSuppressed()[0]);
      assertTrue(frame.isEnabled());
      assertTrue(dialog.isEnabled());
      assertEquals(dispatcherCount, focusManager.dispatcherCount());
    } finally {
      dialog.disarm();
      SwingUtilities.invokeAndWait(
          () -> {
            dialog.dispose();
            frame.dispose();
          });
      InspectingKeyboardFocusManager.install(previousFocusManager);
    }
  }

  @Test
  void gateCloseRestoresRemainingResourcesAndRemovesDispatcherAfterErrors() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
    KeyboardFocusManager previousFocusManager =
        KeyboardFocusManager.getCurrentKeyboardFocusManager();
    InspectingKeyboardFocusManager focusManager = new InspectingKeyboardFocusManager();
    InspectingKeyboardFocusManager.install(focusManager);
    JFrame frame = new JFrame();
    AssertionError windowRestoreFailure = new AssertionError("controlled window restore error");
    AssertionError transferRestoreFailure =
        new AssertionError("controlled transfer-handler restore error");
    ErrorOnRestoreDialog dialog = new ErrorOnRestoreDialog(frame, windowRestoreFailure);
    ErrorOnRestorePanel board = new ErrorOnRestorePanel(transferRestoreFailure);
    TransferHandler transferHandler = new TransferHandler("name");
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            frame.add(board);
            frame.setEnabled(true);
            dialog.setEnabled(true);
            board.setTransferHandler(transferHandler);
            dialog.arm();
            board.arm();
          });
      int dispatcherCount = focusManager.dispatcherCount();
      LizzieFrame.RestartInteractionGate gate =
          LizzieFrame.beginRestartInteractionGate(frame);
      assertFalse(frame.isEnabled());
      assertFalse(dialog.isEnabled());
      assertNull(board.getTransferHandler());
      AtomicReference<AssertionError> observed = new AtomicReference<>();

      SwingUtilities.invokeAndWait(
          () -> observed.set(assertThrows(AssertionError.class, gate::close)));

      assertSame(windowRestoreFailure, observed.get());
      assertEquals(1, windowRestoreFailure.getSuppressed().length);
      assertSame(transferRestoreFailure, windowRestoreFailure.getSuppressed()[0]);
      assertTrue(frame.isEnabled());
      assertTrue(dialog.isEnabled());
      assertSame(transferHandler, board.getTransferHandler());
      assertEquals(dispatcherCount, focusManager.dispatcherCount());
      gate.close();
    } finally {
      dialog.disarm();
      board.disarm();
      SwingUtilities.invokeAndWait(
          () -> {
            dialog.dispose();
            frame.dispose();
          });
      InspectingKeyboardFocusManager.install(previousFocusManager);
    }
  }

  @Test
  void interruptedBeginWaitsForItsPostedMutationAndRollsBackTheAbandonedGate()
      throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
    CountingEnabledFrame frame = new CountingEnabledFrame();
    JPanel board = new JPanel();
    TransferHandler transferHandler = new TransferHandler("text");
    CountDownLatch edtBlocked = new CountDownLatch(1);
    CountDownLatch releaseEdt = new CountDownLatch(1);
    AtomicReference<Throwable> beginFailure = new AtomicReference<>();
    AtomicBoolean interruptPreserved = new AtomicBoolean();
    Thread beginWorker =
        new Thread(
            () -> {
              try {
                LizzieFrame.beginRestartInteractionGate(frame);
              } catch (Throwable failure) {
                beginFailure.set(failure);
                interruptPreserved.set(Thread.currentThread().isInterrupted());
              }
            },
            "controlled-restart-gate-begin");
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            board.setTransferHandler(transferHandler);
            frame.add(board);
            frame.setEnabled(true);
            frame.armTransitionCounting();
          });
      SwingUtilities.invokeLater(
          () -> {
            edtBlocked.countDown();
            try {
              releaseEdt.await();
            } catch (InterruptedException interrupted) {
              Thread.currentThread().interrupt();
            }
          });
      assertTrue(edtBlocked.await(2, TimeUnit.SECONDS));

      beginWorker.start();
      assertTrue(awaitThreadState(beginWorker, Thread.State.WAITING, 2, TimeUnit.SECONDS));
      beginWorker.interrupt();
      beginWorker.join(150L);
      assertTrue(
          beginWorker.isAlive(),
          "an interrupted caller must retain cleanup ownership until the posted begin completes");

      releaseEdt.countDown();
      beginWorker.join(TimeUnit.SECONDS.toMillis(2));
      assertFalse(beginWorker.isAlive());
      SwingUtilities.invokeAndWait(() -> {});

      assertTrue(beginFailure.get() instanceof IllegalStateException);
      assertTrue(interruptPreserved.get());
      assertTrue(frame.isEnabled());
      assertSame(transferHandler, board.getTransferHandler());
      assertEquals(1, frame.disableTransitions.get(), "disable transitions");
      assertEquals(1, frame.enableTransitions.get(), "enable transitions");
    } finally {
      releaseEdt.countDown();
      beginWorker.interrupt();
      beginWorker.join(TimeUnit.SECONDS.toMillis(2));
      SwingUtilities.invokeAndWait(frame::dispose);
    }
  }

  private static boolean awaitThreadState(
      Thread thread, Thread.State expected, long timeout, TimeUnit unit)
      throws InterruptedException {
    long deadline = System.nanoTime() + unit.toNanos(timeout);
    while (System.nanoTime() < deadline) {
      if (thread.getState() == expected) {
        return true;
      }
      Thread.sleep(5L);
    }
    return thread.getState() == expected;
  }

  private static final class CountingEnabledFrame extends JFrame {
    private final AtomicInteger disableTransitions = new AtomicInteger();
    private final AtomicInteger enableTransitions = new AtomicInteger();
    private boolean countTransitions;

    private void armTransitionCounting() {
      countTransitions = true;
    }

    @Override
    public void setEnabled(boolean enabled) {
      boolean changed = enabled != isEnabled();
      super.setEnabled(enabled);
      if (!countTransitions || !changed) {
        return;
      }
      if (enabled) {
        enableTransitions.incrementAndGet();
      } else {
        disableTransitions.incrementAndGet();
      }
    }
  }

  private static final class FailingDisableDialog extends JDialog {
    private boolean failNextDisable = true;

    private FailingDisableDialog(JFrame owner) {
      super(owner);
    }

    @Override
    public void setEnabled(boolean enabled) {
      super.setEnabled(enabled);
      if (!enabled && failNextDisable) {
        failNextDisable = false;
        throw new IllegalStateException("controlled gate failure");
      }
    }
  }

  private static final class ErrorOnDisableDialog extends JDialog {
    private final AssertionError disableFailure;
    private final AssertionError restoreFailure;
    private boolean armed;
    private boolean failDisable = true;
    private boolean failRestore = true;

    private ErrorOnDisableDialog(
        JFrame owner, AssertionError disableFailure, AssertionError restoreFailure) {
      super(owner);
      this.disableFailure = disableFailure;
      this.restoreFailure = restoreFailure;
    }

    private void arm() {
      armed = true;
    }

    private void disarm() {
      armed = false;
    }

    @Override
    public void setEnabled(boolean enabled) {
      super.setEnabled(enabled);
      if (armed && !enabled && failDisable) {
        failDisable = false;
        throw disableFailure;
      }
      if (armed && enabled && failRestore) {
        failRestore = false;
        throw restoreFailure;
      }
    }
  }

  private static final class ErrorOnRestoreDialog extends JDialog {
    private final AssertionError restoreFailure;
    private boolean armed;
    private boolean disabledByGate;

    private ErrorOnRestoreDialog(JFrame owner, AssertionError restoreFailure) {
      super(owner);
      this.restoreFailure = restoreFailure;
    }

    private void arm() {
      armed = true;
    }

    private void disarm() {
      armed = false;
    }

    @Override
    public void setEnabled(boolean enabled) {
      super.setEnabled(enabled);
      if (armed && !enabled) {
        disabledByGate = true;
      } else if (armed && enabled && disabledByGate) {
        disabledByGate = false;
        throw restoreFailure;
      }
    }
  }

  private static final class ErrorOnRestorePanel extends JPanel {
    private final AssertionError restoreFailure;
    private boolean armed;
    private boolean clearedByGate;

    private ErrorOnRestorePanel(AssertionError restoreFailure) {
      this.restoreFailure = restoreFailure;
    }

    private void arm() {
      armed = true;
    }

    private void disarm() {
      armed = false;
    }

    @Override
    public void setTransferHandler(TransferHandler handler) {
      super.setTransferHandler(handler);
      if (armed && handler == null) {
        clearedByGate = true;
      } else if (armed && handler != null && clearedByGate) {
        clearedByGate = false;
        throw restoreFailure;
      }
    }
  }

  private static final class InspectingKeyboardFocusManager
      extends DefaultKeyboardFocusManager {
    private static void install(KeyboardFocusManager focusManager) {
      setCurrentKeyboardFocusManager(focusManager);
    }

    private int dispatcherCount() {
      List<KeyEventDispatcher> dispatchers = getKeyEventDispatchers();
      return dispatchers == null ? 0 : dispatchers.size();
    }
  }

}
