package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.*;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.Leelaz;
import featurecat.lizzie.fox.FoxLiveSession;
import featurecat.lizzie.fox.FoxProtocol;
import featurecat.lizzie.rules.BoardHistoryNode;
import featurecat.lizzie.rules.SGFParser;
import featurecat.lizzie.util.Utils;
import java.awt.*;
import java.net.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/** Opt-in real application + loopback peer + one existing KataGo; no engine fixture substitution. */
public final class FoxLiveSessionAcceptanceIT {
  @Test void actualApplicationFollowsConfirmedFoxActionsAndRestores() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("fox.smoke"));
    DesktopProbeProcess.requireDisplay();
    Path result = DesktopProbeProcess.run(FoxLiveSessionAcceptanceIT.class, "fox-live",
        List.of(), List.of(System.getProperty("fox.engine"), System.getProperty("fox.model")), 180);
    assertTrue(Files.readString(result).contains("PASS"));
  }

  public static void main(String[] args) throws Exception {
    Path work = Path.of(args[2]);
    Path result = Path.of(args[3]);
    Leelaz engine = null;
    try {
      JSONObject config = new JSONObject()
          .put("leelaz", new JSONObject().put("engine-settings-list", new JSONArray()))
          .put("ui", new JSONObject().put("autoload-empty", true).put("first-time-load", false)
              .put("use-language", 2).put("auto-quick-analyze-on-load", false));
      Files.writeString(work.resolve("config.txt"), config.toString(2));
      Path engineConfig = work.resolve("fox-katago.cfg");
      Files.writeString(engineConfig, "rules = chinese\nkomi = 6.5\nnumSearchThreads = 2\n"
          + "maxVisits = 64\nmaxTime = 3.0\nponderingEnabled = false\n"
          + "logAllGTPCommunication = true\nlogSearchInfo = true\nlogDir = "
          + work.resolve("gtp-log") + "\n");
      System.setProperty("lizzie.work.dir", work.toString());
      Lizzie.main(new String[0]);
      await(() -> Lizzie.frame != null && Lizzie.frame.isShowing(), 30, "main Swing window");
      SwingUtilities.invokeAndWait(() -> {
        for (Window window : Window.getWindows()) if (window instanceof Dialog) window.dispose();
      });
      EngineData entry = new EngineData();
      entry.id = UUID.randomUUID().toString();
      entry.index = Lizzie.engineManager.engineList.size();
      entry.commands = quote(args[0]) + " gtp -model " + quote(args[1]) + " -config " + quote(engineConfig.toString());
      entry.name = "Fox real KataGo acceptance";
      entry.width = entry.height = 19;
      entry.komi = 6.5f;
      Utils.saveEngineSettings(new ArrayList<>(List.of(entry)));
      engine = new NamedEngine(entry);
      Lizzie.engineManager.engineList.add(engine);
      assertTrue(Lizzie.engineManager.switchEngineIfAvailable(entry.index, true));
      Leelaz target = engine;
      await(() -> target.isLoaded() && !target.hasExclusiveGtpWorkInProgress(), 60, "current KataGo ready");
      target.notPondering();
      target.sendCommand("stop");
      Thread.sleep(500);
      BoardHistoryNode originalDisplay = Lizzie.board.getHistory().getCurrentHistoryNode();
      SwingUtilities.invokeAndWait(FoxLiveSession::showDialog);
      JDialog fox = dialog();
      int port;
      try (ServerSocket available = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
        port = available.getLocalPort();
      }
      int selectedPort = port;
      SwingUtilities.invokeAndWait(() -> find(fox, JSpinner.class).setValue(selectedPort));
      SwingUtilities.invokeLater(() -> button(fox, "Fox.start").doClick());
      await(() -> modal() != null, 5, "explicit rules confirmation");
      SwingUtilities.invokeAndWait(() -> {
        JDialog modal = modal();
        JOptionPane pane = find(modal, JOptionPane.class);
        pane.setValue(JOptionPane.OK_OPTION);
        modal.dispose();
      });
      await(() -> button(fox, "Fox.stop").isEnabled(), 5, "listener start");
      try (Socket peer = new Socket("127.0.0.1", port)) {
        peer.setSoTimeout(200);
        send(peer, "FARULE,19,60,30,3,650", "FASTATUS,1,B", "FATIMELEFT,60,30,3");
        assertThrows(SocketTimeoutException.class, () -> peer.getInputStream().read(), "no send before authoritative turn");
        long turnStart = System.nanoTime();
        send(peer, "FAMOVE,1,B,0^0^0^N");
        peer.setSoTimeout(15000);
        assertEquals("AFSTATUS,B", receive(peer));
        send(peer, "FASTATUS,1,B", "FATIMELEFT,59,30,3", "FAMOVE,1,B,0^0^0^N");
        await(() -> find(fox, JTextArea.class).getText().contains("%"), 25, "real current candidates");
        String candidates = find(fox, JTextArea.class).getText();
        String submission = receive(peer);
        long submitted = System.nanoTime();
        assertTrue(submission.startsWith("AFPLAY,B,1^"), submission);
        String tuple = submission.substring("AFPLAY,B,".length());
        String[] fields = tuple.split("\\^");
        String vertex = "ABCDEFGHJKLMNOPQRSTUVWXYZ".charAt(Integer.parseInt(fields[1]))
            + Integer.toString(19 - Integer.parseInt(fields[2]));
        assertEquals(0, Lizzie.board.getHistory().getMoveNumber(), "pending is not a local move");
        assertTrue(hasText(fox, "Fox.pending"));
        assertTrue(target.hasExclusiveGtpLease());
        assertFalse(target.hasForegroundAnalysisLeaseWorkInProgress());
        assertEquals(1, ProcessHandle.current().descendants()
            .filter(p -> p.info().command().orElse("").endsWith("katago")).count());
        String beforeConfirmation = gtp(work);
        assertTrue(beforeConfirmation.contains("play " + vertex), "same real search final choice");
        assertEquals(1, beforeConfirmation.lines().filter(line -> line.contains("Controller:")
            && line.contains("kata-search_analyze_cancellable")).count());
        assertFalse(beforeConfirmation.lines().anyMatch(line -> line.contains("Controller:")
            && line.endsWith("play B " + vertex)), "engine not advanced before confirmation");
        assertTrue(submitted - turnStart < TimeUnit.SECONDS.toNanos(58), "normal submit before deadline");
        ImageIO.write(new Robot().createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())),
            "png", work.resolve("fox-pending.png").toFile());
        peer.setSoTimeout(250);
        send(peer, "FASTATUS,1,B", "FATIMELEFT,58,30,3", "FAMOVE,1,B,0^0^0^N");
        assertThrows(SocketTimeoutException.class, () -> peer.getInputStream().read(), "query never resends pending");
        long confirmed = System.nanoTime();
        send(peer, "FAMOVE,0,B," + tuple, "FATIMELEFT,58,30,3");
        await(() -> Lizzie.board.getHistory().getMoveNumber() == 1, 5, "confirmed local MOVE");
        await(() -> gtp(work).lines().anyMatch(line -> line.contains("Controller:")
            && line.endsWith("play B " + vertex)), 5, "timely confirmed engine sync");
        long synchronizedAt = System.nanoTime();
        await(() -> find(fox, JTextArea.class).getText().contains("%"), 15, "opponent candidates");
        assertThrows(SocketTimeoutException.class, () -> peer.getInputStream().read(), "opponent never submits");
        ImageIO.write(new Robot().createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())),
            "png", work.resolve("fox-confirmed.png").toFile());
        Files.writeString(work.resolve("timing.txt"), "turnStart=" + turnStart + "\nsubmitted=" + submitted
            + "\nconfirmed=" + confirmed + "\nengineSynchronized=" + synchronizedAt
            + "\nsubmission=" + submission + "\ncandidatesBeforeFinal=\n" + candidates);
        // A peer-observed pass is evidence, never an invented engine choice.
        int whiteX = Integer.parseInt(fields[1]) == 2 && Integer.parseInt(fields[2]) == 16 ? 3 : 2;
        String white = "2^" + whiteX + "^16^W";
        send(peer, "FAMOVE,1,B," + white, "FASKIP,0,B");
        await(() -> Lizzie.board.getHistory().getMoveNumber() == 3, 5, "MOVE MOVE PASS history");
        BoardHistoryNode pass = Lizzie.board.getHistory().getCurrentHistoryNode();
        BoardHistoryNode historical = pass.previous().orElseThrow();
        assertTrue(pass.getData().isPassNode());
        await(() -> gtp(work).contains("play B pass"), 10, "real pass replay");
        await(() -> find(fox, JTextArea.class).getText().contains("%"), 15, "pass-position candidate");
        String navigationCommands = commands(work);
        Controller[] controls = new Controller[1];
        SwingUtilities.invokeAndWait(() -> {
          controls[0] = new Controller(Lizzie.frame);
          controls[0].setVisible(true);
          literalButton(controls[0], "<").doClick();
        });
        assertSame(historical, Lizzie.board.getHistory().getCurrentHistoryNode());
        assertTrue(hasText(fox, "Fox.browsing"));
        assertFalse(find(fox, JTextArea.class).getText().contains("%"));
        SwingUtilities.invokeAndWait(() -> {
          controls[0].dispose();
          Lizzie.frame.toFront();
          Lizzie.frame.setMainPanelFocus();
        });
        Robot robot = new Robot();
        robot.delay(250);
        robot.keyPress(java.awt.event.KeyEvent.VK_UP);
        robot.keyRelease(java.awt.event.KeyEvent.VK_UP);
        await(() -> Lizzie.board.getHistory().getMoveNumber() == 1, 5, "actual keyboard history");
        Component keyboardHost = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        assertNotNull(keyboardHost);
        Point hostOrigin = keyboardHost.getLocationOnScreen();
        robot.mouseMove(hostOrigin.x + 10, hostOrigin.y + 10);
        robot.mouseWheel(1);
        await(() -> Lizzie.board.getHistory().getCurrentHistoryNode() == historical, 5, "actual wheel history");
        SwingUtilities.invokeAndWait(() -> {
          Lizzie.config.showVariationGraph = true;
          Lizzie.config.showScrollVariation = false;
          Lizzie.frame.refresh();
        });
        await(() -> Lizzie.frame.currentVariationTreeImage() != null, 5, "published actual tree");
        SwingUtilities.invokeAndWait(() -> {
          var view = Lizzie.frame.currentVariationTreeImage().view();
          Lizzie.frame.onClicked(view.x() + 25, view.y() + view.height() / 2 + 25);
        });
        await(() -> Lizzie.board.getHistory().getCurrentHistoryNode() == pass, 5, "actual tree click");
        SwingUtilities.invokeAndWait(() -> Lizzie.board.moveToAnyPosition(historical));
        assertSame(historical, Lizzie.board.getHistory().getCurrentHistoryNode());
        assertEquals(navigationCommands, commands(work), "browsing sends no GTP at all");
        int oldPlayouts = historical.getData().getPlayouts();
        var oldMoves = new ArrayList<>(historical.getData().bestMoves);
        ImageIO.write(robot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())),
            "png", work.resolve("fox-history.png").toFile());
        String fourth = "3^" + whiteX + "^15^W";
        send(peer, "FAMOVE,1,B," + fourth);
        peer.setSoTimeout(15000);
        assertEquals("AFSTATUS,B", receive(peer));
        send(peer, "FASTATUS,1,B," + tuple + "," + white + "," + fourth,
            "FATIMELEFT,57,30,3", "FAMOVE,1,B," + fourth);
        String browsingSubmission = receive(peer);
        assertTrue(browsingSubmission.startsWith("AFPLAY,B,4^") || browsingSubmission.equals("AFSKIP,B"), browsingSubmission);
        assertSame(historical, Lizzie.board.getHistory().getCurrentHistoryNode(), "live messages preserve display");
        assertEquals(4, Lizzie.board.getHistory().getMainEnd().getData().moveNumber);
        assertEquals(oldPlayouts, historical.getData().getPlayouts());
        assertEquals(oldMoves, historical.getData().bestMoves);
        assertFalse(find(fox, JTextArea.class).getText().contains("%"));
        String beforeReturn = commands(work);
        SwingUtilities.invokeAndWait(() -> button(fox, "Fox.returnLive").doClick());
        await(() -> Lizzie.board.getHistory().getMoveNumber() == 4, 5, "return to current live tail");
        assertTrue(find(fox, JTextArea.class).getText().contains("%")
            || find(fox, JTextArea.class).getText().contains(Lizzie.resourceBundle.getString("Fox.waitingCandidate")));
        assertEquals(beforeReturn, commands(work));
        SwingUtilities.invokeAndWait(() -> Lizzie.board.moveToAnyPosition(historical));
        Files.writeString(work.resolve("history-evidence.txt"), "navigationCommands=unchanged\nreturnCommands=unchanged\n"
            + "displayMove=2 liveMove=4\ncacheUnchanged=true\nautomaticWhileBrowsing=" + browsingSubmission + "\n");
        send(peer, "FARESULT,W+R", "FASTATUS,0");
        await(() -> !target.hasExclusiveGtpWorkInProgress(), 35, "confirmed terminal handback");
        assertTrue(target.isLoaded());
        SwingUtilities.invokeAndWait(() -> assertTrue(Lizzie.board.previousMove(false)));
        send(peer, "FARULE,19,60,30,3,650", "FASTATUS,1,B", "FATIMELEFT,60,30,3", "FAMOVE,1,B,0^0^0^N");
        peer.setSoTimeout(5000);
        assertEquals("AFSTATUS,B", receive(peer));
        await(() -> target.hasExclusiveGtpLease() && Lizzie.board.getHistory().getMoveNumber() == 0,
            15, "new game independent admission");
        send(peer, "FASTATUS,1,B", "FATIMELEFT,59,30,3", "FAMOVE,1,B,0^0^0^N");
        assertTrue(receive(peer).startsWith("AFPLAY,B,1^"));
        assertEquals(0, Lizzie.board.getHistory().getMoveNumber());
        await(() -> hasText(fox, "Fox.confirmationFailed") && !target.hasExclusiveGtpWorkInProgress(),
            15, "unconfirmed request visibly stops and restores");
        send(peer, "FASTATUS,1,B", "FATIMELEFT,58,30,3", "FAMOVE,1,B,0^0^0^N");
        peer.setSoTimeout(300);
        assertThrows(SocketTimeoutException.class, () -> peer.getInputStream().read(), "failure never resends");
        assertTrue(hasText(fox, "Fox.confirmationFailed"), "query retains failure notice");
        ImageIO.write(new Robot().createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())),
            "png", work.resolve("fox-unconfirmed.png").toFile());
      }
      await(() -> !target.hasExclusiveGtpWorkInProgress(), 35, "disconnect handback");
      try (Socket reconnected = new Socket("127.0.0.1", port)) {
        send(reconnected, "FARULE,19,60,30,3,650", "FASTATUS,1,B,1^16^3^B",
            "FATIMELEFT,60,30,3", "FAMOVE,0,B,1^16^3^B");
        await(() -> find(fox, JTextArea.class).getText().contains(
            Lizzie.resourceBundle.getString("Fox.incomplete")), 5, "cold reconnect blocked");
        assertFalse(target.hasExclusiveGtpLease());
        assertSame(originalDisplay, Lizzie.board.getHistory().getCurrentHistoryNode(),
            "incomplete observation retains pre-entry stable board");
        reconnected.setSoTimeout(250);
        assertThrows(SocketTimeoutException.class, () -> reconnected.getInputStream().read(), "blocked has no send");
        send(reconnected, "FASTATUS,0", "FARULE,19,60,30,3,650", "FASTATUS,1,B",
            "FATIMELEFT,60,30,3", "FAMOVE,1,B,0^0^0^N");
        reconnected.setSoTimeout(5000);
        assertEquals("AFSTATUS,B", receive(reconnected));
        send(reconnected, "FASTATUS,1,B", "FATIMELEFT,59,30,3", "FAMOVE,1,B,0^0^0^N");
        await(() -> find(fox, JTextArea.class).getText().contains("%"), 25, "new connection search candidates");
        String lastSubmission = receive(reconnected);
        assertTrue(lastSubmission.startsWith("AFPLAY,B,1^"), lastSubmission);
        send(reconnected, "FAMOVE,0,B," + lastSubmission.substring("AFPLAY,B,".length()));
        await(() -> Lizzie.board.getHistory().getMoveNumber() == 1, 5, "last confirmed live move");
        await(() -> find(fox, JTextArea.class).getText().contains("%"), 15, "last opponent search");
        BoardHistoryNode exitDisplay = Lizzie.board.getHistory().root();
        SwingUtilities.invokeAndWait(() -> Lizzie.board.navigateToNode(exitDisplay));
        await(() -> Lizzie.board.getHistory().getCurrentHistoryNode() == exitDisplay, 5, "historical Stop target");
        String beforeStop = commands(work);
        SwingUtilities.invokeAndWait(() -> {
          button(fox, "Fox.stop").doClick();
          assertTrue(FoxLiveSession.rejectOrdinaryMutation(), "ordinary commands blocked during handback");
        });
        await(() -> !FoxLiveSession.isActive() && !target.hasExclusiveGtpWorkInProgress(),
            35, "active explicit stop handback");
        assertSame(exitDisplay, Lizzie.board.getHistory().getCurrentHistoryNode());
        String restoredCommands = commands(work).substring(beforeStop.length());
        assertTrue(restoredCommands.contains("clear_board"), restoredCommands);
        assertFalse(restoredCommands.lines().anyMatch(line -> line.matches(".*Controller: (?:[0-9]+ )?play [BW] .*")),
            "handback restores displayed root, not the live MOVE");
        ImageIO.write(new Robot().createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())),
            "png", work.resolve("fox-history-stopped.png").toFile());
        Files.writeString(work.resolve("history-stop-gtp.txt"), restoredCommands);
      }
      assertTrue(target.isLoaded());
      assertTrue(target.beginExclusiveGtpLifecycleTransition());
      target.endExclusiveGtpLifecycleTransition();
      String handback = gtp(work);
      assertTrue(handback.contains("kata-time_settings none"));
      int reset = handback.lastIndexOf("kata-time_settings none");
      assertTrue(handback.substring(reset).matches("(?s)kata-time_settings none\\R[^\\n]*: =[^\\n]*\\R.*Controller:.*name.*"),
          "reset response precedes final handback fence");
      DesktopTimeControl.applyEngineGameTime(target, DesktopTimeControl.SideMode.ENGINE_OWNED, 0, "");
      target.sendCommand("kata-debug-print-tc");
      target.sendCommand("137001 name");
      await(() -> gtp(work).contains("=137001 KataGo"), 5, "post-stop engine-owned clock observation");
      Files.writeString(work.resolve("post-stop-time-control.txt"), gtp(work).substring(reset));
      var clocks = java.util.regex.Pattern.compile(
          "(Black|White) originalMainTime ([^ ]+) mainTimeLeft ([^ ]+) inOvertime ([01])")
          .matcher(gtp(work).substring(reset));
      int observedClocks = 0;
      while (clocks.find()) {
        assertTrue(Double.parseDouble(clocks.group(2)) >= 1e20);
        assertTrue(Double.parseDouble(clocks.group(3)) >= 1e20);
        assertEquals("0", clocks.group(4), "engine-owned mode has no Fox overtime");
        observedClocks++;
      }
      assertEquals(2, observedClocks);
      Files.writeString(result, "PASS Swing TCP one KataGo same-search candidates final automatic send pending no-preadvance confirmed timely-sync opponent init blocked no-send terminal disconnect Stop restore\n"
          + "engine=" + args[0] + "\nmodel=" + args[1] + "\nprocess=" + ProcessHandle.current().pid() + "\n");
      target.sendCommand("quit");
      System.exit(0);
    } catch (Throwable failure) {
      Files.writeString(result, "FAIL " + failure + "\n");
      failure.printStackTrace();
      if (engine != null) engine.sendCommand("quit");
      System.exit(1);
    }
  }

  private static String commands(Path work) {
    return gtp(work).lines().filter(line -> line.contains("Controller:"))
        .collect(java.util.stream.Collectors.joining("\n"));
  }

  private static JButton literalButton(Container parent, String label) {
    for (Component child : parent.getComponents()) {
      if (child instanceof JButton button && label.equals(button.getText())) return button;
      if (child instanceof Container container) {
        JButton found = literalButton(container, label);
        if (found != null) return found;
      }
    }
    return null;
  }
  private static String receive(Socket peer) throws Exception {
    java.io.ByteArrayOutputStream frame = new java.io.ByteArrayOutputStream();
    int value;
    while ((value = peer.getInputStream().read()) != -1) {
      frame.write(value);
      if (value == '\n') break;
    }
    List<String> messages = new ArrayList<>();
    new FoxProtocol().accept(frame.toByteArray(), frame.size(), messages::add);
    assertEquals(1, messages.size());
    System.out.println("Fox peer received " + messages.get(0) + " at " + System.nanoTime());
    return messages.get(0);
  }

  private static boolean hasText(Container parent, String key) {
    String text = Lizzie.resourceBundle.getString(key);
    for (Component child : parent.getComponents()) {
      if (child instanceof JLabel label && label.getText().equals(text)) return true;
      if (child instanceof Container c && hasText(c, key)) return true;
    }
    return false;
  }

  private static String gtp(Path work) {
    try (var paths = Files.list(work.resolve("gtp-log"))) {
      StringBuilder result = new StringBuilder();
      for (Path path : paths.toList()) result.append(Files.readString(path));
      return result.toString();
    } catch (Exception failure) { throw new AssertionError(failure); }
  }

  private static String quote(String value) { return "\"" + value + "\""; }
  private static void send(Socket peer, String... bodies) throws Exception {
    for (String body : bodies) {
      byte[] frame = FoxProtocol.encode(body);
      peer.getOutputStream().write(frame, 0, 3);
      peer.getOutputStream().write(frame, 3, frame.length - 3);
    }
    peer.getOutputStream().flush();
  }
  private static JDialog dialog() {
    for (Window w : Window.getWindows()) if (w instanceof JDialog d
        && d.getTitle().equals(Lizzie.resourceBundle.getString("Fox.title"))) return d;
    throw new AssertionError("Fox dialog missing");
  }
  private static JDialog modal() {
    for (Window w : Window.getWindows()) if (w instanceof JDialog d && d.isModal() && d.isShowing()) return d;
    return null;
  }
  private static JButton button(Container parent, String key) {
    String text = Lizzie.resourceBundle.getString(key);
    for (Component child : parent.getComponents()) {
      if (child instanceof JButton b && b.getText().equals(text)) return b;
      if (child instanceof Container c) { JButton b = buttonOrNull(c, text); if (b != null) return b; }
    }
    throw new AssertionError("Button missing: " + key);
  }
  private static JButton buttonOrNull(Container parent, String text) {
    for (Component child : parent.getComponents()) {
      if (child instanceof JButton b && b.getText().equals(text)) return b;
      if (child instanceof Container c) { JButton b = buttonOrNull(c, text); if (b != null) return b; }
    }
    return null;
  }
  private static <T> T find(Container parent, Class<T> type) {
    if (type.isInstance(parent)) return type.cast(parent);
    for (Component child : parent.getComponents()) {
      if (type.isInstance(child)) return type.cast(child);
      if (child instanceof Container c) { T value = find(c, type); if (value != null) return value; }
    }
    return null;
  }
  private static void await(BooleanSupplier condition, int seconds, String detail) throws Exception {
    long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
    while (System.nanoTime() < end) { if (condition.getAsBoolean()) return; Thread.sleep(50); }
    throw new AssertionError(detail);
  }
  private static final class NamedEngine extends Leelaz {
    NamedEngine(EngineData entry) throws Exception {
      super(entry.commands); savedEntryId = entry.id; width = height = oriWidth = oriHeight = 19;
      komi = orikomi = 6.5f;
    }
    @Override public String getEngineName(int index) { return "Fox real KataGo acceptance"; }
  }
}
