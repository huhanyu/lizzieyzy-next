package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.*;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.Leelaz;
import featurecat.lizzie.analysis.KataGoRules;
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
  @Test void actualDirectStartQueriesCurrentRulesWithoutRulesWindow() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("fox.smoke"));
    DesktopProbeProcess.requireDisplay();
    Path result = DesktopProbeProcess.run(FoxLiveSessionAcceptanceIT.class, "fox-rules-preflight",
        List.of("-Dfox.rules.preflight=true"),
        List.of(System.getProperty("fox.engine"), System.getProperty("fox.model")), 90);
    assertTrue(Files.readString(result).contains("PASS"));
  }

  @Test void actualPreparationSurvivesDisconnectAndFencesStoppedOwner() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("fox.smoke"));
    DesktopProbeProcess.requireDisplay();
    Path result = DesktopProbeProcess.run(FoxLiveSessionAcceptanceIT.class, "fox-prepare-recovery",
        List.of("-Dfox.prepare.recovery=true"),
        List.of(System.getProperty("fox.engine"), System.getProperty("fox.model")), 120);
    assertTrue(Files.readString(result).contains("PASS"));
  }

  @Test void actualApplicationFollowsConfirmedFoxActionsAndRestores() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("fox.smoke"));
    DesktopProbeProcess.requireDisplay();
    Path result = DesktopProbeProcess.run(FoxLiveSessionAcceptanceIT.class, "fox-live",
        List.of(), List.of(System.getProperty("fox.engine"), System.getProperty("fox.model")), 180);
    assertTrue(Files.readString(result).contains("PASS"));
  }

  @Test void actualToolbarRefusesDynamicPdaOverride() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("fox.smoke"));
    DesktopProbeProcess.requireDisplay();
    Path result = DesktopProbeProcess.run(FoxLiveSessionAcceptanceIT.class, "fox-pda-dynamic",
        List.of("-Dfox.pda.dynamic=true"),
        List.of(System.getProperty("fox.engine"), System.getProperty("fox.model")), 90);
    assertTrue(Files.readString(result).contains("PASS"));
  }

  @Test void actualToolbarRefusesWrappedStaticConfiguration() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("fox.smoke"));
    DesktopProbeProcess.requireDisplay();
    Path result = DesktopProbeProcess.run(FoxLiveSessionAcceptanceIT.class, "fox-pda-wrapper",
        List.of("-Dfox.pda.wrapper=true"),
        List.of(System.getProperty("fox.engine"), System.getProperty("fox.model")), 90);
    assertTrue(Files.readString(result).contains("PASS"));
  }

  @Test void actualRecoveredKoPreservesEngineLegality() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("fox.smoke"));
    DesktopProbeProcess.requireDisplay();
    Path result = DesktopProbeProcess.run(FoxLiveSessionAcceptanceIT.class, "fox-ko",
        List.of("-Dfox.ko.smoke=true"),
        List.of(System.getProperty("fox.engine"), System.getProperty("fox.model")), 90);
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
          + "playoutDoublingAdvantage = 0.2\nogsChatToStderr = true\n"
          + (Boolean.getBoolean("fox.pda.dynamic") ? "dynamicPlayoutDoublingAdvantageCapPerOppLead = 0.045\n" : "")
          + "logAllGTPCommunication = true\nlogSearchInfo = true\nlogDir = "
          + work.resolve("gtp-log") + "\n");
      if (Boolean.getBoolean("fox.rules.preflight")) {
        config.getJSONObject("leelaz").put("engine-settings-list", new JSONArray().put(new JSONObject()
            .put("name", "Fox cold-start KataGo")
            .put("command", quote(args[0]) + " gtp -model " + quote(args[1])
                + " -config " + quote(engineConfig.toString()))
            .put("preload", false).put("isDefault", true).put("width", 19).put("height", 19)
            .put("komi", 6.5).put("useJavaSSH", false)));
        config.getJSONObject("ui").put("autoload-empty", false).put("autoload-default", true)
            .put("autoload-last", false).put("default-engine", 0);
        Files.writeString(work.resolve("config.txt"), config.toString(2));
      }
      System.setProperty("lizzie.work.dir", work.toString());
      Lizzie.main(new String[0]);
      await(() -> Lizzie.frame != null && Lizzie.frame.isShowing(), 30, "main Swing window");
      SwingUtilities.invokeAndWait(() -> {
        for (Window window : Window.getWindows()) if (window instanceof Dialog) window.dispose();
      });
      if (Boolean.getBoolean("fox.rules.preflight")) {
        await(() -> Lizzie.leelaz != null && Lizzie.leelaz.isLoaded()
            && !Lizzie.leelaz.hasExclusiveGtpWorkInProgress(), 60, "default KataGo ready");
        engine = Lizzie.leelaz;
        engine.notPondering();
        engine.sendCommand("stop");
        directRulesPreflight(engine, work);
        Files.writeString(result, "PASS direct dialog queries current rules without opening rules window\n");
        engine.sendCommand("quit");
        System.exit(0);
      }
      EngineData entry = new EngineData();
      entry.id = UUID.randomUUID().toString();
      entry.index = Lizzie.engineManager.engineList.size();
      String executable = args[0];
      if (Boolean.getBoolean("fox.pda.wrapper")) {
        Path wrapper = work.resolve("wrapper.sh");
        Files.writeString(wrapper, "#!/bin/sh\nexec " + quote(args[0])
            + " \"$@\" -override-config dynamicPlayoutDoublingAdvantageCapPerOppLead=0\n");
        assertTrue(wrapper.toFile().setExecutable(true));
        executable = wrapper.toString();
      }
      entry.commands = quote(executable) + " gtp -model " + quote(args[1]) + " -config " + quote(engineConfig.toString());
      entry.name = "Fox real KataGo acceptance";
      entry.width = entry.height = 19;
      entry.komi = 6.5f;
      Utils.saveEngineSettings(new ArrayList<>(List.of(entry)));
      engine = Boolean.getBoolean("fox.prepare.recovery") ? new PreparationGateEngine(entry) : new NamedEngine(entry);
      Lizzie.engineManager.engineList.add(engine);
      assertTrue(Lizzie.engineManager.switchEngineIfAvailable(entry.index, true));
      Leelaz target = engine;
      await(() -> target.isLoaded() && !target.hasExclusiveGtpWorkInProgress(), 60, "current KataGo ready");
      target.notPondering();
      target.sendCommand("stop");
      Thread.sleep(500);
      KataGoRules customRules = KataGoRules.parse("chinese").orElseThrow()
          .overlayEditor("AREA", "SITUATIONAL", false, "ALL", "N-1", false);
      for (KataGoRules selected : List.of(KataGoRules.parse("japanese").orElseThrow(), customRules)) {
        var operation = target.applyEngineRulesOperation(selected);
        await(operation::isDone, 10, "real current rule set/readback");
        assertTrue(operation.snapshot().isConfirmed());
        assertTrue(selected.semanticallyEquals(operation.snapshot().observed()));
      }
      SwingUtilities.invokeAndWait(() -> Lizzie.board.getHistory().publishManualRules(customRules));
      assertThreads(target, work);
      if (Boolean.getBoolean("fox.prepare.recovery")) {
        preparationRecovery((PreparationGateEngine) target, work);
        Files.writeString(result, "PASS preparation recovery, own-turn submission and stopped-owner fencing\n");
        target.sendCommand("quit");
        System.exit(0);
      }
      if (Boolean.getBoolean("fox.ko.smoke")) {
        realKoReplay(target, work);
        Files.writeString(result, "PASS real recovered ko legality\n");
        target.sendCommand("quit");
        System.exit(0);
      }
      BoardHistoryNode originalDisplay = Lizzie.board.getHistory().getCurrentHistoryNode();
      SwingUtilities.invokeAndWait(() -> {
        JMenuBar bar = LizzieFrame.menu;
        int found = 0;
        for (int m = 0; m < bar.getMenuCount(); m++) {
          JMenu menu = bar.getMenu(m);
          if (menu == null) continue;
          for (Component child : menu.getMenuComponents()) {
            if (child instanceof JMenuItem item && item.getText().equals(Lizzie.resourceBundle.getString("Fox.title"))) {
              assertEquals(Lizzie.resourceBundle.getString("Menu.sync"), menu.getText());
              found++;
              item.doClick();
            }
          }
        }
        assertEquals(1, found, "sole sync-menu entry");
      });
      JDialog fox = dialog();
      await(() -> button(fox, "Fox.start").isEnabled(), 10, "confirmed current engine limits");
      SwingUtilities.invokeAndWait(() -> {
        assertEquals("3.0", limitField(fox, "fox-max-time").getText());
        assertEquals("64", limitField(fox, "fox-max-visits").getText());
        limitField(fox, "fox-max-time").setText("10");
        limitField(fox, "fox-max-visits").setText("128");
      });
      capture(fox, work.resolve("fox-limits-before-start.png"));
      SwingUtilities.invokeAndWait(() -> {
        JSpinner spinner = find(fox, JSpinner.class);
        JFormattedTextField editor = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
        assertEquals("7000", editor.getText());
        for (String invalid : List.of("7,000", "7000.5", "0", "65536"))
          assertThrows(java.text.ParseException.class, () -> editor.getFormatter().stringToValue(invalid));
        assertEquals(7000, editor.getValue());
        assertNull(find(fox, JComboBox.class), "no independent rules selector");
        assertFalse(find(fox, JTextArea.class).isEditable(), "communication has no command input");
        assertTrue(readOnlyRules(fox).getText().contains("tax=ALL"));
      });
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
      var logging = featurecat.lizzie.logging.LoggingRuntime.current().orElseThrow();
      assertFalse(logging.fullTraceActive(), "opening and starting the panel never enable disk full trace");
      logging.startFullTrace(java.util.Set.of(featurecat.lizzie.logging.TraceScope.ENGINE_GTP));
      SwingUtilities.invokeAndWait(() -> Lizzie.frame.togglePonderMannul());
      await(() -> target.isPondering() && Lizzie.board.getData().getPlayouts() > 0,
          15, "ordinary search before Fox ownership");
      try (Socket peer = new Socket("127.0.0.1", port)) {
        peer.setSoTimeout(200);
        send(peer, "FARULE,19,60,30,3,650", "FASTATUS,1,B", "FATIMELEFT,60,30,3");
        assertThrows(SocketTimeoutException.class, () -> peer.getInputStream().read(), "no send before authoritative turn");
        long turnStart = System.nanoTime();
        send(peer, "FAMOVE,1,B,0^0^0^N");
        peer.setSoTimeout(15000);
        assertEquals("AFSTATUS,B", receive(peer));
        send(peer, "FASTATUS,1,B", "FATIMELEFT,59,30,3", "FAMOVE,1,B,0^0^0^N");
        await(() -> !Lizzie.frame.getBestMoves().isEmpty(), 25, "real current candidates");
        assertEquals(0.2, target.pda, 1e-9, "initial PDA preserved through acquisition");
        String beforePda = commands(work);
        SwingUtilities.invokeAndWait(() -> {
          if (!LizzieFrame.menu.chkPDA.isSelected()) LizzieFrame.menu.chkPDA.doClick();
          LizzieFrame.menu.txtGfPDA.setText("0.4");
          LizzieFrame.menu.txtGfPDA.setText("0.8");
        });
        if (Boolean.getBoolean("fox.pda.dynamic") || Boolean.getBoolean("fox.pda.wrapper")) {
          String reason = Boolean.getBoolean("fox.pda.wrapper") ? "Fox.pdaUnknown" : "Fox.pdaDynamic";
          await(() -> hasText(fox, reason), 5, "visible unsupported PDA refusal");
          assertEquals(0.2, target.pda, 1e-9);
          assertFalse(commands(work).substring(beforePda.length()).contains("kata-set-param playoutDoublingAdvantage"));
          SwingUtilities.invokeAndWait(() -> assertEquals("0.2", LizzieFrame.menu.txtGfPDA.getText()));
          String move = receive(peer);
          String rejectedEditCommands = commands(work).substring(beforePda.length());
          assertFalse(rejectedEditCommands.contains(" stop"), "rejected edit keeps original search");
          assertFalse(rejectedEditCommands.contains("kata-search_analyze_cancellable"), "no replacement search");
          assertFalse(rejectedEditCommands.contains("kata-set-param playoutDoublingAdvantage"));
          send(peer, "FAMOVE,0,B," + move.substring("AFPLAY,B,".length()));
          await(() -> Lizzie.board.getHistory().getMoveNumber() == 1, 5, "refused update retains safe search");
          capture(fox, work.resolve("fox-pda-dynamic-refused.png"));
          SwingUtilities.invokeAndWait(() -> button(fox, "Fox.stop").doClick());
          await(() -> !FoxLiveSession.ownsEngine() && !target.hasExclusiveGtpWorkInProgress(), 15, "dynamic handback");
          assertFalse(target.isPondering());
          assertEquals(0.2, target.pda, 1e-9);
          Files.writeString(work.resolve("pda-refused.txt"), "reason=" + reason
              + "\nlaunch=" + entry.commands + "\nrefused=true\nconfirmed=0.2\npaused=true\n" + gtp(work));
          Files.writeString(result, "PASS actual Swing toolbar " + reason
              + " refusal; safe original search and confirmed PDA retained\n");
          target.sendCommand("quit");
          System.exit(0);
        }
        await(() -> Math.abs(target.pda - 0.8) < 1e-9, 5, "latest real PDA confirmation");
        await(() -> commands(work).substring(beforePda.length()).contains("kata-search_analyze_cancellable"), 5, "same-turn PDA re-search");
        await(() -> !Lizzie.frame.getBestMoves().isEmpty(), 10, "new PDA current candidates");
        await(() -> find(fox, JTextArea.class).getText().contains("< info move"), 5, "raw analysis stream");
        assertTrue(find(fox, JTextArea.class).getText().contains("> "));
        assertTrue(find(fox, JTextArea.class).getText().contains("< ="));
        assertTrue(find(fox, JTextArea.class).getText().contains(customRules.toGtpArgument()));
        assertFalse(find(fox, JSpinner.class).isEnabled());
        assertFalse(limitField(fox, "fox-max-time").isEnabled());
        assertFalse(limitField(fox, "fox-max-visits").isEnabled());
        assertBreadth(false);
        assertTrue(hasText(fox, "Fox.breadthUnavailable"));
        String candidates = find(fox, JTextArea.class).getText();
        SwingUtilities.invokeAndWait(() -> {
          assertFalse(Lizzie.frame.getBestMoves().isEmpty(), "current Fox candidates reach ordinary table");
          assertTrue(Lizzie.frame.listTable.getRowCount() > 0, "actual candidate table has live rows");
          assertEquals(Lizzie.frame.getBestMoves().get(0).coordinate,
              Lizzie.frame.listTable.getValueAt(0, 1).toString());
          Lizzie.frame.boardRenderer.selectNormalVariation();
          assertTrue(Lizzie.frame.boardRenderer.bestMoveCoordinateName().isPresent(),
              "current Fox candidates reach main-board renderer");
          assertTrue(Lizzie.board.getData().bestMoves.isEmpty(), "live results never enter node cache");
        });
        captureMain(work.resolve("fox-main-live.png"));
        String submission = receive(peer);
        long submitted = System.nanoTime();
        assertTrue(submission.startsWith("AFPLAY,B,1^"), submission);
        String pdaSearch = commands(work).substring(beforePda.length());
        assertTrue(pdaSearch.indexOf(" stop") < pdaSearch.indexOf("kata-set-param playoutDoublingAdvantage 0.8"), pdaSearch);
        await(() -> engineStderr(target).contains("PDA -0.80") || engineStderr(target).contains("PDA 0.80"),
            5, "actual search parameters rather than setter/readback");
        Files.writeString(work.resolve("pda-static.txt"), "initial=0.2\nconfirmed=0.8\n" + pdaSearch + "\n" + engineStderr(target));
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
        assertEquals(2, beforeConfirmation.lines().filter(line -> line.contains("Controller:")
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
        await(() -> !Lizzie.frame.getBestMoves().isEmpty(), 15, "opponent candidates");
        assertFalse(target.applyEngineRulesOperation(KataGoRules.parse("japanese").orElseThrow()).accepted(),
            "rules cannot change while Fox owns engine");
        SwingUtilities.invokeAndWait(fox::toFront);
        capture(fox, work.resolve("fox-current-rules-stream.png"));
        Files.writeString(work.resolve("current-rules-stream.txt"), find(fox, JTextArea.class).getText());
        await(() -> log(work.resolve("logs/app.log")).contains("outcome=CONFIRMED"),
            5, "production on-disk submission confirmation");
        String durable = log(work.resolve("logs/app.log"));
        for (String field : List.of("fox search", "reader=", "clock=", "deadlineNanos=",
            "elapsedSinceClockNanos=", "remainingNanos=", "searchLimitSeconds=", "fox selection",
            "completedNanos=", "fox write", "writtenNanos=", "resolvedNanos=", "connection=", "game=", "revision=", "search="))
          assertTrue(durable.contains(field), "durable field: " + field);
        assertTrue(durable.contains("turnStartedNanos=") && durable.contains("elapsedTurnNanos="));
        String confirmedEvent = durable.lines().filter(line -> line.contains("fox submission")
            && line.contains("outcome=CONFIRMED")).findFirst().orElseThrow();
        String selectedEvent = durable.lines().filter(line -> line.contains("fox selection"))
            .findFirst().orElseThrow();
        for (String identity : List.of("connection", "game", "revision", "search"))
          assertEquals(number(selectedEvent, identity), number(confirmedEvent, identity));
        assertTrue(selectedEvent.contains("vertex=" + vertex));
        assertTrue(number(selectedEvent, "completedNanos") <= number(confirmedEvent, "preparedNanos"));
        assertTrue(number(confirmedEvent, "preparedNanos") <= number(confirmedEvent, "writtenNanos"));
        assertTrue(number(confirmedEvent, "writtenNanos") < number(confirmedEvent, "deadlineNanos"));
        assertTrue(number(confirmedEvent, "writtenNanos") <= number(confirmedEvent, "resolvedNanos"));
        await(() -> log(work.resolve("logs/engine-trace.log"))
            .contains("kata-search_analyze_cancellable"), 5, "production exclusive GTP trace");
        String trace = log(work.resolve("logs/engine-trace.log"));
        assertTrue(trace.contains("gtp raw stream=info move"), "exclusive candidate stream traced");
        assertTrue(trace.contains("gtp raw stream=play " + vertex), "exclusive final choice traced");
        var searchCommands = trace.lines().filter(line -> line.contains("gtp raw command=")
            && line.contains("kata-search_analyze_cancellable")).map(line -> line.substring(line.indexOf("gtp raw"))).toList();
        assertEquals(searchCommands.size(), searchCommands.stream().distinct().count(), "no duplicate trace events");
        Files.writeString(work.resolve("durable-first-turn.txt"), durable);
        assertThrows(SocketTimeoutException.class, () -> peer.getInputStream().read(), "opponent never submits");
        String previewCommands = commands(work);
        SwingUtilities.invokeAndWait(() -> {
          Lizzie.frame.setMouseOverCoords(0);
          Lizzie.frame.refresh();
        });
        await(() -> Lizzie.frame.boardRenderer.branchOpt.isPresent(), 5, "live candidate ordinary preview");
        captureMain(work.resolve("fox-main-preview.png"));
        assertEquals(previewCommands, commands(work), "preview does not request another search");
        SwingUtilities.invokeAndWait(() -> Lizzie.frame.clearMouseOverCoordinate(false));
        String floatCommands = commands(work);
        String floatCandidate = Lizzie.frame.getBestMoves().get(0).coordinate;
        SwingUtilities.invokeAndWait(() -> {
          Lizzie.frame.onlyIndependMainBoard();
          Lizzie.frame.independentMainBoard.setBounds(400, 180, 620, 620);
          assertTrue(Lizzie.config.isFloatBoardMode());
          assertTrue(Lizzie.frame.independentMainBoard.isVisible());
          assertTrue(Lizzie.board.getData().bestMoves.isEmpty(), "Fox display never fills node cache");
        });
        new Robot().delay(500);
        SwingUtilities.invokeAndWait(() -> {
          Lizzie.frame.input.keyPressed(new java.awt.event.KeyEvent(Lizzie.frame,
              java.awt.event.KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
              java.awt.event.KeyEvent.VK_1, '1'));
          Lizzie.frame.refresh();
          Lizzie.frame.independentMainBoard.getRootPane().paintImmediately(0, 0,
              Lizzie.frame.independentMainBoard.getWidth(), Lizzie.frame.independentMainBoard.getHeight());
        });
        new Robot().delay(300);
        Toolkit.getDefaultToolkit().sync();
        ImageIO.write(new Robot().createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())),
            "png", work.resolve("fox-float-number-preview.png").toFile());
        Files.writeString(work.resolve("float-number-preview.txt"), "candidate=" + floatCandidate
            + "\nselected=" + java.util.Arrays.toString(Lizzie.frame.independentMainBoard.mouseOverCoordinate)
            + "\nnodeCacheSize=" + Lizzie.board.getData().bestMoves.size()
            + "\ncommandsUnchanged=" + floatCommands.equals(commands(work)) + "\n");
        assertEquals(floatCommands, commands(work), "floating number preview does not restart search");
        assertArrayEquals(featurecat.lizzie.rules.Board.convertNameToCoordinates(floatCandidate),
            Lizzie.frame.independentMainBoard.mouseOverCoordinate, "floating number key selects live candidate");
        assertTrue(Lizzie.frame.independentMainBoard.boardRenderer.branchOpt.isPresent(),
            "floating number key presents live PV");
        BoardHistoryNode floatLiveNode = Lizzie.board.getHistory().getCurrentHistoryNode();
        SwingUtilities.invokeAndWait(() -> {
          Lizzie.frame.setMouseOverCoords(0);
          assertArrayEquals(LizzieFrame.outOfBoundCoordinate,
              Lizzie.frame.independentMainBoard.mouseOverCoordinate, "same number toggles preview off");
          Lizzie.board.navigateToNode(floatLiveNode.previous().orElseThrow());
          Lizzie.frame.setMouseOverCoords(0);
          assertTrue(Lizzie.frame.getBestMoves().isEmpty());
          assertArrayEquals(LizzieFrame.outOfBoundCoordinate,
              Lizzie.frame.independentMainBoard.mouseOverCoordinate, "history cannot select live PV");
          Lizzie.frame.independentMainBoard.boardRenderer.selectNormalVariation();
          assertFalse(Lizzie.frame.independentMainBoard.boardRenderer.bestMoveCoordinateName().isPresent());
        });
        SwingUtilities.invokeAndWait(() -> Lizzie.frame.independentMainBoard.getRootPane().paintImmediately(
            0, 0, Lizzie.frame.independentMainBoard.getWidth(), Lizzie.frame.independentMainBoard.getHeight()));
        ImageIO.write(new Robot().createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())),
            "png", work.resolve("fox-float-history.png").toFile());
        SwingUtilities.invokeAndWait(() -> button(fox, "Fox.returnLive").doClick());
        assertSame(floatLiveNode, Lizzie.board.getHistory().getCurrentHistoryNode());
        assertEquals(floatCommands, commands(work), "floating navigation and return do not restart search");
        SwingUtilities.invokeAndWait(() -> {
          Lizzie.frame.toggleOnlyIndependMainBoard();
          Lizzie.frame.clearMouseOverCoordinate(false);
        });
        ImageIO.write(new Robot().createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())),
            "png", work.resolve("fox-confirmed.png").toFile());
        Files.writeString(work.resolve("timing.txt"), "turnStart=" + turnStart + "\nsubmitted=" + submitted
            + "\nconfirmed=" + confirmed + "\nengineSynchronized=" + synchronizedAt
            + "\nsubmission=" + submission + "\ncandidatesBeforeFinal=\n" + candidates);
        // A peer-observed pass is evidence, never an invented engine choice.
        int whiteX = Integer.parseInt(fields[1]) == 2 && Integer.parseInt(fields[2]) == 16 ? 3 : 2;
        String white = "2^" + whiteX + "^16^W";
        peer.setSoTimeout(5000);
        send(peer, "FAMOVE,1,B," + white);
        assertEquals("AFSTATUS,B", receive(peer));
        // Observe the pass before refreshing the clock, so this turn cannot submit a choice.
        send(peer, "FASTATUS,1,B," + tuple + "," + white, "FAMOVE,1,B," + white,
            "FASKIP,0,B", "FATIMELEFT,58,30,3");
        await(() -> Lizzie.board.getHistory().getMoveNumber() == 3, 5, "MOVE MOVE PASS history");
        BoardHistoryNode pass = Lizzie.board.getHistory().getCurrentHistoryNode();
        BoardHistoryNode historical = pass.previous().orElseThrow();
        assertTrue(pass.getData().isPassNode());
        await(() -> gtp(work).contains("play B pass"), 10, "real pass replay");
        await(() -> !Lizzie.frame.getBestMoves().isEmpty(), 15, "pass-position candidate");
        String navigationCommands = commands(work);
        Controller[] controls = new Controller[1];
        SwingUtilities.invokeAndWait(() -> {
          controls[0] = new Controller(Lizzie.frame);
          controls[0].setVisible(true);
          literalButton(controls[0], "<").doClick();
        });
        assertSame(historical, Lizzie.board.getHistory().getCurrentHistoryNode());
        assertTrue(hasText(fox, "Fox.browsing"));
        assertDisplayCandidates(false);
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
        boolean[] treeClicked = {false};
        for (int attempt = 0; attempt < 100 && !treeClicked[0]; attempt++) {
          SwingUtilities.invokeAndWait(() -> {
            var image = Lizzie.frame.currentVariationTreeImage();
            if (image == null) return;
            var view = image.view();
            Lizzie.frame.onClicked(view.x() + 25, view.y() + view.height() / 2 + 25);
            treeClicked[0] = true;
          });
          if (!treeClicked[0]) Thread.sleep(50);
        }
        assertTrue(treeClicked[0], "published actual tree clicked on EDT");
        await(() -> Lizzie.board.getHistory().getCurrentHistoryNode() == pass, 5, "actual tree click");
        SwingUtilities.invokeAndWait(() -> Lizzie.board.moveToAnyPosition(historical));
        assertSame(historical, Lizzie.board.getHistory().getCurrentHistoryNode());
        assertDisplayCandidates(false);
        assertEquals(navigationCommands, commands(work), "browsing sends no GTP at all");
        int oldPlayouts = historical.getData().getPlayouts();
        var oldMoves = new ArrayList<>(historical.getData().bestMoves);
        robot.waitForIdle();
        robot.delay(200);
        ImageIO.write(robot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())),
            "png", work.resolve("fox-history.png").toFile());
        SwingUtilities.invokeAndWait(() -> fox.toFront());
        robot.waitForIdle();
        robot.delay(100);
        ImageIO.write(robot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())),
            "png", work.resolve("fox-history-controls.png").toFile());
        String fourth = "3^" + whiteX + "^15^W";
        send(peer, "FAMOVE,1,B," + fourth);
        peer.setSoTimeout(15000);
        assertEquals("AFSTATUS,B", receive(peer));
        SwingUtilities.invokeAndWait(() -> button(fox, "Fox.returnLive").doClick());
        await(() -> Lizzie.board.getHistory().getMoveNumber() == 4, 5, "return while waiting");
        assertTrue(Lizzie.frame.getBestMoves().isEmpty(), "no old candidate flicker");
        assertDisplayCandidates(false);
        SwingUtilities.invokeAndWait(() -> Lizzie.board.moveToAnyPosition(historical));
        send(peer, "FASTATUS,1,B," + tuple + "," + white + "," + fourth,
            "FATIMELEFT,57,30,3", "FAMOVE,1,B," + fourth);
        String browsingSubmission = receive(peer);
        assertTrue(browsingSubmission.startsWith("AFPLAY,B,4^") || browsingSubmission.equals("AFSKIP,B"), browsingSubmission);
        assertSame(historical, Lizzie.board.getHistory().getCurrentHistoryNode(), "live messages preserve display");
        assertEquals(4, Lizzie.board.getHistory().getMainEnd().getData().moveNumber);
        assertEquals(oldPlayouts, historical.getData().getPlayouts());
        assertEquals(oldMoves, historical.getData().bestMoves);
        assertTrue(Lizzie.frame.getBestMoves().isEmpty());
        assertDisplayCandidates(false);
        String beforeReturn = commands(work);
        SwingUtilities.invokeAndWait(() -> button(fox, "Fox.returnLive").doClick());
        await(() -> Lizzie.board.getHistory().getMoveNumber() == 4, 5, "return to current live tail");
        assertFalse(Lizzie.frame.getBestMoves().isEmpty(), "current valid candidate immediately returns");
        assertTrue(hasText(fox, "Fox.pending"));
        assertEquals(beforeReturn, commands(work));
        assertDisplayCandidates(true);
        captureMain(work.resolve("fox-main-return-current.png"));
        SwingUtilities.invokeAndWait(() -> Lizzie.board.moveToAnyPosition(historical));
        if (browsingSubmission.startsWith("AFPLAY"))
          send(peer, "FAMOVE,0,B," + browsingSubmission.substring("AFPLAY,B,".length()));
        else send(peer, "FASKIP,0,B");
        await(() -> Lizzie.board.getHistory().getMainEndWithPass().getData().moveNumber == 5,
            5, "automatic choice confirmed while browsing");
        assertSame(historical, Lizzie.board.getHistory().getCurrentHistoryNode());
        Files.writeString(work.resolve("history-evidence.txt"), "navigationCommands=unchanged\nreturnCommands=unchanged\n"
            + "displayMove=2 liveMove=5\ncacheUnchanged=true\nautomaticWhileBrowsing=" + browsingSubmission + "\n");
        send(peer, "FASCORE");
        await(() -> hasText(fox, "Fox.officialAction"), 5, "official request before terminal");
        assertTrue(target.hasExclusiveGtpLease());
        String beforeTerminal = commands(work);
        send(peer, "FARESULT,W+R", "FASTATUS,0");
        await(() -> !target.hasExclusiveGtpWorkInProgress(), 35, "confirmed terminal handback");
        assertTrue(target.isLoaded());
        assertSame(historical, Lizzie.board.getHistory().getCurrentHistoryNode());
        assertPausedHandbackAndExplicitAnalysis(target, work, beforeTerminal, "terminal");
        SwingUtilities.invokeAndWait(() -> assertTrue(Lizzie.board.previousMove(false)));
        int navigationFence = gtp(work).length();
        target.sendCommand("name");
        await(() -> gtp(work).substring(navigationFence).contains("= KataGo"),
            5, "ordinary navigation settled before next Fox game");
        send(peer, "FARULE,19,60,30,3,650", "FASTATUS,1,B", "FATIMELEFT,60,30,3", "FAMOVE,1,B,0^0^0^N");
        peer.setSoTimeout(5000);
        assertEquals("AFSTATUS,B", receive(peer));
        await(() -> target.hasExclusiveGtpLease() && Lizzie.board.getHistory().getMoveNumber() == 0,
            15, "new game independent admission");
        send(peer, "FASTATUS,1,B", "FATIMELEFT,59,30,3", "FAMOVE,1,B,0^0^0^N");
        assertTrue(receive(peer).startsWith("AFPLAY,B,1^"));
        assertEquals(0, Lizzie.board.getHistory().getMoveNumber());
        await(() -> hasText(fox, "Fox.confirmationFailed") && target.hasExclusiveGtpLease(),
            15, "unconfirmed request stops automation while retaining display owner");
        send(peer, "FASTATUS,1,B", "FATIMELEFT,58,30,3", "FAMOVE,1,B,0^0^0^N");
        peer.setSoTimeout(300);
        assertThrows(SocketTimeoutException.class, () -> peer.getInputStream().read(), "failure never resends");
        assertTrue(hasText(fox, "Fox.confirmationFailed"), "query retains failure notice");
        ImageIO.write(new Robot().createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())),
            "png", work.resolve("fox-unconfirmed.png").toFile());
        String failedCommands = commands(work);
        var failedHistory = Lizzie.board.getHistory();
        send(peer, "FARULE,19,60,30,3,650", "FASTATUS,1,B", "FATIMELEFT,58,30,3",
            "FAMOVE,1,B,0^0^0^N");
        peer.setSoTimeout(1500);
        assertThrows(SocketTimeoutException.class, () -> peer.getInputStream().read(),
            "same-socket reinit cannot issue another query or submission");
        assertTrue(hasText(fox, "Fox.confirmationFailed"));
        assertSame(failedHistory, Lizzie.board.getHistory());
        assertTrue(target.hasExclusiveGtpLease());
        assertEquals(failedCommands, commands(work), "reinit cannot restart or replace held owner work");
        capture(fox, work.resolve("fox-reinit-blocked.png"));
        send(peer, "FASTATUS,0");
        await(() -> !target.hasExclusiveGtpWorkInProgress(), 35, "idle confirms failed-owner handback");
        String idleHandback = commands(work).substring(failedCommands.length());
        assertTrue(idleHandback.contains("kata-time_settings none"));
        send(peer, "FARULE,19,60,30,3,650", "FASTATUS,1,B", "FATIMELEFT,60,30,3",
            "FAMOVE,1,B,0^0^0^N");
        peer.setSoTimeout(15000);
        assertEquals("AFSTATUS,B", receive(peer));
        send(peer, "FASTATUS,1,B", "FATIMELEFT,59,30,3", "FAMOVE,1,B,0^0^0^N");
        String freshSubmission = receive(peer);
        assertTrue(freshSubmission.startsWith("AFPLAY,B,1^"), freshSubmission);
        assertNotSame(failedHistory, Lizzie.board.getHistory());
        assertTrue(target.hasExclusiveGtpLease());
        String freshCommands = commands(work).substring(failedCommands.length());
        assertTrue(freshCommands.indexOf("kata-time_settings none")
            < freshCommands.lastIndexOf("kgs-time_settings"), freshCommands);
        Files.writeString(work.resolve("reinit-idle-new-game-gtp.txt"), gtp(work));
        capture(fox, work.resolve("fox-reinit-fresh-game.png"));
        await(() -> hasText(fox, "Fox.confirmationFailed"), 15, "fresh pending also remains unconfirmed");
      }
      await(() -> hasText(fox, "Fox.disconnected"), 5, "disconnect invalidation");
      try (Socket reconnected = new Socket("127.0.0.1", port)) {
        send(reconnected, "FARULE,19,60,30,3,650", "FASTATUS,1,B,1^16^3^B",
            "FATIMELEFT,60,30,3", "FAMOVE,0,B,1^16^3^B");
        await(() -> hasText(fox, "Fox.recoveryUnconfirmed"), 5, "unconfirmed reconnect blocked");
        assertTrue(target.hasExclusiveGtpLease());
        assertEquals(0, Lizzie.board.getHistory().getMoveNumber(),
            "incoming incomplete observation never replaces confirmed pre-submit root");
        reconnected.setSoTimeout(250);
        assertThrows(SocketTimeoutException.class, () -> reconnected.getInputStream().read(), "blocked has no send");
        ImageIO.write(new Robot().createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())),
            "png", work.resolve("fox-reconnect-unconfirmed.png").toFile());
        send(reconnected, "FASTATUS,0", "FARULE,19,60,30,3,650", "FASTATUS,1,B",
            "FATIMELEFT,60,30,3", "FAMOVE,1,B,0^0^0^N");
        reconnected.setSoTimeout(5000);
        assertEquals("AFSTATUS,B", receive(reconnected));
        send(reconnected, "FASTATUS,1,B", "FATIMELEFT,59,30,3", "FAMOVE,1,B,0^0^0^N");
        await(() -> !Lizzie.frame.getBestMoves().isEmpty(), 25, "new connection search candidates");
        String lastSubmission = receive(reconnected);
        assertTrue(lastSubmission.startsWith("AFPLAY,B,1^"), lastSubmission);
        send(reconnected, "FAMOVE,0,B," + lastSubmission.substring("AFPLAY,B,".length()));
        await(() -> Lizzie.board.getHistory().getMoveNumber() == 1, 5, "last confirmed live move");
        await(() -> !Lizzie.frame.getBestMoves().isEmpty(), 15, "last opponent search");
        send(reconnected, "FASCORE");
        await(() -> hasText(fox, "Fox.officialAction"), 5, "stable official action instruction");
        send(reconnected, "FASTATUS,1,B," + lastSubmission.substring("AFPLAY,B,".length()),
            "FATIMELEFT,58,30,3", "FAMOVE,0,B," + lastSubmission.substring("AFPLAY,B,".length()));
        reconnected.setSoTimeout(500);
        assertThrows(SocketTimeoutException.class, () -> reconnected.getInputStream().read(),
            "official score request never sends score, pass or move");
        String scoreCommands = commands(work);
        BoardHistoryNode exitDisplay = Lizzie.board.getHistory().root();
        SwingUtilities.invokeAndWait(() -> Lizzie.board.navigateToNode(exitDisplay));
        await(() -> Lizzie.board.getHistory().getCurrentHistoryNode() == exitDisplay, 5, "historical Stop target");
        SwingUtilities.invokeAndWait(() -> button(fox, "Fox.returnLive").doClick());
        SwingUtilities.invokeAndWait(() -> Lizzie.board.navigateToNode(exitDisplay));
        assertTrue(hasText(fox, "Fox.officialAction"));
        assertEquals(scoreCommands, commands(work), "official request queries and navigation never resume search");
        assertEquals(1, Lizzie.board.getHistory().getMainEnd().getData().moveNumber);
        assertTrue(target.hasExclusiveGtpLease());
        capture(fox, work.resolve("fox-score-after-query-navigation.png"));
        Files.writeString(work.resolve("score-status.txt"), "officialAction=" + hasText(fox, "Fox.officialAction"));
        Files.writeString(work.resolve("score-gtp.txt"), gtp(work));
        String beforeStop = commands(work);
        SwingUtilities.invokeAndWait(() -> {
          button(fox, "Fox.stop").doClick();
          assertTrue(FoxLiveSession.rejectOrdinaryMutation(), "ordinary commands blocked during handback");
          assertFalse(LizzieFrame.menu.chkWRN.isEnabled());
          assertFalse(LizzieFrame.menu.txtWRN.isEnabled());
        });
        await(() -> !FoxLiveSession.isActive() && !target.hasExclusiveGtpWorkInProgress(),
            35, "active explicit stop handback");
        assertSame(exitDisplay, Lizzie.board.getHistory().getCurrentHistoryNode());
        assertFalse(FoxLiveSession.isBrowsingHistory());
        String restoredCommands = commands(work).substring(beforeStop.length());
        assertTrue(restoredCommands.contains("clear_board"), restoredCommands);
        assertFalse(restoredCommands.lines().anyMatch(line -> line.matches(".*Controller: (?:[0-9]+ )?play [BW] .*")),
            "handback restores displayed root, not the live MOVE");
        SwingUtilities.invokeAndWait(() -> {
          Lizzie.frame.getRootPane().paintImmediately(0, 0, Lizzie.frame.getWidth(), Lizzie.frame.getHeight());
          fox.getRootPane().paintImmediately(0, 0, fox.getWidth(), fox.getHeight());
        });
        ImageIO.write(new Robot().createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())),
            "png", work.resolve("fox-history-stopped.png").toFile());
        Files.writeString(work.resolve("history-stop-gtp.txt"), restoredCommands);
        assertPausedHandbackAndExplicitAnalysis(target, work, beforeStop, "direct-stop");
      }
      retainedReconnect(fox, port, target, work);
      unconfirmedRetainedStop(fox, port, target, work);
      naturalPassReconnect(fox, port, target, work);
      directLimitCases(fox, port, target, work);
      assertEquals(0.8, target.pda, 1e-9, "latest PDA survives paused handback and original limit restore");
      SwingUtilities.invokeAndWait(() -> {
        assertEquals("0.8", LizzieFrame.menu.txtGfPDA.getText());
        assertEquals("0.8", Lizzie.config.txtKataEnginePDA);
      });
      assertTrue(target.isLoaded());
      assertThreads(target, work);
      assertTrue(customRules.semanticallyEquals(Lizzie.board.getHistory().captureSessionRules().parsedRules().orElseThrow()));
      assertTrue(target.beginExclusiveGtpLifecycleTransition());
      target.endExclusiveGtpLifecycleTransition();
      String handback = gtp(work);
      assertTrue(handback.contains("kata-time_settings none"));
      int reset = handback.lastIndexOf("kata-time_settings none");
      assertTrue(handback.substring(reset).matches("(?s)kata-time_settings none\\R[^\\n]*: =[^\\n]*\\R.*Controller:.*name.*"),
          "reset response precedes final handback fence");
      int clockFenceStart = gtp(work).length();
      DesktopTimeControl.applyEngineGameTime(target, DesktopTimeControl.SideMode.ENGINE_OWNED, 0, "");
      target.sendCommand("kata-debug-print-tc");
      target.sendCommand("name");
      await(() -> gtp(work).substring(clockFenceStart).contains("= KataGo"), 5, "post-stop engine-owned clock observation");
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
      if (target.isPondering()) SwingUtilities.invokeAndWait(() -> Lizzie.frame.togglePonderMannul());
      SwingUtilities.invokeAndWait(() -> Lizzie.frame.togglePonderMannul());
      await(() -> Lizzie.board.getData().getPlayouts() > 0, 15, "ordinary analysis after confirmed handback");
      SwingUtilities.invokeAndWait(() -> assertSame(Lizzie.board.getData().bestMoves, Lizzie.frame.getBestMoves()));
      captureMain(work.resolve("fox-ordinary-analysis-restored.png"));
      target.notPondering();
      int pauseFenceStart = gtp(work).length();
      target.sendCommand("name");
      await(() -> gtp(work).substring(pauseFenceStart).contains("= KataGo"), 5, "ordinary pause fence");
      assertFalse(target.isPondering());
      SwingUtilities.invokeAndWait(() -> {
        Lizzie.frame.onlyIndependMainBoard();
        var ordinaryMoves = Lizzie.board.getData().bestMoves;
        int index = java.util.stream.IntStream.range(0, Math.min(9, ordinaryMoves.size()))
            .filter(i -> !ordinaryMoves.get(i).coordinate.equalsIgnoreCase("pass")).findFirst().orElseThrow();
        Lizzie.frame.input.keyPressed(new java.awt.event.KeyEvent(Lizzie.frame,
            java.awt.event.KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
            java.awt.event.KeyEvent.VK_1 + index, (char) ('1' + index)));
        assertArrayEquals(featurecat.lizzie.rules.Board.convertNameToCoordinates(ordinaryMoves.get(index).coordinate),
            Lizzie.frame.independentMainBoard.mouseOverCoordinate, "ordinary paused numeric preview after Fox handback");
        Lizzie.frame.setMouseOverCoords(index);
        assertArrayEquals(LizzieFrame.outOfBoundCoordinate, Lizzie.frame.independentMainBoard.mouseOverCoordinate);
        Lizzie.frame.toggleOnlyIndependMainBoard();
      });
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
  private static void directRulesPreflight(Leelaz target, Path work) throws Exception {
    String before = commands(work);
    Files.writeString(work.resolve("rules-before-dialog.txt"), target.engineRulesResult().status().toString());
    SwingUtilities.invokeAndWait(FoxLiveSession::showDialog);
    JDialog fox = dialog();
    await(() -> target.engineRulesResult().isConfirmed() && button(fox, "Fox.start").isEnabled(),
        10, "direct dialog confirms current rules without separate rules window");
    KataGoRules observed = target.engineRulesResult().observed();
    assertTrue(KataGoRules.parse("chinese").orElseThrow().semanticallyEquals(observed));
    SwingUtilities.invokeAndWait(() -> assertEquals(observed.fieldSummary(), readOnlyRules(fox).getText()));
    assertFalse(commands(work).substring(before.length()).contains("kata-set-rules"),
        "preflight observes engine rules without changing them");
    capture(fox, work.resolve("fox-rules-preflight-ready.png"));
    // A parsed, confirmed response can still omit fields required by a live session.
    var rulesResult = Leelaz.class.getDeclaredField("engineRulesResult");
    rulesResult.setAccessible(true);
    rulesResult.set(target, target.engineRulesResult().confirmed(
        KataGoRules.parse("{\"ko\":\"POSITIONAL\"}").orElseThrow()));
    await(() -> !button(fox, "Fox.start").isEnabled(), 2,
        "incomplete confirmed rules must disable direct Start");
    SwingUtilities.invokeAndWait(() -> {
      fox.setVisible(false);
      FoxLiveSession.showDialog();
    });
    await(() -> target.engineRulesResult().observed().hasRequiredFields()
        && button(fox, "Fox.start").isEnabled(), 10,
        "reopening direct dialog retries incomplete rules and restores readiness");
    int port;
    try (ServerSocket available = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      port = available.getLocalPort();
    }
    int selectedPort = port;
    SwingUtilities.invokeAndWait(() -> find(fox, JSpinner.class).setValue(selectedPort));
    startPreparationCase(fox);
    try (Socket peer = new Socket("127.0.0.1", port)) {
      sendSetupOpponentTurn(peer);
      await(() -> !Lizzie.frame.getBestMoves().isEmpty(), 15, "cold direct start produces opponent-turn candidates");
      capture(fox, work.resolve("fox-rules-preflight-active.png"));
      SwingUtilities.invokeAndWait(() -> button(fox, "Fox.stop").doClick());
      await(() -> !FoxLiveSession.ownsEngine() && !target.hasExclusiveGtpWorkInProgress(),
          15, "cold direct start confirmed handback");
      assertFalse(target.isPondering());
    }
    Files.writeString(work.resolve("rules-preflight-gtp.txt"), gtp(work));
  }


  private static String engineStderr(Leelaz target) {
    try {
      var field = Leelaz.class.getDeclaredField("recentStderrLines");
      field.setAccessible(true);
      @SuppressWarnings("unchecked")
      var lines = (java.util.ArrayDeque<String>) field.get(target);
      synchronized (lines) { return String.join("\n", lines); }
    } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
  }

  private static long number(String line, String key) {
    var match = java.util.regex.Pattern.compile("\\b" + key + "=(\\d+)").matcher(line);
    assertTrue(match.find(), key + " missing: " + line);
    return Long.parseLong(match.group(1));
  }

  private static String log(Path file) {
    try { return Files.exists(file) ? Files.readString(file) : ""; }
    catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
  }

  private static void assertDisplayCandidates(boolean present) throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      assertEquals(present, !Lizzie.frame.getBestMoves().isEmpty());
      assertEquals(present, Lizzie.frame.listTable.getRowCount() > 0);
      Lizzie.frame.boardRenderer.selectNormalVariation();
      assertEquals(present, Lizzie.frame.boardRenderer.bestMoveCoordinateName().isPresent());
    });
  }

  private static void captureMain(Path image) throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      Lizzie.frame.toFront();
      Lizzie.frame.refresh();
    });
    Robot robot = new Robot();
    robot.delay(200);
    ImageIO.write(robot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())),
        "png", image.toFile());
  }

  private static void assertBreadth(boolean available) throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      assertEquals(available, LizzieFrame.menu.chkWRN.isEnabled());
      assertEquals(available && LizzieFrame.menu.chkWRN.isSelected(),
          LizzieFrame.menu.txtWRN.isEnabled());
    });
  }

  private static void assertPausedHandbackAndExplicitAnalysis(
      Leelaz target, Path work, String before, String scenario) throws Exception {
    await(() -> !FoxLiveSession.ownsEngine(), 5, scenario + " UI handback");
    assertBreadth(true);
    String restored = commands(work).substring(before.length());
    assertTrue(restored.contains("kata-time_settings none"), restored);
    assertTrue(restored.contains("kata-set-rules"), restored);
    assertTrue(restored.contains("kata-set-param maxTime"), restored);
    assertTrue(restored.contains("kata-set-param maxVisits"), restored);
    assertEquals(new featurecat.lizzie.analysis.FoxEngineSession.Limits(3.0, 64),
        target.queryFoxLimits().get(10, TimeUnit.SECONDS));
    assertFalse(restored.contains("kata-analyze"), "handback must not start ordinary analysis");
    assertFalse(target.isPondering());
    Thread.sleep(200);
    String quietCommands = commands(work);
    String quietOutput = gtp(work);
    Path tracePath = work.resolve("logs/engine-trace.log");
    String quietTrace = Files.readString(tracePath);
    Thread.sleep(750);
    assertEquals(quietCommands, commands(work), "paused handback emits no automatic commands");
    assertEquals(quietOutput, gtp(work), "paused handback emits no automatic engine output");
    assertEquals(quietTrace, Files.readString(tracePath), "single-reader raw GTP remains quiet");
    captureMain(work.resolve("fox-" + scenario + "-paused.png"));
    BoardHistoryNode retained = Lizzie.board.getHistory().getCurrentHistoryNode();
    SwingUtilities.invokeAndWait(() -> {
      assertTrue(LizzieFrame.toolbar.analyse.isEnabled());
      assertEquals(Lizzie.resourceBundle.getString("BottomToolbar.analyse"),
          LizzieFrame.toolbar.analyse.getText(), scenario + " paused action label");
      assertSame(Menu.Playing2, LizzieFrame.toolbar.analyse.getIcon(),
          scenario + " paused action icon");
      LizzieFrame.toolbar.analyse.doClick();
    });
    await(() -> commands(work).substring(quietCommands.length()).contains("kata-analyze")
        && rawTrace(tracePath).substring(quietTrace.length()).contains("info move")
        && Lizzie.board.getData().getPlayouts() > 0, 15, scenario + " explicit ordinary output");
    assertSame(retained, Lizzie.board.getHistory().getCurrentHistoryNode());
    SwingUtilities.invokeAndWait(() -> {
      assertEquals(Lizzie.resourceBundle.getString("BottomToolbar.pauseAnalyse"),
          LizzieFrame.toolbar.analyse.getText(), scenario + " running action label");
      assertSame(Menu.ready2, LizzieFrame.toolbar.analyse.getIcon(),
          scenario + " running action icon");
    });
    captureMain(work.resolve("fox-" + scenario + "-explicit-analysis.png"));
    Files.writeString(work.resolve(scenario + "-paused-handback.txt"),
        "pausedCommandsAndOutput=true\nbreadthReleased=true\nretainedMove="
            + retained.getData().moveNumber + "\nplayouts=" + Lizzie.board.getData().getPlayouts()
            + "\n" + Files.readString(tracePath).substring(quietTrace.length()));
  }

  private static void directLimitCases(JDialog fox, int port, Leelaz target, Path work) throws Exception {
    for (String scenario : List.of("visits", "time", "fox-clock")) {
      await(() -> button(fox, "Fox.start").isEnabled(), 10, "limit controls released");
      SwingUtilities.invokeAndWait(() -> {
        limitField(fox, "fox-max-time").setText(scenario.equals("time") ? "1.5" : "10");
        limitField(fox, "fox-max-visits").setText(scenario.equals("visits") ? "128" : "0");
      });
      SwingUtilities.invokeLater(() -> button(fox, "Fox.start").doClick());
      await(() -> modal() != null, 5, "limit case confirmation");
      answerModal(JOptionPane.OK_OPTION);
      await(() -> modal() != null, 5, "limit case replace history");
      SwingUtilities.invokeAndWait(() -> button(modal(), "Fox.replace").doClick());
      await(() -> button(fox, "Fox.stop").isEnabled(), 5, "limit case listener");
      int logStart = gtp(work).length();
      long started = System.nanoTime();
      try (Socket peer = new Socket("127.0.0.1", port)) {
        peer.setSoTimeout(15000);
        String time = scenario.equals("fox-clock") ? "0,3,1" : "60,30,3";
        send(peer, "FASTATUS,0", "FARULE,19," + time + ",650", "FASTATUS,1,B",
            "FATIMELEFT," + time, "FAMOVE,1,B,0^0^0^N");
        assertEquals("AFSTATUS,B", receive(peer));
        send(peer, "FASTATUS,1,B", "FATIMELEFT," + time, "FAMOVE,1,B,0^0^0^N");
        String move = receive(peer);
        long elapsed = System.nanoTime() - started;
        assertTrue(move.startsWith("AFPLAY,B,"), move);
        assertTrue(elapsed < TimeUnit.SECONDS.toNanos(scenario.equals("visits") ? 10 : 3));
        String search = gtp(work).substring(logStart);
        assertTrue(search.contains("kata-set-param maxVisits " + (scenario.equals("visits") ? "128" : "1125899906842624")));
        var visits = java.util.regex.Pattern.compile("Root visits: (\\d+)").matcher(search);
        long maximum = 0;
        while (visits.find()) maximum = Math.max(maximum, Long.parseLong(visits.group(1)));
        if (scenario.equals("visits")) assertTrue(maximum > 64 && maximum <= 130,
            "actual root visits include at most the two in-flight search threads: " + maximum);
        else assertTrue(maximum > 0 && maximum < 1125899906842624L);
        send(peer, "FAMOVE,0,B," + move.substring("AFPLAY,B,".length()));
        await(() -> Lizzie.board.getHistory().getMoveNumber() == 1, 5, "capped move confirmed");
        SwingUtilities.invokeAndWait(fox::toFront);
        capture(fox, work.resolve("fox-limit-" + scenario + ".png"));
        Files.writeString(work.resolve("limit-" + scenario + ".txt"),
            "elapsedNanos=" + elapsed + "\nmaxObservedVisits=" + maximum + "\n" + search);
        SwingUtilities.invokeAndWait(() -> button(fox, "Fox.stop").doClick());
        await(() -> !FoxLiveSession.ownsEngine() && !target.hasExclusiveGtpWorkInProgress(), 15, "cap restore");
        assertFalse(target.isPondering());
        assertEquals(new featurecat.lizzie.analysis.FoxEngineSession.Limits(3.0, 64),
            target.queryFoxLimits().get(10, TimeUnit.SECONDS));
        Files.writeString(work.resolve("limit-" + scenario + "-restored.txt"),
            "maxTime=3.0\nmaxVisits=64\npaused=true\n" + gtp(work).substring(logStart));
      }
    }
  }

  private static void realKoReplay(Leelaz target, Path work) throws Exception {
    var ready = new java.util.concurrent.CompletableFuture<Void>();
    var adapter = new featurecat.lizzie.analysis.FoxEngineSession(target, target.engineIncarnationToken(),
        value -> {}, reason -> ready.completeExceptionally(new AssertionError(reason)));
    assertEquals(Leelaz.ExclusiveGtpLeaseAvailability.AVAILABLE,
        adapter.acquire(() -> ready.complete(null)));
    ready.get(10, TimeUnit.SECONDS);
    adapter.prepare("chinese", new featurecat.lizzie.analysis.FoxEngineSession.Limits(3, 64));
    var state = new featurecat.lizzie.fox.FoxGameState();
    state.connected();
    long now = System.nanoTime();
    for (String message : List.of("FARULE,5,60,30,3,650", "FASTATUS,1,B", "FATIMELEFT,60,30,3",
        "FAMOVE,1,B,0^0^0^N", "FAMOVE,0,B,1^1^2^B", "FAMOVE,1,B,2^2^2^W",
        "FAMOVE,0,B,3^2^3^B", "FAMOVE,1,B,4^1^1^W", "FAMOVE,0,B,5^3^2^B",
        "FAMOVE,1,B,6^3^1^W", "FAMOVE,0,B,7^0^4^B", "FAMOVE,1,B,8^2^0^W",
        "FAMOVE,0,B,9^2^1^B")) state.receive(message, now);
    assertEquals(9, state.snapshot().actions().size());
    state.disconnected(); state.connected();
    for (String message : List.of("FARULE,5,60,30,3,650",
        "FASTATUS,1,B,1^1^2^B,2^2^2^W,3^2^3^B,4^1^1^W,5^3^2^B,6^3^1^W,7^0^4^B,8^2^0^W,9^2^1^B",
        "FATIMELEFT,50,30,3", "FAMOVE,0,B,9^2^1^B")) state.receive(message, System.nanoTime());
    assertTrue(state.recoverHistory(System.nanoTime()));
    int start = gtp(work).length();
    var selection = adapter.analyze(state.snapshot());
    assertNotNull(selection, "real KataGo accepts the reachable nine-action ko history");
    assertNotEquals("C3", selection.vertex(), "immediate ko recapture is not a legal search choice");
    // GTP play deliberately accepts ko overrides; the engine's policy mask is the legality oracle.
    adapter.drainTranscript();
    assertTrue(target.sendExclusiveGtpCommand(adapter, "910000001 kata-raw-nn 0"));
    StringBuilder raw = new StringBuilder();
    await(() -> {
      raw.append(adapter.drainTranscript());
      return raw.toString().contains("=910000001") && raw.toString().endsWith("[0] < \n");
    }, 10, "real ko policy response through the single Leelaz reader");
    List<String> response = raw.toString().lines().filter(line -> line.startsWith("[0] < "))
        .map(line -> line.substring(6).trim()).toList();
    int policy = response.indexOf("policy");
    assertTrue(policy >= 0, raw.toString());
    assertEquals("NAN", response.get(policy + 3).split("\\s+")[2], "C3 is ko-forbidden after full replay");
    assertNotEquals("NAN", response.get(policy + 1).split("\\s+")[0], "A5 remains a legal ordinary move");
    Files.writeString(work.resolve("reachable-ko-replay.txt"),
        "nine-action recovery accepted; empty C3 masked illegal, A5 legal by real engine\n"
            + raw + "\n" + gtp(work).substring(start));
    var released = new java.util.concurrent.CompletableFuture<Void>();
    adapter.release(() -> released.complete(null),
        () -> released.completeExceptionally(new AssertionError("ko probe handback failed")));
    released.get(15, TimeUnit.SECONDS);
    assertFalse(target.isPondering());
  }

  /** A legal two-eye setup leaves White no legal stone placement; the real engine chooses PASS. */
  private static void naturalPassReconnect(JDialog fox, int port, Leelaz target, Path work) throws Exception {
    SwingUtilities.invokeLater(() -> button(fox, "Fox.start").doClick());
    await(() -> modal() != null, 5, "natural pass rules confirmation");
    answerModal(JOptionPane.OK_OPTION);
    await(() -> modal() != null, 5, "natural pass history replacement");
    SwingUtilities.invokeAndWait(() -> button(modal(), "Fox.replace").doClick());
    await(() -> button(fox, "Fox.stop").isEnabled(), 5, "natural pass listener");
    StringBuilder rules = new StringBuilder("FARULE,5,60,30,3,650");
    for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) {
      if ((x == 1 && y == 1) || (x == 3 && y == 3)) continue;
      rules.append(",0^").append(x).append('^').append(y).append("^B");
    }
    BoardHistoryNode[] pass = new BoardHistoryNode[1];
    String before = commands(work);
    try (Socket peer = new Socket("127.0.0.1", port)) {
      peer.setSoTimeout(20000);
      send(peer, "FASTATUS,0", rules.toString(), "FASTATUS,1,W", "FATIMELEFT,60,30,3",
          "FAMOVE,1,W,0^0^0^N");
      assertEquals("AFSTATUS,W", receive(peer));
      send(peer, "FASTATUS,1,W", "FATIMELEFT,59,30,3", "FAMOVE,1,W,0^0^0^N");
      assertEquals("AFSKIP,W", receive(peer), "unfiltered real engine natural PASS reaches production transport");
      assertEquals(0, Lizzie.board.getHistory().getMoveNumber(), "unconfirmed PASS never advances board");
      assertTrue(Lizzie.board.getData().isSnapshotNode());
      String searched = commands(work).substring(before.length());
      assertTrue(searched.contains("set_free_handicap"));
      assertFalse(searched.contains("play W pass"), "search itself does not play PASS");
      send(peer, "FASKIP,0,W");
      await(() -> Lizzie.board.getHistory().getMoveNumber() == 1, 5, "controlled PASS confirmation");
      pass[0] = Lizzie.board.getHistory().getCurrentHistoryNode();
      assertTrue(pass[0].getData().isPassNode());
      await(() -> commands(work).substring(before.length()).contains("play W pass"), 10, "confirmed PASS engine replay");
      send(peer, "FASTATUS,1,W", "FATIMELEFT,58,30,3", "FAMOVE,0,W,0^0^0^N");
      Thread.sleep(250);
      assertSame(pass[0], Lizzie.board.getHistory().getCurrentHistoryNode(), "pass-less full query retains tail PASS");
      captureMain(work.resolve("fox-natural-pass-confirmed.png"));
      Files.writeString(work.resolve("natural-pass-gtp.txt"), gtp(work));
    }
    await(() -> hasText(fox, "Fox.disconnected"), 5, "natural pass disconnect");
    try (Socket peer = new Socket("127.0.0.1", port)) {
      peer.setSoTimeout(20000);
      String recovery = commands(work);
      send(peer, rules.toString(), "FASTATUS,1,W,1^1^1^B", "FATIMELEFT,40,30,3", "FAMOVE,1,W,1^1^1^B");
      assertEquals("AFSTATUS,W", receive(peer));
      send(peer, "FASTATUS,1,W,1^1^1^B", "FATIMELEFT,39,30,3", "FAMOVE,1,W,1^1^1^B");
      String continuation = receive(peer);
      assertEquals("AFPLAY,W,2^3^3^W", continuation,
          "after Black fills one eye the real engine captures the now one-liberty group");
      String replay = commands(work).substring(recovery.length());
      assertTrue(replay.indexOf("play W pass") >= 0);
      assertTrue(replay.indexOf("play W pass") < replay.indexOf("play B B4"));
      assertTrue(replay.indexOf("play B B4") < replay.indexOf("kata-search_analyze_cancellable W"));
      assertEquals(2, Lizzie.board.getHistory().getMoveNumber());
      assertSame(pass[0], Lizzie.board.getHistory().getCurrentHistoryNode().previous().orElseThrow());
      assertFalse(Lizzie.board.getData().blackToPlay);
      send(peer, "FAMOVE,0,W," + continuation.substring("AFPLAY,W,".length()));
      await(() -> Lizzie.board.getHistory().getMoveNumber() == 3, 5, "fresh capture confirmed once");
      peer.setSoTimeout(300);
      assertThrows(SocketTimeoutException.class, () -> peer.getInputStream().read(), "no duplicate recovered submission");
      Files.writeString(work.resolve("natural-pass-recovery-gtp.txt"), replay);
      capture(fox, work.resolve("fox-natural-pass-recovered.png"));
      SwingUtilities.invokeAndWait(() -> button(fox, "Fox.stop").doClick());
      await(() -> !FoxLiveSession.isActive() && !target.hasExclusiveGtpWorkInProgress(),
          35, "natural pass confirmed handback");
    }
  }

  private static void retainedReconnect(JDialog fox, int port, Leelaz target, Path work) throws Exception {
    SwingUtilities.invokeLater(() -> button(fox, "Fox.start").doClick());
    await(() -> modal() != null, 5, "reconnect scenario rules confirmation");
    answerModal(JOptionPane.OK_OPTION);
    await(() -> modal() != null, 5, "existing unsaved history replacement prompt");
    SwingUtilities.invokeAndWait(() -> button(modal(), "Fox.replace").doClick());
    await(() -> button(fox, "Fox.stop").isEnabled(), 5, "new listener");
    BoardHistoryNode[] historic = new BoardHistoryNode[1];
    try (Socket peer = new Socket("127.0.0.1", port)) {
      send(peer, "FASTATUS,0", "FARULE,19,60,30,3,650", "FASTATUS,1,B", "FATIMELEFT,60,30,3",
          "FAMOVE,1,B,0^0^0^N", "FAMOVE,0,B,1^16^3^B", "FAMOVE,1,B,2^3^15^W", "FASKIP,0,B");
      await(() -> Lizzie.board.getHistory().getMoveNumber() == 3
          && !Lizzie.frame.getBestMoves().isEmpty(), 25, "retained pass live candidate");
      historic[0] = Lizzie.board.getHistory().getCurrentHistoryNode().previous().orElseThrow();
      SwingUtilities.invokeAndWait(() -> Lizzie.board.navigateToNode(historic[0]));
    }
    await(() -> hasText(fox, "Fox.disconnected") && target.hasExclusiveGtpLease(),
        5, "retained disconnected owner");
    assertBreadth(false);
    assertSame(historic[0], Lizzie.board.getHistory().getCurrentHistoryNode());
    try (Socket peer = new Socket("127.0.0.1", port)) {
      String before = commands(work);
      send(peer, "FARULE,19,60,30,3,650", "FASTATUS,1,B,1^16^3^B,2^3^15^W",
          "FATIMELEFT,40,30,3", "FAMOVE,0,B,2^3^15^W");
      assertTrue(target.hasExclusiveGtpLease());
      peer.setSoTimeout(250);
      assertThrows(SocketTimeoutException.class, () -> peer.getInputStream().read());
      assertBreadth(false);
      await(() -> commands(work).substring(before.length()).contains("kata-search_analyze_cancellable W"),
          20, "replayed pass before new opponent search");
      assertSame(historic[0], Lizzie.board.getHistory().getCurrentHistoryNode());
      assertEquals(3, Lizzie.board.getHistory().getMainEndWithPass().getData().moveNumber);
      String replay = commands(work).substring(before.length());
      assertTrue(replay.indexOf("play B pass") < replay.indexOf("kata-search_analyze_cancellable W"));
      assertTrue(replay.contains("time_left B 40.0 0"), replay);
      assertThrows(SocketTimeoutException.class, () -> peer.getInputStream().read(), "opponent no send after recovery");
      SwingUtilities.invokeAndWait(() -> button(fox, "Fox.returnLive").doClick());
      await(() -> !Lizzie.frame.getBestMoves().isEmpty(), 15, "new recovery candidates");
      Files.writeString(work.resolve("reconnect-replay-gtp.txt"), gtp(work));
      capture(fox, work.resolve("fox-reconnect-restored.png"));
    }
    await(() -> hasText(fox, "Fox.disconnected") && target.hasExclusiveGtpLease(), 5, "second disconnect");
    try (Socket peer = new Socket("127.0.0.1", port)) {
      peer.setSoTimeout(20000);
      String before = commands(work);
      send(peer, "FARULE,19,60,30,3,650", "FASTATUS,1,B,1^16^3^B,2^3^15^W,3^15^15^W",
          "FATIMELEFT,35,30,3", "FAMOVE,1,B,3^15^15^W");
      assertEquals("AFSTATUS,B", receive(peer));
      send(peer, "FASTATUS,1,B,1^16^3^B,2^3^15^W,3^15^15^W", "FATIMELEFT,34,30,3",
          "FAMOVE,1,B,3^15^15^W");
      String move = receive(peer);
      assertTrue(move.startsWith("AFPLAY,B,4^"), move);
      assertEquals(4, Lizzie.board.getHistory().getMainEndWithPass().getData().moveNumber);
      assertTrue(Lizzie.board.getData().blackToPlay);
      String replay = commands(work).substring(before.length());
      assertTrue(replay.indexOf("play B pass") < replay.indexOf("play W Q4"));
      assertTrue(replay.indexOf("play W Q4") < replay.indexOf("kata-search_analyze_cancellable B"));
      Files.writeString(work.resolve("four-action-recovery-gtp.txt"), gtp(work));
      send(peer, "FAMOVE,0,B," + move.substring("AFPLAY,B,".length()));
      await(() -> Lizzie.board.getHistory().getMoveNumber() == 5, 5, "fresh recovery move confirmed");
      capture(fox, work.resolve("fox-four-action-recovered.png"));
      SwingUtilities.invokeAndWait(() -> Lizzie.board.navigateToNode(historic[0]));
      String beforeStop = commands(work);
      SwingUtilities.invokeAndWait(() -> button(fox, "Fox.stop").doClick());
      await(() -> !FoxLiveSession.isActive() && !target.hasExclusiveGtpWorkInProgress(), 35, "recovered safe Stop");
      assertSame(historic[0], Lizzie.board.getHistory().getCurrentHistoryNode());
      String stopReplay = commands(work).substring(beforeStop.length());
      assertTrue(stopReplay.contains("play B R16") && stopReplay.contains("play W D4"), stopReplay);
      assertFalse(stopReplay.contains("play B pass"), "Stop restores displayed two moves, not live trailing pass");
      Files.writeString(work.resolve("reconnect-displayed-stop-gtp.txt"), stopReplay);
    }
  }

  private static void unconfirmedRetainedStop(JDialog fox, int port, Leelaz target, Path work) throws Exception {
    SwingUtilities.invokeLater(() -> button(fox, "Fox.start").doClick());
    await(() -> modal() != null, 5, "unconfirmed scenario rules confirmation");
    answerModal(JOptionPane.OK_OPTION);
    await(() -> modal() != null, 5, "confirmed history replacement prompt");
    SwingUtilities.invokeAndWait(() -> button(modal(), "Fox.replace").doClick());
    await(() -> button(fox, "Fox.stop").isEnabled(), 5, "unconfirmed listener");
    BoardHistoryNode[] firstMove = new BoardHistoryNode[1];
    try (Socket peer = new Socket("127.0.0.1", port)) {
      send(peer, "FASTATUS,0", "FARULE,19,60,30,3,650", "FASTATUS,1,B", "FATIMELEFT,60,30,3",
          "FAMOVE,1,B,0^0^0^N", "FAMOVE,0,B,1^16^3^B", "FAMOVE,1,B,2^3^15^W");
      peer.setSoTimeout(15000);
      assertEquals("AFSTATUS,B", receive(peer));
      send(peer, "FASTATUS,1,B,1^16^3^B,2^3^15^W", "FATIMELEFT,58,30,3", "FAMOVE,1,B,2^3^15^W");
      String pending = receive(peer);
      if (pending.equals("AFSTATUS,B")) {
        // The opening and post-white own turns may each have already written their refresh.
        send(peer, "FASTATUS,1,B,1^16^3^B,2^3^15^W", "FATIMELEFT,58,30,3", "FAMOVE,1,B,2^3^15^W");
        pending = receive(peer);
      }
      assertTrue(pending.startsWith("AFPLAY,B,3^") || pending.equals("AFSKIP,B"), pending);
      assertEquals(2, Lizzie.board.getHistory().getMoveNumber());
      firstMove[0] = Lizzie.board.getHistory().getCurrentHistoryNode().previous().orElseThrow();
      // Disconnect with the physical request still unresolved; neither pending nor its failure is a MOVE.
    }
    await(() -> hasText(fox, "Fox.disconnected"), 5, "pending disconnect");
    try (Socket peer = new Socket("127.0.0.1", port)) {
      send(peer, "FARULE,19,60,30,3,650", "FASTATUS,1,B,1^16^3^B,2^3^15^W",
          "FATIMELEFT,50,30,3", "FAMOVE,1,B,2^3^15^W");
      await(() -> hasText(fox, "Fox.recoveryUnconfirmed"), 5, "retained pending refusal");
      assertTrue(target.hasExclusiveGtpLease());
      String beforeBrowse = commands(work);
      SwingUtilities.invokeAndWait(() -> Lizzie.board.navigateToNode(firstMove[0]));
      assertEquals(beforeBrowse, commands(work), "blocked browsing remains local");
      peer.setSoTimeout(250);
      assertThrows(SocketTimeoutException.class, () -> peer.getInputStream().read(), "unresolved request never resent");
      capture(fox, work.resolve("fox-reconnect-pending-history.png"));
      String beforeStop = commands(work);
      SwingUtilities.invokeAndWait(() -> button(fox, "Fox.stop").doClick());
      await(() -> !FoxLiveSession.isActive() && !target.hasExclusiveGtpWorkInProgress(), 35, "unconfirmed history safe Stop");
      assertSame(firstMove[0], Lizzie.board.getHistory().getCurrentHistoryNode());
      String restored = commands(work).substring(beforeStop.length());
      assertTrue(restored.contains("play B R16"), restored);
      assertFalse(restored.contains("play W D4"), "Stop restores displayed move one, not retained move two");
      Files.writeString(work.resolve("reconnect-unconfirmed-stop-gtp.txt"), restored);
    }
  }

  private static void preparationRecovery(PreparationGateEngine target, Path work) throws Exception {
    SwingUtilities.invokeAndWait(FoxLiveSession::showDialog);
    JDialog fox = dialog();
    await(() -> button(fox, "Fox.start").isEnabled(), 10, "confirmed current limits");
    int port;
    try (ServerSocket available = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      port = available.getLocalPort();
    }
    int selectedPort = port;
    SwingUtilities.invokeAndWait(() -> find(fox, JSpinner.class).setValue(selectedPort));
    startPreparationCase(fox);
    Object reader = target.engineIncarnationToken();
    target.armPreparation();
    Object retainedOwner;
    String disconnectedCommands;
    try (Socket peer = new Socket("127.0.0.1", port)) {
      sendSetupOpponentTurn(peer);
      assertTrue(target.held.await(5, TimeUnit.SECONDS), "real matching final preparation ACK held");
      retainedOwner = target.lastOwner;
      assertTrue(FoxLiveSession.ownsEngine());
      assertEquals(0, Lizzie.board.getHistory().getMoveNumber(), "setup is not an action");
      disconnectedCommands = commands(work);
    }
    await(() -> hasText(fox, "Fox.disconnected"), 5, "disconnect consumed before preparation success");
    target.releasePreparation();
    drainPreparationWorker();
    assertTrue(FoxLiveSession.ownsEngine(), "disconnect retains owner");
    assertSame(retainedOwner, target.lastOwner);
    assertSame(reader, target.engineIncarnationToken());
    assertEquals(disconnectedCommands, commands(work), "successful preparation cannot search while disconnected");
    assertTrue(Lizzie.frame.getBestMoves().isEmpty());
    Files.writeString(work.resolve("preparation-disconnected.txt"),
        "matchingAck=" + target.heldResponse + "\nworkerAndEdtDrained=true\nsameOwner=true\nnewCommands=0\ncandidates=0\n");
    try (Socket peer = new Socket("127.0.0.1", port)) {
      sendSetupOpponentTurn(peer);
      await(() -> !Lizzie.frame.getBestMoves().isEmpty(), 15, "fresh opponent candidates after preparation recovery");
      assertSame(retainedOwner, target.lastOwner, "recovery reuses prepared owner");
      assertSame(reader, target.engineIncarnationToken());
      String replay = commands(work).substring(disconnectedCommands.length());
      assertTrue(replay.contains("set_free_handicap D16 Q4"), replay);
      assertTrue(replay.contains("kata-search_analyze_cancellable W"), replay);
      peer.setSoTimeout(300);
      assertThrows(SocketTimeoutException.class, () -> peer.getInputStream().read(), "opponent candidates never submit");
      Files.writeString(work.resolve("preparation-recovered-gtp.txt"), gtp(work));
      capture(fox, work.resolve("preparation-recovered.png"));
      send(peer, "FAMOVE,1,B,1^3^15^W");
      peer.setSoTimeout(15000);
      assertEquals("AFSTATUS,B", receive(peer), "new own turn requires authoritative clock refresh");
      send(peer, "FASTATUS,1,B,1^3^15^W", "FATIMELEFT,59,30,3", "FAMOVE,1,B,1^3^15^W");
      String move = receive(peer);
      assertTrue(move.startsWith("AFPLAY,B,2^") || move.equals("AFSKIP,B"), move);
      if (move.equals("AFSKIP,B")) send(peer, "FASKIP,0,B");
      else send(peer, "FAMOVE,0,B," + move.substring("AFPLAY,B,".length()));
      await(() -> Lizzie.board.getHistory().getMoveNumber() == 2, 5, "one confirmed own-turn action");
      peer.setSoTimeout(300);
      assertThrows(SocketTimeoutException.class, () -> peer.getInputStream().read(), "single submission only");
      Files.writeString(work.resolve("preparation-own-turn.txt"), "confirmed=" + move + "\nactions=2\n" + gtp(work));
      SwingUtilities.invokeAndWait(() -> button(fox, "Fox.stop").doClick());
      await(() -> !FoxLiveSession.ownsEngine() && !target.hasExclusiveGtpWorkInProgress(), 15, "recovered owner handback");
    }

    startPreparationCase(fox);
    target.armPreparation();
    Object stoppedOwner;
    try (Socket peer = new Socket("127.0.0.1", port)) {
      sendSetupOpponentTurn(peer);
      assertTrue(target.held.await(5, TimeUnit.SECONDS), "second owner final ACK held");
      stoppedOwner = target.lastOwner;
      assertNotSame(retainedOwner, stoppedOwner);
      String beforeStop = commands(work);
      SwingUtilities.invokeAndWait(() -> button(fox, "Fox.stop").doClick());
      target.releasePreparation();
      drainPreparationWorker();
      await(() -> !FoxLiveSession.ownsEngine() && !target.hasExclusiveGtpWorkInProgress(), 15, "late prepare success cannot resurrect stopped owner");
      assertFalse(target.isPondering());
      String stopped = commands(work).substring(beforeStop.length());
      assertFalse(stopped.contains("kata-search_analyze_cancellable"), stopped);
      assertFalse(stopped.contains("kata-analyze"), stopped);
      peer.setSoTimeout(1000);
      assertEquals(-1, peer.getInputStream().read(), "stopped peer receives no submission");
      Files.writeString(work.resolve("preparation-stopped-gtp.txt"), stopped);
    }

    startPreparationCase(fox);
    try (Socket peer = new Socket("127.0.0.1", port)) {
      sendSetupOpponentTurn(peer);
      await(() -> !Lizzie.frame.getBestMoves().isEmpty(), 15, "replacement owner prepares and searches normally");
      assertNotSame(stoppedOwner, target.lastOwner);
      assertSame(reader, target.engineIncarnationToken());
      peer.setSoTimeout(300);
      assertThrows(SocketTimeoutException.class, () -> peer.getInputStream().read());
      capture(fox, work.resolve("preparation-replacement.png"));
      SwingUtilities.invokeAndWait(() -> button(fox, "Fox.stop").doClick());
      await(() -> !FoxLiveSession.ownsEngine() && !target.hasExclusiveGtpWorkInProgress(), 15, "replacement owner handback");
    }
  }

  private static void sendSetupOpponentTurn(Socket peer) throws Exception {
    send(peer, "FARULE,19,60,30,3,650,0^3^3^B,0^15^15^B", "FASTATUS,1,B",
        "FATIMELEFT,60,30,3", "FAMOVE,0,B,0^0^0^N");
  }

  private static void startPreparationCase(JDialog fox) throws Exception {
    await(() -> button(fox, "Fox.start").isEnabled(), 10, "preparation case start enabled");
    SwingUtilities.invokeLater(() -> button(fox, "Fox.start").doClick());
    await(() -> modal() != null, 5, "preparation case rules confirmation");
    answerModal(JOptionPane.OK_OPTION);
    await(() -> button(fox, "Fox.stop").isEnabled() || modal() != null, 5, "listener or history replacement prompt");
    if (modal() != null) SwingUtilities.invokeAndWait(() -> button(modal(), "Fox.replace").doClick());
    await(() -> button(fox, "Fox.stop").isEnabled(), 5, "preparation case listener");
  }

  /** Queue markers establish callback consumption rather than relying on a sleep after ACK delivery. */
  private static void drainPreparationWorker() throws Exception {
    var instance = FoxLiveSession.class.getDeclaredField("instance");
    instance.setAccessible(true);
    var worker = FoxLiveSession.class.getDeclaredField("worker");
    worker.setAccessible(true);
    ((java.util.concurrent.ExecutorService) worker.get(instance.get(null)))
        .submit(() -> {}).get(4, TimeUnit.SECONDS);
    SwingUtilities.invokeAndWait(() -> {});
  }

  private static void answerModal(int answer) throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      JDialog dialog = modal();
      find(dialog, JOptionPane.class).setValue(answer);
      dialog.dispose();
    });
  }

  private static void capture(JDialog fox, Path path) throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      Lizzie.frame.getRootPane().paintImmediately(0, 0, Lizzie.frame.getWidth(), Lizzie.frame.getHeight());
      fox.getRootPane().paintImmediately(0, 0, fox.getWidth(), fox.getHeight());
      if (modal() != null) modal().getRootPane().paintImmediately(0, 0, modal().getWidth(), modal().getHeight());
    });
    Robot robot = new Robot();
    robot.waitForIdle();
    robot.delay(100);
    ImageIO.write(robot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())),
        "png", path.toFile());
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

  private static String rawTrace(Path path) {
    try { return Files.readString(path); }
    catch (java.io.IOException failure) { throw new AssertionError(failure); }
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
  private static void assertThreads(Leelaz target, Path work) throws Exception {
    int before = gtp(work).length();
    target.sendCommand("kata-get-param numSearchThreads");
    await(() -> gtp(work).substring(before).matches(
        "(?s).*Controller: kata-get-param numSearchThreads\\R[^\\n]*: = 2\\s.*"),
        5, "effective engine threads remain two");
  }

  private static JTextField readOnlyRules(Container parent) {
    for (Component child : parent.getComponents()) {
      if (child instanceof JTextField field && !field.isEditable()) return field;
      if (child instanceof Container container) {
        JTextField found = readOnlyRules(container);
        if (found != null) return found;
      }
    }
    return null;
  }
  private static JTextField limitField(Container parent, String name) {
    for (Component child : parent.getComponents()) {
      if (child instanceof JTextField field && name.equals(field.getName())) return field;
      if (child instanceof Container container) {
        JTextField found = limitField(container, name);
        if (found != null) return found;
      }
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
  private static class NamedEngine extends Leelaz {
    NamedEngine(EngineData entry) throws Exception {
      super(entry.commands); savedEntryId = entry.id; width = height = oriWidth = oriHeight = 19;
      komi = orikomi = 6.5f;
    }
    @Override public String getEngineName(int index) { return "Fox real KataGo acceptance"; }
  }

  /** Gates the real same-reader response at the existing exclusive-consumer boundary. */
  private static final class PreparationGateEngine extends NamedEngine {
    volatile java.util.concurrent.CountDownLatch held;
    private volatile java.util.concurrent.CountDownLatch release;
    private volatile boolean armed;
    private volatile String responsePrefix;
    volatile String heldResponse;
    volatile Object lastOwner;

    PreparationGateEngine(EngineData entry) throws Exception { super(entry); }

    void armPreparation() {
      held = new java.util.concurrent.CountDownLatch(1);
      release = new java.util.concurrent.CountDownLatch(1);
      responsePrefix = null;
      armed = true;
    }

    void releasePreparation() { release.countDown(); }

    @Override public boolean sendExclusiveGtpCommand(Object owner, String command) {
      if (armed && command.endsWith(" kata-get-param playoutDoublingAdvantage")) {
        responsePrefix = "=" + command.substring(0, command.indexOf(' ')) + " ";
        armed = false;
      }
      return super.sendExclusiveGtpCommand(owner, command);
    }

    @Override public ExclusiveGtpLeaseAvailability beginForegroundAnalysisLease(
        Object owner, java.util.function.Consumer<String> lines, Runnable ready, Runnable closed) {
      lastOwner = owner;
      return super.beginForegroundAnalysisLease(owner, line -> {
        String prefix = responsePrefix;
        if (prefix != null && line.startsWith(prefix)) {
          responsePrefix = null;
          heldResponse = line;
          held.countDown();
          try {
            if (!release.await(4, TimeUnit.SECONDS)) throw new AssertionError("preparation ACK gate timeout");
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
          }
        }
        lines.accept(line);
      }, ready, closed);
    }
  }
}
