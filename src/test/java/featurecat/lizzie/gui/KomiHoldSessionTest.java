package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class KomiHoldSessionTest {
  private static final int INITIAL_DELAY_MS = 40;
  private static final int REPEAT_DELAY_MS = 20;

  @Test
  void shortClickLeavesHoldIdleSoActionListenerIsTheOnlyStep() throws Exception {
    AtomicInteger clicks = new AtomicInteger();
    AtomicInteger holdSteps = new AtomicInteger();
    JButton button = new JButton();
    button.addActionListener(event -> clicks.incrementAndGet());
    attachForHeadlessTest(button, holdSteps);

    SwingUtilities.invokeAndWait(
        () -> {
          button.dispatchEvent(mouse(button, MouseEvent.MOUSE_PRESSED));
          button.dispatchEvent(mouse(button, MouseEvent.MOUSE_RELEASED));
          button.doClick(0);
        });
    Thread.sleep(INITIAL_DELAY_MS + REPEAT_DELAY_MS * 3L);
    flushEdt();

    assertEquals(1, clicks.get(), "one click must change komi exactly once via ActionListener");
    assertEquals(0, holdSteps.get(), "a short click must not start automatic hold steps");
  }

  @Test
  void missedMouseReleasedStopsWhenPointerLeaves() throws Exception {
    AtomicInteger holdSteps = new AtomicInteger();
    JButton button = new JButton();
    KomiHoldSession session = attachForHeadlessTest(button, holdSteps);

    SwingUtilities.invokeAndWait(
        () -> button.dispatchEvent(mouse(button, MouseEvent.MOUSE_PRESSED)));
    awaitAtLeast(holdSteps, 1);
    assertTrue(session.isHolding(), "hold must be active before the missed-release fallback");

    SwingUtilities.invokeAndWait(
        () -> button.dispatchEvent(mouse(button, MouseEvent.MOUSE_EXITED)));
    flushEdt();
    int frozen = holdSteps.get();
    assertFalse(session.isHolding(), "pointer leave must stop a hold that never saw mouseReleased");

    Thread.sleep(REPEAT_DELAY_MS * 4L);
    flushEdt();
    assertEquals(
        frozen,
        holdSteps.get(),
        "missed mouseReleased must not leave automatic komi steps running");
  }

  @Test
  void disableDuringHoldStopsRepeatingWithoutMouseReleased() throws Exception {
    AtomicInteger holdSteps = new AtomicInteger();
    JButton button = new JButton();
    KomiHoldSession session = attachForHeadlessTest(button, holdSteps);

    SwingUtilities.invokeAndWait(
        () -> button.dispatchEvent(mouse(button, MouseEvent.MOUSE_PRESSED)));
    awaitAtLeast(holdSteps, 1);
    assertTrue(session.isHolding());

    SwingUtilities.invokeAndWait(() -> button.setEnabled(false));
    flushEdt();
    int frozen = holdSteps.get();
    assertFalse(session.isHolding(), "disable during hold must stop the session");

    Thread.sleep(REPEAT_DELAY_MS * 4L);
    flushEdt();
    assertEquals(
        frozen, holdSteps.get(), "a disabled control must not keep stepping komi by itself");
  }

  @Test
  void focusLostStopsHoldWhenMouseReleasedIsMissed() throws Exception {
    AtomicInteger holdSteps = new AtomicInteger();
    JButton button = new JButton();
    KomiHoldSession session = attachForHeadlessTest(button, holdSteps);

    SwingUtilities.invokeAndWait(
        () -> button.dispatchEvent(mouse(button, MouseEvent.MOUSE_PRESSED)));
    awaitAtLeast(holdSteps, 1);

    SwingUtilities.invokeAndWait(
        () -> {
          FocusEvent lost = new FocusEvent(button, FocusEvent.FOCUS_LOST);
          for (FocusListener listener : button.getFocusListeners()) {
            listener.focusLost(lost);
          }
        });
    flushEdt();
    int frozen = holdSteps.get();
    assertFalse(session.isHolding());

    Thread.sleep(REPEAT_DELAY_MS * 4L);
    flushEdt();
    assertEquals(frozen, holdSteps.get(), "focus loss must not leave automatic hold steps running");
  }

  @Test
  void aLaterPressDoesNotLeaveTwoRepeaters() throws Exception {
    AtomicInteger holdSteps = new AtomicInteger();
    JButton button = new JButton();
    KomiHoldSession session = attachForHeadlessTest(button, holdSteps);

    SwingUtilities.invokeAndWait(
        () -> {
          button.dispatchEvent(mouse(button, MouseEvent.MOUSE_PRESSED));
          button.dispatchEvent(mouse(button, MouseEvent.MOUSE_PRESSED));
        });
    awaitAtLeast(holdSteps, 1);
    assertTrue(session.isHolding());

    SwingUtilities.invokeAndWait(
        () -> button.dispatchEvent(mouse(button, MouseEvent.MOUSE_RELEASED)));
    flushEdt();
    int frozen = holdSteps.get();
    assertFalse(session.isHolding());

    Thread.sleep(REPEAT_DELAY_MS * 4L);
    flushEdt();
    assertEquals(frozen, holdSteps.get(), "only one hold task may remain after release");
  }

  @Test
  void detachedControlWithoutAncestorWindowCannotRepeat() throws Exception {
    AtomicInteger holdSteps = new AtomicInteger();
    JButton button = new JButton();
    KomiHoldSession session =
        KomiHoldSession.attach(
            button, holdSteps::incrementAndGet, INITIAL_DELAY_MS, REPEAT_DELAY_MS);

    SwingUtilities.invokeAndWait(
        () -> button.dispatchEvent(mouse(button, MouseEvent.MOUSE_PRESSED)));
    Thread.sleep(INITIAL_DELAY_MS + REPEAT_DELAY_MS * 3L);
    flushEdt();

    assertFalse(session.isHolding(), "a detached control must never start a hold session");
    assertFalse(session.isFocusManagerBound(), "a rejected hold must not bind global focus state");
    assertEquals(0, holdSteps.get(), "a control without an ancestor window must not repeat");
  }

  @Test
  void realWindowControlHideStopsProductionLifecycleSession() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
    AtomicInteger holdSteps = new AtomicInteger();
    JButton button = new JButton("komi");
    JFrame frame = new JFrame("KomiHoldSessionTest");
    KomiHoldSession[] session = new KomiHoldSession[1];
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            frame.add(button);
            frame.pack();
            frame.setLocation(-10_000, -10_000);
            frame.setVisible(true);
            session[0] =
                KomiHoldSession.attach(
                    button, holdSteps::incrementAndGet, INITIAL_DELAY_MS, REPEAT_DELAY_MS);
            button.dispatchEvent(mouse(button, MouseEvent.MOUSE_PRESSED));
          });
      awaitAtLeast(holdSteps, 1);

      SwingUtilities.invokeAndWait(() -> button.setVisible(false));
      flushEdt();
      int frozen = holdSteps.get();
      assertFalse(session[0].isHolding());
      assertFalse(session[0].isFocusManagerBound());

      Thread.sleep(REPEAT_DELAY_MS * 4L);
      flushEdt();
      assertEquals(frozen, holdSteps.get(), "a hidden live control must leave no active timer");
    } finally {
      SwingUtilities.invokeAndWait(frame::dispose);
    }
  }

  @Test
  void showingChangeDuringHoldStopsAndUnbindsGlobalFocusListener() throws Exception {
    assertHierarchyMutationStops(
        "hide",
        (button, originalParent, replacementParent) -> button.setVisible(false));
  }

  @Test
  void removalDuringHoldStopsAndUnbindsGlobalFocusListener() throws Exception {
    assertHierarchyMutationStops(
        "remove",
        (button, originalParent, replacementParent) -> originalParent.remove(button));
  }

  @Test
  void reparentDuringHoldStopsAndUnbindsGlobalFocusListener() throws Exception {
    assertHierarchyMutationStops(
        "reparent",
        (button, originalParent, replacementParent) -> replacementParent.add(button));
  }

  @Test
  void displayabilityChangeDuringHoldStopsAndUnbindsGlobalFocusListener() throws Exception {
    assertHierarchyMutationStops(
        "displayability change",
        (button, originalParent, replacementParent) ->
            fireHierarchyChange(
                button, originalParent, HierarchyEvent.DISPLAYABILITY_CHANGED));
  }

  private static void assertHierarchyMutationStops(
      String reason, HierarchyMutation mutation) throws Exception {
    AtomicInteger holdSteps = new AtomicInteger();
    JButton button = new JButton();
    JPanel originalParent = new JPanel();
    JPanel replacementParent = new JPanel();
    SwingUtilities.invokeAndWait(() -> originalParent.add(button));
    KomiHoldSession session = attachForHeadlessTest(button, holdSteps);

    SwingUtilities.invokeAndWait(
        () -> button.dispatchEvent(mouse(button, MouseEvent.MOUSE_PRESSED)));
    awaitAtLeast(holdSteps, 1);
    flushEdt();
    assertTrue(session.isHolding(), "hold must be active before " + reason);
    assertTrue(session.isFocusManagerBound(), "active hold must bind the focus manager");

    SwingUtilities.invokeAndWait(
        () -> mutation.apply(button, originalParent, replacementParent));
    flushEdt();
    int frozen = holdSteps.get();
    assertFalse(session.isHolding(), reason + " must stop the hold immediately");
    assertFalse(
        session.isFocusManagerBound(), reason + " must unbind the global focus listener");

    Thread.sleep(REPEAT_DELAY_MS * 4L);
    flushEdt();
    assertEquals(frozen, holdSteps.get(), reason + " must leave no repeating timer behind");
  }

  private static KomiHoldSession attachForHeadlessTest(
      JButton button, AtomicInteger holdSteps) {
    return KomiHoldSession.attachForTesting(
        button,
        holdSteps::incrementAndGet,
        INITIAL_DELAY_MS,
        REPEAT_DELAY_MS,
        () -> true);
  }

  private static void fireHierarchyChange(
      JButton button, Container changedParent, long changeFlags) {
    HierarchyEvent event =
        new HierarchyEvent(
            button,
            HierarchyEvent.HIERARCHY_CHANGED,
            button,
            changedParent,
            changeFlags);
    for (HierarchyListener listener : button.getHierarchyListeners()) {
      listener.hierarchyChanged(event);
    }
  }

  @FunctionalInterface
  private interface HierarchyMutation {
    void apply(JButton button, JPanel originalParent, JPanel replacementParent);
  }

  private static MouseEvent mouse(JButton button, int id) {
    return new MouseEvent(
        button, id, System.currentTimeMillis(), 0, 0, 0, 1, false, MouseEvent.BUTTON1);
  }

  private static void awaitAtLeast(AtomicInteger value, int minimum) throws Exception {
    long deadline = System.nanoTime() + 2_000_000_000L;
    while (value.get() < minimum) {
      if (System.nanoTime() > deadline) {
        fail("timed out waiting for " + minimum + " hold steps, had " + value.get());
      }
      Thread.sleep(5L);
    }
  }

  private static void flushEdt() throws Exception {
    SwingUtilities.invokeAndWait(() -> {});
  }
}
