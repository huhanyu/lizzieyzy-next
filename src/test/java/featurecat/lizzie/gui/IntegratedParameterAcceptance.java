package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.*;

import featurecat.lizzie.Config;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.Leelaz;
import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.BoardHistoryNode;
import featurecat.lizzie.rules.SGFParser;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import org.json.JSONObject;

/** Assertions for the combined real-engine mode; process/setup/retirement stay in its parent IT. */
final class IntegratedParameterAcceptance {
  private static final String WRN = "analysisWideRootNoise";
  private static final String PDA = "playoutDoublingAdvantage";
  private static final String NOTE = "Combined acceptance manual comment — keep this note.";
  private static final Pattern SEARCH =
      Pattern.compile(
          "Controller: \\d*\\s*(?:kata-analyze|lz-analyze|kata-search(?:_analyze)?|genmove)\\b");
  private final Path work;
  private final Path result;
  private Leelaz ordinaryEngine;
  private final int[] gameLogStarts = new int[2];
  private Map<String, String> ordinaryPreferences;

  IntegratedParameterAcceptance(Path work, Path result, Leelaz ordinaryEngine) {
    this.work = work;
    this.result = result;
    this.ordinaryEngine = ordinaryEngine;
  }

  void beforeGame(double wrn, double pda) throws Exception {
    await(
        () ->
            Lizzie.leelaz == ordinaryEngine
                && LizzieFrame.menu.chkWRN.isEnabled()
                && LizzieFrame.menu.chkPDA.isEnabled()
                && ordinaryValues(wrn, pda),
        20,
        "ordinary toolbar startup readback");
    assertOrdinaryShown(wrn, pda);
    ordinaryAnalysis(wrn, pda, "ordinary-before-match");
    ordinaryPreferences = preferences();
    Files.writeString(
        work.resolve("ordinary-preferences-before.json"),
        new JSONObject(ordinaryPreferences).toString(2));
    for (int i = 0; i < 2; i++) gameLogStarts[i] = log(i).length();
    record("ordinary-nonzero-parameters-and-visible-summary=PASS");
  }

  void observeGame() throws Exception {
    hidden("running");
    showParameters("running");
    await(() -> Lizzie.board.getData().moveNumber >= 2, 25, "both real participants move");
    int[][] before = parameterWrites();
    edt(
        () -> {
          Lizzie.engineGame.pause();
          return null;
        });
    await(() -> Lizzie.engineGame.current().paused(), 10, "combined match pauses");
    hidden("paused-double");
    Map<BoardHistoryNode, String> summaries = gameSummaries();
    assertTrue(
        summaries.size() >= 2, "Two real result nodes are required, not fabricated analysis");
    switchLayout(false);
    hidden("paused-single");
    screenshot("paused-single");
    switchLayout(true);
    hidden("paused-double-again");
    showParameters("paused");
    Map<BoardHistoryNode, String> after = gameSummaries();
    for (var entry : summaries.entrySet()) {
      assertEquals(
          entry.getValue(),
          after.get(entry.getKey()),
          "Layout changes must not relabel or overwrite an existing result");
    }
    assertArrayEquals(before[0], parameterWrites()[0], "No first-participant setters from layout");
    assertArrayEquals(before[1], parameterWrites()[1], "No second-participant setters from layout");
    assertEquals(
        ordinaryPreferences, preferences(), "Game values must not overwrite ordinary preferences");
    openingProtocol();
    int pausedMove = edt(() -> Lizzie.board.getData().moveNumber);
    screenshot("paused-double");
    edt(
        () -> {
          assertTrue(Lizzie.engineGame.current().paused());
          Lizzie.engineGame.resume();
          return null;
        });
    await(
        () -> Lizzie.board.getData().moveNumber > pausedMove,
        25,
        "real move after combined resume");
    hidden("resumed");
    screenshot("resumed");
    assertArrayEquals(
        before[0], parameterWrites()[0], "Pause/layout/resume never set first parameters");
    assertArrayEquals(
        before[1], parameterWrites()[1], "Pause/layout/resume never set second parameters");
    record(
        "combined-pause-layout-summary-attribution-and-zero-setter-delta=PASS\ncombined-real-resume=PASS");
  }

  void afterGame(Leelaz foreground, double wrn, double pda) throws Exception {
    ordinaryEngine = foreground;
    record(
        edt(
            () ->
                "handback.foreground="
                    + Lizzie.leelaz.currentEngineN()
                    + " runtime="
                    + Lizzie.leelaz.wrn
                    + ","
                    + Lizzie.leelaz.pda
                    + " toolbar="
                    + LizzieFrame.menu.txtWRN.getText()
                    + ","
                    + LizzieFrame.menu.txtGfPDA.getText()
                    + " enabled="
                    + LizzieFrame.menu.chkWRN.isEnabled()
                    + ","
                    + LizzieFrame.menu.chkPDA.isEnabled()
                    + " hidden="
                    + Lizzie.engineGame.ordinaryParametersHidden()));
    screenshot("handback-observed");
    await(
        () ->
            !Lizzie.engineGame.ordinaryParametersHidden()
                && Lizzie.leelaz == ordinaryEngine
                && LizzieFrame.menu.chkWRN.isEnabled()
                && LizzieFrame.menu.chkPDA.isEnabled()
                && ordinaryValues(wrn, pda),
        30,
        "ordinary eligibility and values restored");
    assertOrdinaryShown(wrn, pda);
    assertEquals(
        ordinaryPreferences, preferences(), "Natural completion preserves ordinary preferences");
    Files.writeString(
        work.resolve("ordinary-preferences-after.json"), new JSONObject(preferences()).toString(2));
    screenshot("ordinary-restored");
    edt(
        () -> {
          Lizzie.frame.newEmptyBoard();
          return null;
        });
    await(() -> ordinaryEngine.canSetAnalysisWideRootNoise(), 25, "new ordinary board ready");
    String summary = ordinaryAnalysis(wrn, pda, "ordinary-after-match");
    Path sgf = work.resolve("combined-ordinary-summary.sgf");
    edt(
        () -> {
          // The same production coordinator used by Save Original, without a native file chooser.
          SgfSaveCoordinator.saveOriginal(sgf.toFile());
          return null;
        });
    SgfSaveCoordinator.pendingSaves().get(20, TimeUnit.SECONDS);
    assertTrue(Files.isRegularFile(sgf));
    assertTrue(Files.readString(sgf).contains("wrn=" + wrn), "Named WRN extension is saved");
    String reopened =
        edt(
            () -> {
              Lizzie.frame.newEmptyBoard();
              assertTrue(
                  Lizzie.frame.loadFile(sgf.toFile(), false, false),
                  "Production open accepts saved SGF");
              // Check the adopted payload before the normal post-open analysis can replace its
              // visits.
              BoardData data = Lizzie.board.getData();
              assertTrue(data.getPlayouts() > 0);
              assertTrue(data.wrnKnown);
              assertEquals(wrn, data.wrn, 1e-9);
              assertEquals(pda, data.pda, 1e-9);
              assertTrue(data.comment.contains(NOTE), data.comment);
              SGFParser.appendComment();
              assertEquals(
                  SGFParser.splitWinrateComment(summary).matchInfo,
                  SGFParser.splitWinrateComment(data.comment).matchInfo,
                  "Production save/open preserves the complete analysis summary");
              Lizzie.frame.refresh();
              return data.comment;
            });
    Files.writeString(work.resolve("production-reopened-summary.txt"), reopened);
    await(
        () -> !Lizzie.board.isLoadingFile && ordinaryEngine.canSetAnalysisWideRootNoise(),
        30,
        "production SGF open synchronizes the real engine");
    edt(
        () -> {
          if (ordinaryEngine.isPondering()) Lizzie.frame.togglePonderMannul();
          return null;
        });
    await(
        () ->
            visibleCommentContains(Lizzie.frame.getContentPane(), wrnText(wrn))
                && visibleCommentContains(Lizzie.frame.getContentPane(), pdaText(pda))
                && visibleCommentContains(Lizzie.frame.getContentPane(), NOTE),
        15,
        "reopened visible summary and manual note");
    assertOrdinaryShown(wrn, pda);
    assertEquals(
        ordinaryPreferences, preferences(), "Saving/opening does not replace ordinary preferences");
    screenshot("production-sgf-reopened");
    record(
        "ordinary-eligibility-values-preferences-restored=PASS\nproduction-sgf-save-open-summary-wrn-manual-comment=PASS\nsgf="
            + sgf);
  }

  private String ordinaryAnalysis(double wrn, double pda, String label) throws Exception {
    edt(
        () -> {
          Lizzie.board.getData().comment = NOTE;
          // Parameter inspection is a GTP command and stops KataGo's current analysis stream.
          if (ordinaryEngine.isPondering()) Lizzie.frame.togglePonderMannul();
          Lizzie.frame.togglePonderMannul();
          return null;
        });
    await(
        () ->
            Lizzie.board.getData().getPlayouts() > 0
                && Lizzie.board.getData().wrnKnown
                && Math.abs(Lizzie.board.getData().wrn - wrn) < 1e-9
                && Math.abs(Lizzie.board.getData().pda - pda) < 1e-9,
        30,
        "fresh real ordinary analysis " + label);
    edt(
        () -> {
          if (ordinaryEngine.isPondering()) Lizzie.frame.togglePonderMannul();
          return null;
        });
    // A real response fences preceding analysis output before capturing the summary for saving.
    assertEquals(wrn, EngineGameParametersAcceptanceIT.readParameter(ordinaryEngine, WRN), 1e-9);
    assertEquals(pda, EngineGameParametersAcceptanceIT.readParameter(ordinaryEngine, PDA), 1e-9);
    String summary =
        edt(
            () -> {
              SGFParser.appendComment();
              String comment = Lizzie.board.getData().comment;
              assertTrue(comment.contains(NOTE), comment);
              assertTrue(comment.contains(wrnText(wrn)), comment);
              assertTrue(comment.contains(pdaText(pda)), comment);
              Lizzie.frame.refresh();
              return comment;
            });
    await(
        () ->
            visibleCommentContains(Lizzie.frame.getContentPane(), wrnText(wrn))
                && visibleCommentContains(Lizzie.frame.getContentPane(), pdaText(pda)),
        15,
        "visible ordinary summary " + label);
    screenshot(label);
    Files.writeString(work.resolve(label + "-summary.txt"), summary);
    return summary;
  }

  private Map<BoardHistoryNode, String> gameSummaries() throws Exception {
    StringBuilder evidence = new StringBuilder();
    Map<BoardHistoryNode, String> results =
        edt(
            () -> {
              Map<BoardHistoryNode, String> summaries = new LinkedHashMap<>();
              BoardHistoryNode node = Lizzie.board.getHistory().getStart();
              boolean[] seen = new boolean[2];
              while (true) {
                BoardData data = node.getData();
                if (data.getPlayouts() > 0) {
                  int position = data.blackToPlay ? 0 : 1;
                  double wrn = position == 0 ? 0.2 : 0.4;
                  double pda = position == 0 ? 0.5 : -0.5;
                  assertTrue(data.wrnKnown, "Real game result records breadth");
                  assertEquals(
                      wrn,
                      data.wrn,
                      1e-9,
                      "Breadth belongs to the participant searching this position");
                  assertEquals(
                      pda, data.pda, 1e-9, "Aggressiveness belongs to the same real result");
                  String summary = SGFParser.formatCommentPk(node);
                  assertTrue(summary.contains(wrnText(wrn)), summary);
                  assertTrue(summary.contains(pdaText(pda)), summary);
                  evidence
                      .append("move=")
                      .append(data.moveNumber)
                      .append(" black-to-play=")
                      .append(data.blackToPlay)
                      .append(" participant=")
                      .append(position + 1)
                      .append('\n')
                      .append(summary)
                      .append("\n\n");
                  summaries.put(node, data.engineName + "\n" + wrnText(wrn) + "\n" + pdaText(pda));
                  seen[position] = true;
                }
                if (node.next().isEmpty()) break;
                node = node.next().orElseThrow();
              }
              assertTrue(
                  seen[0] && seen[1], "Both asymmetric participants have real analysis summaries");
              return summaries;
            });
    Files.writeString(work.resolve("paused-game-summaries.txt"), evidence);
    return results;
  }

  private void showParameters(String label) throws Exception {
    EngineGameParametersDialog dialog =
        edt(
            () -> {
              JMenuItem item = gameParametersItem(LizzieFrame.menu);
              assertNotNull(item, "Production menu has game parameters");
              JMenu menu = (JMenu) ((JPopupMenu) item.getParent()).getInvoker();
              menu.setSelected(true);
              menu.setSelected(false);
              assertTrue(item.isVisible() && item.isEnabled());
              item.doClick();
              for (Window window : Window.getWindows()) {
                if (window instanceof EngineGameParametersDialog found && found.isShowing())
                  return found;
              }
              throw new AssertionError("Menu did not open production parameters panel");
            });
    edt(
        () -> {
          List<JTextField> fields = new ArrayList<>();
          collectFields(dialog.getContentPane(), fields);
          assertEquals(4, fields.size());
          double[] expected = {0.2, 0.5, 0.4, -0.5};
          for (int i = 0; i < expected.length; i++) {
            assertTrue(fields.get(i).isShowing() && fields.get(i).isEnabled());
            assertEquals(expected[i], Double.parseDouble(fields.get(i).getText()), 1e-9);
          }
          for (String text :
              List.of("EngineGameParameters.position1", "EngineGameParameters.position2")) {
            assertTrue(
                labelContains(dialog.getContentPane(), Lizzie.resourceBundle.getString(text)));
          }
          for (String text : List.of("0.2", "0.4")) {
            assertTrue(
                labelContains(
                    dialog.getContentPane(),
                    Lizzie.resourceBundle.getString("EngineGameParameters.wrn") + ": " + text));
          }
          for (String text : List.of("0.5", "-0.5")) {
            assertTrue(
                labelContains(
                    dialog.getContentPane(),
                    Lizzie.resourceBundle.getString("EngineGameParameters.pda") + ": " + text));
          }
          return null;
        });
    screenshot(label + "-parameters");
    edt(
        () -> {
          dialog.setVisible(false);
          return null;
        });
    record("both-position-confirmed-panel." + label + "=PASS");
  }

  private static JMenuItem gameParametersItem(Container parent) {
    for (Component child : parent.getComponents()) {
      if (child instanceof JMenuItem item
          && Lizzie.resourceBundle.getString("EngineGameParameters.entry").equals(item.getText()))
        return item;
      Container nested =
          child instanceof JMenu menu
              ? menu.getPopupMenu()
              : child instanceof Container container ? container : null;
      if (nested != null) {
        JMenuItem found = gameParametersItem(nested);
        if (found != null) return found;
      }
    }
    return null;
  }

  private void hidden(String label) throws Exception {
    await(
        () -> Lizzie.engineGame.ordinaryParametersHidden() && hiddenViolation() == null,
        10,
        "complete ordinary groups hidden " + label);
    // Keep observing after queued menu/layout work, not just a transient property value.
    long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(400);
    do {
      edt(
          () -> {
            assertNull(hiddenViolation(), label);
            return null;
          });
      Thread.sleep(25);
    } while (System.nanoTime() < until);
    record("full-ordinary-groups-hidden." + label + "=PASS");
  }

  private static String hiddenViolation() throws Exception {
    Menu menu = LizzieFrame.menu;
    Component[] parts = {
      menu.chkWRN,
      menu.lblWRN,
      menu.txtWRN,
      menu.lblWRNForDouble,
      menu.wrnFieldPanel,
      menu.chkPDA,
      menu.lblGfPDA,
      menu.txtGfPDA,
      menu.lblGfPDAForDouble,
      menu.pdaFieldPanel,
      menu.lblPDASpinner,
      menu.lblCustomPda,
      menu.txtPDA,
      menu.more2,
      menu.customPDAMorePanel
    };
    for (Component part : parts) if (showing(part)) return "Visible ordinary component: " + part;
    Field separator = Menu.class.getDeclaredField("sepForPdaWrn");
    separator.setAccessible(true);
    if (showing((Component) separator.get(menu))) return "Ordinary separator remains visible";
    JComponent komi = menu.komiPanel;
    if (Lizzie.config.showDoubleMenu) {
      if (!showing(komi)) return "Missing double-row komi anchor";
      List<Component> visible =
          Arrays.stream(komi.getParent().getComponents())
              .filter(IntegratedParameterAcceptance::showing)
              .toList();
      int index = visible.indexOf(komi);
      if (index + 1 < visible.size()) {
        Component next = visible.get(index + 1);
        boolean sameRow =
            next.getY() < komi.getY() + komi.getHeight()
                && next.getY() + next.getHeight() > komi.getY();
        if (sameRow && next.getX() - komi.getX() - komi.getWidth() > 4)
          return "Empty ordinary parameter slot";
        if (isSeparator(next) && index + 2 < visible.size() && isSeparator(visible.get(index + 2)))
          return "Useless doubled separator";
      }
    } else if (showing(komi) && showing(menu.toolPanel)) {
      int right = 0;
      for (Component child : komi.getComponents()) {
        if (child != menu.toolPanel && showing(child) && child.getX() < menu.toolPanel.getX())
          right = Math.max(right, child.getX() + child.getWidth());
      }
      if (menu.toolPanel.getX() - right > 4) return "Empty single-row parameter slot";
    }
    return null;
  }

  private static boolean isSeparator(Component component) {
    return component instanceof JSeparator
        || component.getClass().getSimpleName().equals("ToolbarSeparator");
  }

  private static boolean showing(Component component) {
    return component != null
        && component.isShowing()
        && component.getWidth() > 0
        && component.getHeight() > 0
        && (!(component instanceof JComponent swing) || !swing.getVisibleRect().isEmpty());
  }

  private void assertOrdinaryShown(double wrn, double pda) throws Exception {
    edt(
        () -> {
          assertTrue(Lizzie.config.showDoubleMenu);
          for (Component component :
              List.of(
                  LizzieFrame.menu.wrnFieldPanel,
                  LizzieFrame.menu.chkWRN,
                  LizzieFrame.menu.txtWRN,
                  LizzieFrame.menu.pdaFieldPanel,
                  LizzieFrame.menu.chkPDA,
                  LizzieFrame.menu.txtGfPDA))
            assertTrue(showing(component), "Ordinary component visible: " + component);
          assertTrue(ordinaryValues(wrn, pda));
          return null;
        });
  }

  private static boolean ordinaryValues(double wrn, double pda) {
    Menu menu = LizzieFrame.menu;
    try {
      return menu.chkWRN.isSelected()
          && menu.chkPDA.isSelected()
          && menu.txtWRN.isEnabled()
          && menu.txtGfPDA.isEnabled()
          && Math.abs(Double.parseDouble(menu.txtWRN.getText()) - wrn) < 1e-9
          && Math.abs(Double.parseDouble(menu.txtGfPDA.getText()) - pda) < 1e-9;
    } catch (NumberFormatException notLoaded) {
      return false;
    }
  }

  private void switchLayout(boolean doubleMenu) throws Exception {
    edt(
        () -> {
          if (Lizzie.config.showDoubleMenu != doubleMenu) LizzieFrame.menu.btnDoubleMenu.doClick();
          return null;
        });
    await(() -> Lizzie.config.showDoubleMenu == doubleMenu, 10, "production layout switch");
  }

  private Map<String, String> preferences() throws Exception {
    Map<String, String> values =
        edt(
            () -> {
              Map<String, String> snapshot = new TreeMap<>();
              for (Field field : Config.class.getFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && parameterKey(field.getName()))
                  snapshot.put(
                      "config." + field.getName(), String.valueOf(field.get(Lizzie.config)));
              }
              parameterPreferences("memory.", Lizzie.config.uiConfig, snapshot);
              snapshot.put(
                  "memory.engines",
                  String.valueOf(Lizzie.config.leelazConfig.opt("engine-settings-list")));
              return snapshot;
            });
    JSONObject file = new JSONObject(Files.readString(work.resolve("config.txt")));
    parameterPreferences("file.", file.getJSONObject("ui"), values);
    values.put(
        "file.engines", String.valueOf(file.getJSONObject("leelaz").opt("engine-settings-list")));
    return values;
  }

  private static boolean parameterKey(String key) {
    String lower = key.toLowerCase(Locale.ROOT);
    return lower.contains("wrn") || lower.contains("pda");
  }

  private static void parameterPreferences(String prefix, JSONObject ui, Map<String, String> into) {
    for (String key : ui.keySet())
      if (parameterKey(key)) into.put(prefix + key, String.valueOf(ui.opt(key)));
  }

  private int[][] parameterWrites() throws Exception {
    int[][] counts = new int[2][2];
    for (int i = 0; i < 2; i++) {
      String log = log(i);
      for (int p = 0; p < 2; p++)
        counts[i][p] =
            (int)
                Pattern.compile("Controller: \\d*\\s*kata-set-param " + (p == 0 ? WRN : PDA) + " ")
                    .matcher(log)
                    .results()
                    .count();
    }
    return counts;
  }

  private void openingProtocol() throws Exception {
    StringBuilder evidence = new StringBuilder();
    OffsetDateTime firstSearch = null;
    List<OffsetDateTime> confirmed = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      String segment = log(i).substring(gameLogStarts[i]);
      Files.writeString(work.resolve("match-opening-protocol-" + i + ".txt"), segment);
      Matcher search = SEARCH.matcher(segment);
      assertTrue(search.find(), "Real participant " + i + " starts match analysis");
      int searchAt = search.start();
      String searchLine = lineAt(segment, searchAt);
      OffsetDateTime time = protocolTime(searchLine);
      if (firstSearch == null || time.isBefore(firstSearch)) firstSearch = time;
      String before = segment.substring(0, searchAt);
      String[] names = {WRN, PDA};
      double[] expected = i == 0 ? new double[] {0.2, 0.5} : new double[] {0.4, -0.5};
      evidence.append("participant=").append(i + 1).append('\n');
      for (int p = 0; p < 2; p++) {
        assertEquals(
            1L,
            Pattern.compile("Controller: \\d*\\s*kata-set-param " + names[p] + " ")
                .matcher(segment)
                .results()
                .count(),
            "Only the opening setter is sent during visibility/panel changes");
        Matcher set =
            Pattern.compile(
                    "Controller: \\d*\\s*kata-set-param "
                        + names[p]
                        + " "
                        + Pattern.quote(Double.toString(expected[p]))
                        + "(?:\\s|$)")
                .matcher(before);
        assertTrue(set.find(), "Opening setter precedes first search: " + names[p]);
        evidence.append(lineAt(before, set.start())).append('\n');
        Matcher get =
            Pattern.compile(
                    "Controller: (\\d+) kata-get-param " + names[p] + "\\s*$", Pattern.MULTILINE)
                .matcher(before);
        assertTrue(get.find(set.end()), "Opening readback follows setter: " + names[p]);
        String id = get.group(1);
        evidence.append(lineAt(before, get.start())).append('\n');
        Matcher response =
            Pattern.compile("(?m)^([^\\r\\n]*): =" + id + "\\s+([^\\r\\n]+)$").matcher(before);
        assertTrue(
            response.find(get.end()), "Successful opening readback precedes search: " + names[p]);
        assertEquals(expected[p], Double.parseDouble(response.group(2).trim()), 1e-9);
        String responseLine = response.group();
        confirmed.add(protocolTime(responseLine));
        evidence.append(responseLine).append('\n');
      }
      evidence.append("first-search: ").append(searchLine).append('\n');
    }
    for (OffsetDateTime confirmation : confirmed)
      assertFalse(
          confirmation.isAfter(firstSearch),
          "Neither participant confirms after the first match search");
    evidence.append(
        "KataGo timestamps have one-second precision; per-process response/search order above is exact.\n");
    Files.writeString(work.resolve("opening-confirmation-before-search.txt"), evidence);
    record(
        "opening-confirmed-values-before-participant-search=PASS\nopening-protocol="
            + work.resolve("opening-confirmation-before-search.txt"));
  }

  private static OffsetDateTime protocolTime(String line) {
    return OffsetDateTime.parse(
        line.substring(0, 24), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssxx"));
  }

  private static String lineAt(String text, int offset) {
    int start = text.lastIndexOf('\n', offset) + 1;
    int end = text.indexOf('\n', offset);
    return text.substring(start, end < 0 ? text.length() : end).stripTrailing();
  }

  private String log(int index) throws Exception {
    try (var paths = Files.list(work.resolve("gtp-" + index))) {
      StringBuilder text = new StringBuilder();
      for (Path path : paths.filter(Files::isRegularFile).sorted().toList())
        text.append(Files.readString(path));
      return text.toString();
    }
  }

  private static String wrnText(double wrn) {
    return Lizzie.resourceBundle.getString("SGFParse.wrn") + wrn;
  }

  private static String pdaText(double pda) {
    return Lizzie.resourceBundle.getString("SGFParse.pda") + pda;
  }

  private static boolean visibleCommentContains(Container parent, String expected)
      throws javax.swing.text.BadLocationException {
    for (Component component : parent.getComponents()) {
      if (component instanceof JTextPane pane
          && pane.isShowing()
          && pane.getDocument().getText(0, pane.getDocument().getLength()).contains(expected))
        return true;
      if (component instanceof Container child && visibleCommentContains(child, expected))
        return true;
    }
    return false;
  }

  private static boolean labelContains(Container parent, String expected) {
    for (Component component : parent.getComponents()) {
      if (component instanceof JLabel label
          && label.isShowing()
          && expected.equals(label.getText())) return true;
      if (component instanceof Container child && labelContains(child, expected)) return true;
    }
    return false;
  }

  private static void collectFields(Container parent, List<JTextField> fields) {
    for (Component component : parent.getComponents()) {
      if (component instanceof JTextField field) fields.add(field);
      else if (component instanceof Container child) collectFields(child, fields);
    }
  }

  private void screenshot(String name) throws Exception {
    new Robot().waitForIdle();
    Rectangle bounds =
        edt(
            () -> {
              Rectangle frame = Lizzie.frame.getBounds();
              for (Window window : Window.getWindows())
                if (window instanceof EngineGameParametersDialog && window.isShowing())
                  frame = frame.union(window.getBounds());
              return frame;
            });
    Path file = Files.createDirectories(work.resolve("screenshots")).resolve(name + ".png");
    assertTrue(ImageIO.write(new Robot().createScreenCapture(bounds), "png", file.toFile()));
    record("screenshot." + name + "=" + file);
  }

  private void record(String text) throws Exception {
    Files.writeString(result, text + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
  }

  private static <T> T edt(Callable<T> action) throws Exception {
    FutureTask<T> task = new FutureTask<>(action);
    SwingUtilities.invokeLater(task);
    return task.get(10, TimeUnit.SECONDS);
  }

  private static void await(Callable<Boolean> condition, int seconds, String label)
      throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
    do {
      if (edt(condition)) return;
      Thread.sleep(25);
    } while (System.nanoTime() < deadline);
    throw new AssertionError("Timed out waiting for " + label);
  }
}
