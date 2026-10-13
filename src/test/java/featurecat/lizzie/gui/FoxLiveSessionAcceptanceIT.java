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
        send(peer, "FARULE,19,60,30,3,650", "FASTATUS,1,B", "FATIMELEFT,60,30,3", "FAMOVE,1,B,0^0^0^N");
        await(() -> find(fox, JTextArea.class).getText().contains("%"), 25, "real current candidates");
        assertTrue(target.hasExclusiveGtpLease());
        assertFalse(target.hasForegroundAnalysisLeaseWorkInProgress());
        long engineCount = ProcessHandle.current().descendants()
            .filter(p -> p.info().command().orElse("").endsWith("katago")).count();
        assertEquals(1, engineCount);
        try (Socket duplicate = new Socket("127.0.0.1", port)) {
          duplicate.setSoTimeout(1000);
          assertEquals(-1, duplicate.getInputStream().read());
        }
        send(peer, "FAMOVE,0,B,1^16^3^B", "FAMOVE,1,B,2^3^15^W", "FASKIP,0,B");
        await(() -> Lizzie.board.getHistory().getMoveNumber() == 3, 15, "MOVE MOVE PASS history");
        BoardHistoryNode tail = Lizzie.board.getHistory().getCurrentHistoryNode();
        assertTrue(tail.getData().isPassNode());
        SwingUtilities.invokeAndWait(() -> {
          assertFalse(Lizzie.board.previousMove(false));
          Lizzie.board.navigateToNode(Lizzie.board.getHistory().root());
          Lizzie.board.moveToAnyPosition(Lizzie.board.getHistory().root());
          assertSame(tail, Lizzie.board.getHistory().getCurrentHistoryNode());
          assertFalse(SGFParser.loadFromString("(;SZ[9];B[aa])"));
        });
        assertFalse(target.beginExclusiveGtpLifecycleTransition());
        send(peer, "FASTATUS,1,B,1^16^3^B,2^3^15^W", "FATIMELEFT,59,30,3", "FAMOVE,0,B,2^3^15^W");
        await(() -> find(fox, JTextArea.class).getText().contains("%"), 25, "post-pass candidates");
        assertEquals(3, Lizzie.board.getHistory().getMoveNumber());
        ImageIO.write(new Robot().createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())),
            "png", work.resolve("fox-live.png").toFile());
        assertThrows(SocketTimeoutException.class, () -> peer.getInputStream().read(), "preview sends no move bytes");
        send(peer, "FARESULT,W+R", "FASTATUS,0");
        await(() -> !target.hasExclusiveGtpWorkInProgress(), 35, "confirmed terminal handback");
        assertTrue(target.isLoaded());
        SwingUtilities.invokeAndWait(() -> assertTrue(Lizzie.board.previousMove(false)));
        send(peer, "FARULE,19,60,30,3,650", "FASTATUS,1,B", "FATIMELEFT,60,30,3", "FAMOVE,1,B,0^0^0^N");
        await(() -> target.hasExclusiveGtpLease() && Lizzie.board.getHistory().getMoveNumber() == 0,
            15, "new game independent admission");
      }
      await(() -> !target.hasExclusiveGtpWorkInProgress(), 35, "disconnect handback");
      try (Socket reconnected = new Socket("127.0.0.1", port)) {
        send(reconnected, "FARULE,19,60,30,3,650", "FASTATUS,1,B,1^16^3^B",
            "FATIMELEFT,60,30,3", "FAMOVE,0,B,1^16^3^B");
        await(() -> find(fox, JTextArea.class).getText().contains(
            Lizzie.resourceBundle.getString("Fox.incomplete")), 5, "cold reconnect blocked");
        assertFalse(target.hasExclusiveGtpLease());
        send(reconnected, "FASTATUS,0", "FARULE,19,60,30,3,650", "FASTATUS,1,B",
            "FATIMELEFT,60,30,3", "FAMOVE,1,B,0^0^0^N");
        await(() -> find(fox, JTextArea.class).getText().contains("%"), 25, "new connection new game candidates");
        SwingUtilities.invokeAndWait(() -> button(fox, "Fox.stop").doClick());
        await(() -> !FoxLiveSession.isActive() && !target.hasExclusiveGtpWorkInProgress(),
            35, "active explicit stop handback");
      }
      assertTrue(target.isLoaded());
      assertTrue(target.beginExclusiveGtpLifecycleTransition());
      target.endExclusiveGtpLifecycleTransition();
      Files.writeString(result, "PASS Swing TCP KataGo candidates MOVE PASS duplicate navigation conflicts terminal new-game disconnect stop restore no-outbound-moves\n"
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
