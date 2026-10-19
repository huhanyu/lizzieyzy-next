package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.*;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.Leelaz;
import featurecat.lizzie.fox.FoxLiveSession;
import featurecat.lizzie.fox.FoxProtocol;
import featurecat.lizzie.rules.GIBParser;
import featurecat.lizzie.rules.SGFParser;
import featurecat.lizzie.util.Utils;
import java.awt.*;
import java.awt.datatransfer.*;
import java.net.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/** Production mutation entrypoints under a real Swing/TCP/KataGo owner. */
public final class FoxMutationAdmissionAcceptanceIT {
  @Test
  void ordinaryMutationsWaitForConfirmedFoxHandback() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("fox.smoke"));
    DesktopProbeProcess.requireDisplay();
    Path result =
        DesktopProbeProcess.run(
            FoxMutationAdmissionAcceptanceIT.class,
            "fox-mutations",
            List.of(),
            List.of(System.getProperty("fox.engine"), System.getProperty("fox.model")),
            180);
    assertEquals("PASS", Files.readString(result).trim());
  }

  public static void main(String[] args) throws Exception {
    Path work = Path.of(args[2]);
    Path result = Path.of(args[3]);
    Leelaz engine = null;
    int exit = 0;
    try {
      JSONObject config =
          new JSONObject()
              .put("leelaz", new JSONObject().put("engine-settings-list", new JSONArray()))
              .put(
                  "ui",
                  new JSONObject()
                      .put("autoload-empty", true)
                      .put("first-time-load", false)
                      .put("use-language", 2)
                      .put("auto-quick-analyze-on-load", false));
      Files.writeString(work.resolve("config.txt"), config.toString(2));
      Path cfg = work.resolve("engine.cfg");
      Files.writeString(
          cfg,
          "rules = chinese\nkomi = 6.5\nnumSearchThreads = 2\nmaxVisits = 64\n"
              + "maxTime = 3\nponderingEnabled = false\nlogAllGTPCommunication = true\n"
              + "logSearchInfo = true\nlogDir = "
              + work.resolve("gtp-log")
              + "\n");
      Path gib = work.resolve("foreign.gib");
      Files.writeString(gib, "\\[GAMEINFOMAIN=GONGJE:75,\\]\nINI 0 0 0\nSTO 0 0 1 3 3\n");
      System.setProperty("lizzie.work.dir", work.toString());
      Lizzie.main(new String[0]);
      await(() -> Lizzie.frame != null && Lizzie.frame.isShowing(), "main window");
      SwingUtilities.invokeAndWait(
          () -> {
            for (Window window : Window.getWindows())
              if (window instanceof Dialog) window.dispose();
          });
      EngineData entry = new EngineData();
      entry.id = UUID.randomUUID().toString();
      entry.index = Lizzie.engineManager.engineList.size();
      entry.commands = "\"" + args[0] + "\" gtp -model \"" + args[1] + "\" -config \"" + cfg + "\"";
      entry.name = "Fox mutation real KataGo";
      entry.width = entry.height = 19;
      entry.komi = 6.5f;
      Utils.saveEngineSettings(new ArrayList<>(List.of(entry)));
      engine = new NamedEngine(entry);
      Lizzie.engineManager.engineList.add(engine);
      assertTrue(Lizzie.engineManager.switchEngineIfAvailable(entry.index, true));
      Leelaz target = engine;
      await(() -> target.isLoaded() && !target.hasExclusiveGtpWorkInProgress(), "engine ready");
      target.notPondering();
      Thread.sleep(300);
      SwingUtilities.invokeAndWait(FoxLiveSession::showDialog);
      JDialog fox = foxDialog();
      await(() -> button(fox, "Fox.start").isEnabled(), "confirmed current engine limits");
      int port;
      try (ServerSocket available = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
        port = available.getLocalPort();
      }
      int selectedPort = port;
      SwingUtilities.invokeAndWait(() -> find(fox, JSpinner.class).setValue(selectedPort));
      SwingUtilities.invokeLater(() -> button(fox, "Fox.start").doClick());
      await(() -> modal() != null, "rules confirmation");
      SwingUtilities.invokeAndWait(
          () -> {
            JDialog modal = modal();
            find(modal, JOptionPane.class).setValue(JOptionPane.OK_OPTION);
            modal.dispose();
          });
      await(() -> button(fox, "Fox.stop").isEnabled(), "listener ready");
      try (Socket peer = new Socket("127.0.0.1", port)) {
        peer.setSoTimeout(20000);
        send(
            peer,
            "FARULE,19,60,30,3,650",
            "FASTATUS,1,B",
            "FATIMELEFT,60,30,3",
            "FAMOVE,1,B,0^0^0^N");
        assertEquals("AFSTATUS,B", receive(peer));
        send(peer, "FASTATUS,1,B", "FATIMELEFT,59,30,3", "FAMOVE,1,B,0^0^0^N");
        String submission = receive(peer);
        assertTrue(submission.startsWith("AFPLAY,B,1^"), submission);
        assertEquals(0, Lizzie.board.getHistory().getMoveNumber());
        rejectedMutations(gib, "pending");
        send(
            peer, "FAMOVE,0,B," + submission.substring("AFPLAY,B,".length()), "FATIMELEFT,58,30,3");
        await(() -> Lizzie.board.getHistory().getMoveNumber() == 1, "confirmed move");
        await(() -> !Lizzie.frame.getBestMoves().isEmpty(), "real candidates");
        Thread.sleep(3500);
        String commands = commands(work);
        rejectedMutations(gib, "live");
        Thread.sleep(200);
        assertEquals(commands, commands(work), "rejected edits issue no GTP");
        screenshot(work, "live-rejected.png");
        peer.shutdownOutput();
        await(() -> hasText(fox, "Fox.disconnected"), "retained observation");
        assertTrue(target.hasExclusiveGtpLease());
        rejectedMutations(gib, "retained");
        SwingUtilities.invokeAndWait(
            () -> {
              button(fox, "Fox.stop").doClick();
              assertTrue(hasText(fox, "Fox.restoring"));
              rejectOnEdt(gib, "restoring");
            });
        await(
            () -> button(fox, "Fox.start").isEnabled() && !target.hasExclusiveGtpWorkInProgress(),
            "confirmed Stop");
        SwingUtilities.invokeAndWait(
            () -> {
              try {
                Lizzie.frame.tryPlay(true);
                assertTrue(Lizzie.frame.isTrying);
                Lizzie.frame.tryPlay(true);
                assertFalse(Lizzie.frame.isTrying);
                GameInfoDialog info = editedInfo();
                info.apply();
                info.dispose();
                assertEquals(7.5, Lizzie.board.getHistory().getGameInfo().getKomi());
                assertEquals(
                    "foreign black", Lizzie.board.getHistory().getGameInfo().getPlayerBlack());
                Lizzie.config.readKomi = true;
                assertTrue(Lizzie.frame.loadFile(gib.toFile(), false, false));
                assertEquals("Player 2", Lizzie.board.getHistory().getGameInfo().getPlayerBlack());
                assertEquals(7.5, Lizzie.board.getHistory().getGameInfo().getKomi());
              } catch (Exception failure) {
                throw new RuntimeException(failure);
              }
            });
        screenshot(work, "post-stop-ordinary.png");
        System.out.println(
            "PASS pending/live/retained/restoring rejection; confirmed Stop restores trial/info/GIB");
        Files.writeString(result, "PASS");
      }
    } catch (Throwable failure) {
      failure.printStackTrace();
      Files.writeString(result, "FAIL " + failure);
      exit = 1;
    } finally {
      if (engine != null) engine.sendCommand("quit");
      Thread.sleep(250);
      System.exit(exit);
    }
  }

  private static void rejectedMutations(Path gib, String phase) throws Exception {
    SwingUtilities.invokeAndWait(() -> rejectOnEdt(gib, phase));
  }

  private static void rejectOnEdt(Path gib, String phase) {
    try {
      var history = Lizzie.board.getHistory();
      var node = history.getCurrentHistoryNode();
      String saved = SGFParser.saveToString(false);
      String title = Lizzie.frame.getTitle();
      var file = Lizzie.frame.curFile;
      boolean sound = Lizzie.config.playSound;
      GameInfoDialog info = editedInfo();
      // An already-created editor must recheck admission at Apply, not only at open.
      Lizzie.frame.tryPlay(true);
      assertFalse(Lizzie.frame.isTrying, phase);
      assertFalse(Lizzie.frame.loadFile(gib.toFile(), false, false), phase);
      assertFalse(GIBParser.load(gib.toString()), phase);
      assertFalse(
          Lizzie.frame.loadSgfString("(;SZ[19]KM[7.5];B[dd])", 0, true, false, null), phase);
      assertFalse(
          Lizzie.frame.importDroppedKifuFiles(
              new Transferable() {
                public DataFlavor[] getTransferDataFlavors() {
                  return new DataFlavor[] {DataFlavor.javaFileListFlavor};
                }

                public boolean isDataFlavorSupported(DataFlavor flavor) {
                  return DataFlavor.javaFileListFlavor.equals(flavor);
                }

                public Object getTransferData(DataFlavor flavor) {
                  return List.of(gib.toFile());
                }
              }),
          phase);
      Lizzie.frame.openFile();
      LizzieFrame.editGameInfo();
      info.apply();
      info.dispose();
      assertSame(history, Lizzie.board.getHistory(), phase);
      assertSame(node, history.getCurrentHistoryNode(), phase);
      assertEquals(saved, SGFParser.saveToString(false), phase);
      assertEquals(title, Lizzie.frame.getTitle(), phase);
      assertSame(file, Lizzie.frame.curFile, phase);
      assertEquals(sound, Lizzie.config.playSound, phase);
      System.out.println(
          "REJECTED "
              + phase
              + " move="
              + node.getData().moveNumber
              + " komi="
              + history.getGameInfo().getKomi());
    } catch (Exception failure) {
      throw new RuntimeException(failure);
    }
  }

  private static GameInfoDialog editedInfo() throws Exception {
    GameInfoDialog info = new GameInfoDialog();
    info.setGameInfo(Lizzie.board.getHistory().getGameInfo());
    for (String[] field :
        new String[][] {
          {"textFieldKomi", "7.5"},
          {"textFieldBlack", "foreign black"},
          {"textFieldWhite", "foreign white"}
        }) {
      var member = GameInfoDialog.class.getDeclaredField(field[0]);
      member.setAccessible(true);
      ((JTextField) member.get(info)).setText(field[1]);
    }
    return info;
  }

  private static String commands(Path work) throws Exception {
    StringBuilder text = new StringBuilder();
    try (var paths = Files.list(work.resolve("gtp-log"))) {
      for (Path path : paths.toList()) text.append(Files.readString(path));
    }
    return text.toString()
        .lines()
        .filter(line -> line.contains("Controller:"))
        .collect(java.util.stream.Collectors.joining("\n"));
  }

  private static void screenshot(Path work, String name) throws Exception {
    ImageIO.write(
        new Robot().createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())),
        "png",
        work.resolve(name).toFile());
  }

  private static void await(BooleanSupplier condition, String label) throws Exception {
    long end = System.nanoTime() + 40_000_000_000L;
    while (System.nanoTime() < end) {
      if (condition.getAsBoolean()) return;
      Thread.sleep(25);
    }
    throw new AssertionError(label);
  }

  private static <T> T find(Container parent, Class<T> type) {
    if (type.isInstance(parent)) return type.cast(parent);
    for (Component child : parent.getComponents()) {
      if (type.isInstance(child)) return type.cast(child);
      if (child instanceof Container) {
        T found = find((Container) child, type);
        if (found != null) return found;
      }
    }
    return null;
  }

  private static JButton button(Container parent, String key) {
    for (Component child : parent.getComponents()) {
      if (child instanceof JButton
          && ((JButton) child).getText().equals(Lizzie.resourceBundle.getString(key)))
        return (JButton) child;
      if (child instanceof Container) {
        JButton found = button((Container) child, key);
        if (found != null) return found;
      }
    }
    return null;
  }

  private static boolean hasText(Container parent, String key) {
    for (Component child : parent.getComponents()) {
      if (child instanceof JLabel
          && ((JLabel) child).getText().equals(Lizzie.resourceBundle.getString(key))) return true;
      if (child instanceof Container && hasText((Container) child, key)) return true;
    }
    return false;
  }

  private static JDialog foxDialog() {
    for (Window window : Window.getWindows())
      if (window instanceof JDialog
          && ((JDialog) window).getTitle().equals(Lizzie.resourceBundle.getString("Fox.title")))
        return (JDialog) window;
    throw new AssertionError("Fox dialog");
  }

  private static JDialog modal() {
    for (Window window : Window.getWindows())
      if (window instanceof JDialog && ((JDialog) window).isModal() && window.isShowing())
        return (JDialog) window;
    return null;
  }

  private static void send(Socket peer, String... messages) throws Exception {
    for (String message : messages) {
      peer.getOutputStream().write(FoxProtocol.encode(message));
      System.out.println("IN " + message);
    }
    peer.getOutputStream().flush();
  }

  private static String receive(Socket peer) throws Exception {
    var bytes = new java.io.ByteArrayOutputStream();
    int value;
    do {
      value = peer.getInputStream().read();
      if (value < 0) throw new java.io.EOFException();
      bytes.write(value);
    } while (value != '\n');
    var messages = new ArrayList<String>();
    new FoxProtocol().accept(bytes.toByteArray(), bytes.size(), messages::add);
    assertEquals(1, messages.size());
    System.out.println("OUT " + messages.get(0));
    return messages.get(0);
  }

  private static final class NamedEngine extends Leelaz {
    NamedEngine(EngineData entry) throws Exception {
      super(entry.commands);
      savedEntryId = entry.id;
      width = height = oriWidth = oriHeight = 19;
      komi = orikomi = 6.5f;
    }

    @Override
    public String getEngineName(int index) {
      return "Fox mutation real KataGo";
    }
  }
}
