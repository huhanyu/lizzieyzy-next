package featurecat.lizzie.teacher;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.rules.BoardHistoryNode;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.accessibility.AccessibleContext;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

/** Non-modal AI commentary window backed only by existing KataGo analysis evidence. */
public final class TeacherDialog extends JDialog {
  private static TeacherDialog activeDialog;

  private final TeacherSettings settings = TeacherSettings.createDefault();
  private final TeacherRequestController requests = new TeacherRequestController();
  private final ConcurrentLinkedQueue<String> pendingText = new ConcurrentLinkedQueue<>();
  private final Timer textFlushTimer;

  private final JEditorPane output = new JEditorPane();
  private final StringBuilder rawOutput = new StringBuilder();
  private final JLabel status = new JLabel(" ");
  private final JLabel modelStatus = new JLabel(" ", SwingConstants.RIGHT);
  private final JButton explainNext =
      new JButton(TeacherStrings.get("Teacher.action.next", "Explain next move"));
  private final JButton explainRange =
      new JButton(TeacherStrings.get("Teacher.action.range", "Explain range"));
  private final JButton explainWhole =
      new JButton(TeacherStrings.get("Teacher.action.whole", "Explain whole game"));
  private final JButton stop = new JButton(TeacherStrings.get("Teacher.action.stop", "Stop"));
  private final JButton settingsButton =
      new JButton(TeacherStrings.get("Teacher.action.settings", "Settings"));
  private final JButton ask = new JButton(TeacherStrings.get("Teacher.action.ask", "Ask"));
  private final JCheckBox writeToSgf =
      new JCheckBox(
          TeacherStrings.get("Teacher.writeToSgf", "Write result to the SGF comment"), true);
  private final JTextField followUp = new JTextField();
  private final JSpinner rangeStart = new JSpinner();
  private final JSpinner rangeEnd = new JSpinner();
  private final JProgressBar progressBar = new JProgressBar();

  private BoardHistoryNode requestTarget;
  private List<TeacherLlmClient.Message> lastEvidenceContext = List.of();
  private List<TeacherEvidence.Position> lastEvidencePositions = List.of();
  private List<TeacherEvidence.Position> requestPositions = List.of();
  private String requestModel = "";
  private boolean requestRunning;
  private boolean settingsLoaded;
  private boolean settingsUsable;

  public static void show(Window owner) {
    if (activeDialog != null && activeDialog.isDisplayable()) {
      activeDialog.refreshFromBoard();
      activeDialog.setVisible(true);
      activeDialog.toFront();
      activeDialog.requestFocus();
      return;
    }
    activeDialog = new TeacherDialog(owner);
    activeDialog.setVisible(true);
  }

  private TeacherDialog(Window owner) {
    super(owner, TeacherStrings.get("Teacher.title", "AI commentary"), ModalityType.MODELESS);
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setContentPane(buildContent());
    setMinimumSize(new Dimension(760, 540));
    setSize(new Dimension(900, 680));
    setLocationRelativeTo(owner);
    getRootPane()
        .registerKeyboardAction(
            event -> dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);

    textFlushTimer = new Timer(140, event -> flushPendingText());
    textFlushTimer.setRepeats(false);

    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosed(WindowEvent event) {
            requests.close();
            textFlushTimer.stop();
            if (activeDialog == TeacherDialog.this) {
              activeDialog = null;
            }
          }
        });
    refreshFromBoard();
    setRunning(false);
    refreshSettingsStatus();
  }

  private JPanel buildContent() {
    JPanel content = new JPanel(new BorderLayout(0, 12));
    content.setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));

    JLabel title = new JLabel(TeacherStrings.get("Teacher.title", "AI commentary"));
    title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 7f));
    JLabel subtitle =
        new JLabel(
            TeacherStrings.get(
                "Teacher.subtitle",
                "Uses existing KataGo analysis; missing evidence is never invented."));
    subtitle.setForeground(mutedText());
    JPanel headingText = new JPanel(new GridBagLayout());
    GridBagConstraints headingConstraints = new GridBagConstraints();
    headingConstraints.gridx = 0;
    headingConstraints.gridy = 0;
    headingConstraints.weightx = 1.0;
    headingConstraints.anchor = GridBagConstraints.WEST;
    headingConstraints.fill = GridBagConstraints.HORIZONTAL;
    headingText.add(title, headingConstraints);
    headingConstraints.gridy = 1;
    headingConstraints.insets = new Insets(4, 0, 0, 0);
    headingText.add(subtitle, headingConstraints);

    JPanel heading = new JPanel(new BorderLayout(12, 0));
    heading.add(headingText, BorderLayout.CENTER);
    heading.add(settingsButton, BorderLayout.EAST);

    JPanel rangePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    JLabel from = new JLabel(TeacherStrings.get("Teacher.range.from", "From"));
    JLabel to = new JLabel(TeacherStrings.get("Teacher.range.to", "to"));
    from.setLabelFor(rangeStart);
    to.setLabelFor(rangeEnd);
    rangePanel.add(from);
    rangePanel.add(rangeStart);
    rangePanel.add(to);
    rangePanel.add(rangeEnd);

    JPanel actions = new JPanel(new BorderLayout(10, 0));
    JPanel primaryActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    primaryActions.add(explainNext);
    primaryActions.add(explainRange);
    primaryActions.add(explainWhole);
    primaryActions.add(stop);
    actions.add(primaryActions, BorderLayout.WEST);
    actions.add(rangePanel, BorderLayout.EAST);

    JPanel header = new JPanel(new BorderLayout(0, 14));
    header.add(heading, BorderLayout.NORTH);
    header.add(actions, BorderLayout.SOUTH);
    content.add(header, BorderLayout.NORTH);

    output.setContentType("text/html");
    output.setEditable(false);
    output.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
    Color outputForeground = uiColor("TextPane.foreground", output.getForeground());
    Color outputBackground = uiColor("TextPane.background", output.getBackground());
    Color secondaryBackground = uiColor("TextField.background", outputBackground);
    output.setForeground(outputForeground);
    output.setBackground(outputBackground);
    HTMLEditorKit kit = new HTMLEditorKit();
    StyleSheet style = kit.getStyleSheet();
    String fontFamily = output.getFont().getFamily().replace("'", "\\'");
    style.addRule(
        "body { font-family: '"
            + fontFamily
            + "', sans-serif; font-size: 14px; margin: 14px; color: "
            + cssColor(outputForeground)
            + "; background-color: "
            + cssColor(outputBackground)
            + "; }");
    style.addRule("h1 { font-size: 22px; margin: 14px 0 6px 0; }");
    style.addRule("h2 { font-size: 19px; margin: 13px 0 5px 0; }");
    style.addRule("h3 { font-size: 16px; margin: 12px 0 4px 0; }");
    style.addRule("b, strong { font-weight: bold; }");
    style.addRule(
        "code { background-color: "
            + cssColor(secondaryBackground)
            + "; padding: 1px 4px; font-family: monospace; }");
    style.addRule(
        "pre { background-color: "
            + cssColor(secondaryBackground)
            + "; padding: 8px; border: 1px solid "
            + cssColor(borderColor())
            + "; font-family: monospace; }");
    style.addRule("ul { margin: 4px 0; padding-left: 20px; }");
    style.addRule("ol { margin: 4px 0; padding-left: 24px; }");
    style.addRule(
        "blockquote { color: "
            + cssColor(mutedText())
            + "; border-left: 3px solid "
            + cssColor(borderColor())
            + "; margin: 8px 0; padding-left: 10px; }");
    output.setEditorKit(kit);
    output.setText("<html><body></body></html>");
    output
        .getAccessibleContext()
        .setAccessibleName(TeacherStrings.get("Teacher.output", "AI commentary result"));
    JScrollPane outputScroll = new JScrollPane(output);
    outputScroll.setBorder(BorderFactory.createLineBorder(borderColor()));
    content.add(outputScroll, BorderLayout.CENTER);

    JPanel statusRow = new JPanel(new BorderLayout(12, 0));
    status.setForeground(mutedText());
    modelStatus.setForeground(mutedText());
    status
        .getAccessibleContext()
        .setAccessibleName(TeacherStrings.get("Teacher.status.accessible", "Commentary status"));
    modelStatus
        .getAccessibleContext()
        .setAccessibleName(TeacherStrings.get("Teacher.model.accessible", "Selected AI model"));
    statusRow.add(status, BorderLayout.CENTER);
    statusRow.add(modelStatus, BorderLayout.EAST);

    JLabel followUpLabel = new JLabel(TeacherStrings.get("Teacher.followUp", "Follow-up question"));
    followUpLabel.setLabelFor(followUp);
    JPanel followUpRow = new JPanel(new BorderLayout(8, 0));
    followUpRow.add(followUpLabel, BorderLayout.WEST);
    followUpRow.add(followUp, BorderLayout.CENTER);
    followUpRow.add(ask, BorderLayout.EAST);

    JPanel footer = new JPanel(new BorderLayout(0, 8));
    progressBar.setIndeterminate(true);
    progressBar.setVisible(false);
    progressBar
        .getAccessibleContext()
        .setAccessibleName(
            TeacherStrings.get("Teacher.progress.accessible", "Commentary generation progress"));
    JPanel bottomPanel = new JPanel(new java.awt.BorderLayout(0, 4));
    bottomPanel.add(followUpRow, java.awt.BorderLayout.NORTH);
    bottomPanel.add(writeToSgf, java.awt.BorderLayout.SOUTH);
    footer.add(statusRow, java.awt.BorderLayout.NORTH);
    footer.add(progressBar, java.awt.BorderLayout.CENTER);
    footer.add(bottomPanel, java.awt.BorderLayout.SOUTH);
    content.add(footer, BorderLayout.SOUTH);

    explainNext.addActionListener(event -> explainNextMove());
    explainRange.addActionListener(event -> explainRange());
    explainWhole.addActionListener(event -> explainWholeGame());
    stop.addActionListener(event -> stopRequest());
    settingsButton.addActionListener(
        event -> {
          if (TeacherSettingsDialog.show(this, settings)) {
            refreshSettingsStatus();
          }
        });
    ask.addActionListener(event -> askFollowUp());
    followUp.addActionListener(event -> askFollowUp());

    explainNext
        .getAccessibleContext()
        .setAccessibleDescription(
            TeacherStrings.get(
                "Teacher.action.next.description",
                "Compare the recorded next move with KataGo's top candidates."));
    stop.getAccessibleContext()
        .setAccessibleDescription(
            TeacherStrings.get(
                "Teacher.action.stop.description", "Cancel the active network request."));
    return content;
  }

  private void refreshFromBoard() {
    if (Lizzie.board == null || Lizzie.board.getHistory() == null) {
      setStatus(TeacherStrings.get("Teacher.status.noGame", "No game is loaded."));
      return;
    }
    BoardHistoryNode current = Lizzie.board.getHistory().getCurrentHistoryNode();
    int lastMove = Math.max(1, Lizzie.board.getHistory().getStart().getLast().getData().moveNumber);
    rangeStart.setModel(new SpinnerNumberModel(1, 1, lastMove, 1));
    rangeEnd.setModel(new SpinnerNumberModel(lastMove, 1, lastMove, 1));
    Optional<String> saved = TeacherCommentCodec.extract(current.getData().comment);
    if (!requests.isRunning() && saved.isPresent()) {
      lastEvidenceContext = List.of();
      lastEvidencePositions = List.of();
      rawOutput.setLength(0);
      rawOutput.append(saved.get());
      output.setText(markdownToHtml(rawOutput.toString()));
      output.setCaretPosition(0);
      setStatus(
          TeacherStrings.get(
              "Teacher.status.savedLoaded", "Loaded saved commentary from this SGF node."));
    } else if (!requests.isRunning()) {
      lastEvidenceContext = List.of();
      lastEvidencePositions = List.of();
      setStatus(evidenceStatus(current));
    }
  }

  private void refreshSettingsStatus() {
    settingsLoaded = false;
    settingsUsable = false;
    updateControlState();
    modelStatus.setText(
        TeacherStrings.get("Teacher.status.loadingSettings", "Loading secure settings..."));
    new SwingWorker<TeacherSettings.Snapshot, Void>() {
      @Override
      protected TeacherSettings.Snapshot doInBackground() throws Exception {
        return settings.load();
      }

      @Override
      protected void done() {
        settingsLoaded = true;
        try {
          TeacherSettings.Snapshot snapshot = get();
          settingsUsable = true;
          modelStatus.setText(
              snapshot.hasApiKey
                  ? TeacherStrings.format("Teacher.status.modelReady", "Model: {0}", snapshot.model)
                  : TeacherStrings.get(
                      "Teacher.status.needsKey", "Configure an API key before use"));
        } catch (Exception error) {
          settingsUsable = false;
          modelStatus.setText(localError(error));
        }
        updateControlState();
      }
    }.execute();
  }

  private void explainNextMove() {
    BoardHistoryNode current = currentNode();
    if (current == null) {
      return;
    }
    Optional<TeacherEvidence.Position> position = TeacherEvidence.current(current);
    if (position.isEmpty()) {
      setStatus(
          TeacherStrings.get(
              "Teacher.status.needsAnalysis",
              "This position has no KataGo candidates yet. Analyze it first."));
      return;
    }
    lastEvidenceContext =
        TeacherPromptBuilder.forPosition(
            position.get(), TeacherStrings.locale(), settings.snapshot());
    lastEvidencePositions = List.of(position.get());
    startRequest(lastEvidenceContext, current);
  }

  private void explainRange() {
    BoardHistoryNode root = rootNode();
    if (root == null) {
      return;
    }
    int first = ((Number) rangeStart.getValue()).intValue();
    int last = ((Number) rangeEnd.getValue()).intValue();
    if (first > last) {
      int temporary = first;
      first = last;
      last = temporary;
    }
    TeacherEvidence.Range evidence = TeacherEvidence.mainLine(root, first, last);
    if (evidence.isEmpty()) {
      setStatus(
          TeacherStrings.get(
              "Teacher.status.rangeNeedsAnalysis",
              "No analyzed positions were found in this range."));
      return;
    }
    lastEvidenceContext =
        TeacherPromptBuilder.forRange(
            evidence,
            TeacherPromptBuilder.Mode.RANGE,
            TeacherStrings.locale(),
            settings.snapshot());
    lastEvidencePositions = evidence.positions;
    startRequest(
        lastEvidenceContext,
        currentNode(),
        TeacherStrings.format(
            "Teacher.status.evidenceReady",
            "{0} key positions selected ({1} analyzed, {2} omitted). Generating commentary...",
            evidence.positions.size(),
            evidence.analyzedPositions,
            evidence.omittedPositions));
  }

  private void explainWholeGame() {
    BoardHistoryNode root = rootNode();
    if (root == null) {
      return;
    }
    TeacherEvidence.Range evidence = TeacherEvidence.wholeGame(root);
    if (evidence.isEmpty()) {
      setStatus(
          TeacherStrings.get(
              "Teacher.status.rangeNeedsAnalysis",
              "No analyzed positions were found in this game."));
      return;
    }
    lastEvidenceContext =
        TeacherPromptBuilder.forRange(
            evidence,
            TeacherPromptBuilder.Mode.WHOLE_GAME,
            TeacherStrings.locale(),
            settings.snapshot());
    lastEvidencePositions = evidence.positions;
    startRequest(
        lastEvidenceContext,
        root,
        TeacherStrings.format(
            "Teacher.status.evidenceReady",
            "{0} key positions selected ({1} analyzed, {2} omitted). Generating commentary...",
            evidence.positions.size(),
            evidence.analyzedPositions,
            evidence.omittedPositions));
  }

  private void askFollowUp() {
    String question = followUp.getText().trim();
    if (question.isEmpty()) {
      return;
    }
    if (lastEvidenceContext.isEmpty()) {
      BoardHistoryNode current = currentNode();
      if (current == null) {
        return;
      }
      Optional<TeacherEvidence.Position> position = TeacherEvidence.current(current);
      if (position.isEmpty()) {
        setStatus(
            TeacherStrings.get(
                "Teacher.status.needsAnalysis",
                "This position has no KataGo candidates yet. Analyze it first."));
        return;
      }
      lastEvidenceContext =
          TeacherPromptBuilder.forPosition(
              position.get(), TeacherStrings.locale(), settings.snapshot());
      lastEvidencePositions = List.of(position.get());
    }
    startRequest(
        TeacherPromptBuilder.forFollowUp(
            lastEvidenceContext,
            rawOutput.toString(),
            question,
            TeacherStrings.locale(),
            settings.snapshot()),
        currentNode());
    followUp.setText("");
  }

  private void startRequest(List<TeacherLlmClient.Message> messages, BoardHistoryNode targetNode) {
    startRequest(
        messages,
        targetNode,
        TeacherStrings.get("Teacher.status.requesting", "Generating commentary..."));
  }

  private void startRequest(
      List<TeacherLlmClient.Message> messages, BoardHistoryNode targetNode, String runningStatus) {
    messages = appendKnowledge(messages, targetNode);
    TeacherLlmClient client = configuredClient();
    if (client == null) {
      return;
    }
    TeacherSettings.Snapshot snapshot = settings.snapshot();
    requestModel = snapshot.model;
    requestTarget = targetNode;
    requestPositions = List.copyOf(lastEvidencePositions);
    pendingText.clear();
    rawOutput.setLength(0);
    output.setText("<html><body></body></html>");
    setRunning(true);
    setStatus(runningStatus);
    requests.start(
        client,
        messages,
        new TeacherRequestController.Listener() {
          @Override
          public void onText(String text) {
            queuePendingText(text);
          }

          @Override
          public void onComplete(String fullText) {
            SwingUtilities.invokeLater(() -> completeRequest(fullText));
          }

          @Override
          public void onFailure(Throwable error) {
            SwingUtilities.invokeLater(() -> failRequest(error));
          }

          @Override
          public void onCancelled() {
            SwingUtilities.invokeLater(() -> cancelledRequest());
          }
        });
  }

  /** 把知识库匹配结果（定式/棋形）拼到最后一条 user 消息；无匹配不改动。 */
  private static List<TeacherLlmClient.Message> appendKnowledge(
      List<TeacherLlmClient.Message> messages, BoardHistoryNode node) {
    if (messages == null || messages.isEmpty()) {
      return messages;
    }
    String knowledge = TeacherEvidence.knowledgeMatchText(node);
    if (knowledge.isEmpty()) {
      return messages;
    }
    java.util.ArrayList<TeacherLlmClient.Message> out = new java.util.ArrayList<>(messages);
    int last = out.size() - 1;
    TeacherLlmClient.Message message = out.get(last);
    if ("user".equals(message.role)) {
      out.set(
          last,
          new TeacherLlmClient.Message(
              message.role, message.content + "\n\n【Knowledge】\n" + knowledge));
    }
    return out;
  }

  private TeacherLlmClient configuredClient() {
    try {
      TeacherSettings.Snapshot snapshot = settings.load();
      if (!snapshot.hasApiKey) {
        if (!TeacherSettingsDialog.show(this, settings)) {
          return null;
        }
        snapshot = settings.snapshot();
      }
      Optional<String> apiKey = settings.apiKey();
      if (apiKey.isEmpty()) {
        setStatus(TeacherStrings.get("Teacher.status.needsKey", "Configure an API key before use"));
        return null;
      }
      return new TeacherLlmClient(snapshot.baseUrl, apiKey.get(), snapshot.model);
    } catch (Exception error) {
      setStatus(localError(error));
      return null;
    }
  }

  private void completeRequest(String fullText) {
    flushPendingText();
    String result = fullText == null ? "" : fullText.trim();
    if (result.isEmpty()) {
      failRequest(new IllegalStateException("AI service returned an empty response."));
      return;
    }
    rawOutput.setLength(0);
    rawOutput.append(result);
    output.setText(markdownToHtml(result));
    output.setCaretPosition(0);
    appendVerifierNotes(result);
    if (writeToSgf.isSelected() && requestTarget != null && requestTarget.getData() != null) {
      requestTarget.getData().comment =
          TeacherCommentCodec.upsert(requestTarget.getData().comment, result, requestModel);
      if (Lizzie.frame != null) {
        Lizzie.frame.refresh();
      }
      setStatus(
          TeacherStrings.get(
              "Teacher.status.completedSaved",
              "Commentary completed and added to the SGF comment."));
    } else {
      setStatus(TeacherStrings.get("Teacher.status.completed", "Commentary completed."));
    }
    setRunning(false);
  }

  /** 防编造校验：轻量 TeacherVerifier + 重型 QualityGate（claim 级核对），附到输出末尾（不阻断显示）。 */
  private void appendVerifierNotes(String result) {
    try {
      TeacherVerifier.Result verification = TeacherVerifier.verify(result, requestPositions);
      java.util.ArrayList<String> notes = new java.util.ArrayList<>(verification.violations);
      notes.addAll(verification.warnings);
      appendQualityGateNotes(result, notes);
      if (notes.isEmpty()) {
        return;
      }
      java.util.ArrayList<String> shown = new java.util.ArrayList<>();
      for (String note : notes) {
        shown.add(note);
        if (shown.size() >= 4) {
          break;
        }
      }
      StringBuilder builder =
          new StringBuilder("\n\n> ")
              .append(TeacherStrings.get("Teacher.verify.note", "Verifier notes"))
              .append(": ")
              .append(String.join("; ", shown));
      rawOutput.append(builder);
      output.setText(markdownToHtml(rawOutput.toString()));
    } catch (Exception ignored) {
      // 校验失败不阻断解说显示
    }
  }

  /** 重型校验链：构建 MoveAnalysis → TeachingEvidence → QualityGate（结构化/claim 级核对）。 */
  private void appendQualityGateNotes(String result, java.util.ArrayList<String> notes) {
    if (requestTarget == null
        || requestTarget.getData() == null
        || requestPositions.size() != 1
        || requestPositions.get(0).moveNumber != requestTarget.getData().moveNumber) {
      return;
    }
    try {
      MoveAnalysis analysis = TeacherEvidence.moveAnalysis(requestTarget);
      TeachingEvidenceBuilder.TeachingEvidence evidence =
          TeachingEvidenceBuilder.buildTeachingEvidence(
              analysis, "", java.util.List.of(), java.util.List.of(), java.util.List.of());
      featurecat.lizzie.teacher.analysis.QualityGate.TeacherQualityGateResult gate =
          featurecat.lizzie.teacher.analysis.QualityGate.runTeacherQualityGate(
              result, evidence, false);
      notes.addAll(gate.violations);
      notes.addAll(gate.warnings);
    } catch (Exception ignored) {
      // 重型校验失败不阻断解说显示
    }
  }

  private void failRequest(Throwable error) {
    flushPendingText();
    setStatus(
        TeacherStrings.format(
            "Teacher.status.failed", "Commentary failed: {0}", localError(error)));
    setRunning(false);
  }

  private void cancelledRequest() {
    flushPendingText();
    setStatus(TeacherStrings.get("Teacher.status.cancelled", "Commentary stopped."));
    setRunning(false);
  }

  private void stopRequest() {
    if (!requests.isRunning()) {
      return;
    }
    requests.cancel();
    cancelledRequest();
  }

  private void flushPendingText() {
    StringBuilder addition = new StringBuilder();
    String text;
    while ((text = pendingText.poll()) != null) {
      addition.append(text);
    }
    if (addition.length() > 0) {
      rawOutput.append(addition);
      output.setText(markdownToHtml(rawOutput.toString()));
      output.setCaretPosition(output.getDocument().getLength());
    }
  }

  private void setRunning(boolean running) {
    requestRunning = running;
    progressBar.setVisible(running);
    if (!running) {
      textFlushTimer.stop();
    }
    updateControlState();
  }

  private void queuePendingText(String text) {
    if (text == null || text.isEmpty()) {
      return;
    }
    pendingText.add(text);
    SwingUtilities.invokeLater(
        () -> {
          if (isDisplayable() && requestRunning && !textFlushTimer.isRunning()) {
            textFlushTimer.start();
          }
        });
  }

  private void updateControlState() {
    boolean ready = settingsLoaded && settingsUsable && !requestRunning;
    explainNext.setEnabled(ready);
    explainRange.setEnabled(ready);
    explainWhole.setEnabled(ready);
    settingsButton.setEnabled(settingsLoaded && !requestRunning);
    ask.setEnabled(ready);
    followUp.setEnabled(ready);
    rangeStart.setEnabled(ready);
    rangeEnd.setEnabled(ready);
    stop.setEnabled(requestRunning);
  }

  private BoardHistoryNode currentNode() {
    if (Lizzie.board == null || Lizzie.board.getHistory() == null) {
      setStatus(TeacherStrings.get("Teacher.status.noGame", "No game is loaded."));
      return null;
    }
    return Lizzie.board.getHistory().getCurrentHistoryNode();
  }

  private BoardHistoryNode rootNode() {
    if (Lizzie.board == null || Lizzie.board.getHistory() == null) {
      setStatus(TeacherStrings.get("Teacher.status.noGame", "No game is loaded."));
      return null;
    }
    return Lizzie.board.getHistory().getStart();
  }

  private String evidenceStatus(BoardHistoryNode node) {
    Optional<TeacherEvidence.Position> position = TeacherEvidence.current(node);
    if (position.isEmpty()) {
      return TeacherStrings.get(
          "Teacher.status.needsAnalysis",
          "This position has no KataGo candidates yet. Analyze it first.");
    }
    return TeacherStrings.format(
        "Teacher.status.ready",
        "Ready: move {0}, {1} KataGo candidates.",
        position.get().moveNumber,
        position.get().candidates.size());
  }

  private void setStatus(String message) {
    String previous = status.getText();
    String next = message == null || message.isBlank() ? " " : message;
    status.setText(next);
    status.setToolTipText(status.getText());
    status
        .getAccessibleContext()
        .firePropertyChange(AccessibleContext.ACCESSIBLE_DESCRIPTION_PROPERTY, previous, next);
  }

  private static String localError(Throwable error) {
    Throwable cause = error;
    while (cause != null && cause.getCause() != null) {
      cause = cause.getCause();
    }
    String message = cause == null ? "" : cause.getMessage();
    return message == null || message.isBlank()
        ? TeacherStrings.get("Teacher.error.generic", "The operation failed.")
        : message;
  }

  private static Color mutedText() {
    Color color = UIManager.getColor("Label.disabledForeground");
    return color == null ? Color.GRAY : color;
  }

  private static Color borderColor() {
    Color color = UIManager.getColor("Separator.foreground");
    return color == null ? new Color(190, 190, 190) : color;
  }

  static String markdownToHtml(String markdown) {
    if (markdown == null || markdown.isEmpty()) {
      return "<html><body></body></html>";
    }
    String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
    StringBuilder html = new StringBuilder("<html><body>");
    String openList = null;
    boolean inCodeBlock = false;
    for (String line : normalized.split("\n", -1)) {
      if (line.startsWith("```")) {
        openList = closeList(html, openList);
        if (inCodeBlock) {
          html.append("</code></pre>");
        } else {
          html.append("<pre><code>");
        }
        inCodeBlock = !inCodeBlock;
        continue;
      }
      if (inCodeBlock) {
        appendEscaped(html, line);
        html.append('\n');
        continue;
      }
      boolean unordered = line.startsWith("- ") || line.startsWith("* ");
      boolean ordered = line.matches("\\d+\\.\\s+.*");
      if (unordered || ordered) {
        String listType = unordered ? "ul" : "ol";
        if (!listType.equals(openList)) {
          openList = closeList(html, openList);
          html.append('<').append(listType).append('>');
          openList = listType;
        }
        int contentStart = unordered ? 2 : line.indexOf('.') + 1;
        while (contentStart < line.length() && Character.isWhitespace(line.charAt(contentStart))) {
          contentStart++;
        }
        html.append("<li>").append(inlineMarkdown(line.substring(contentStart))).append("</li>");
        continue;
      }
      openList = closeList(html, openList);
      if (line.startsWith("### ")) {
        html.append("<h3>").append(inlineMarkdown(line.substring(4))).append("</h3>");
      } else if (line.startsWith("## ")) {
        html.append("<h2>").append(inlineMarkdown(line.substring(3))).append("</h2>");
      } else if (line.startsWith("# ")) {
        html.append("<h1>").append(inlineMarkdown(line.substring(2))).append("</h1>");
      } else if (line.startsWith("> ")) {
        html.append("<blockquote>")
            .append(inlineMarkdown(line.substring(2)))
            .append("</blockquote>");
      } else if (line.trim().isEmpty()) {
        html.append("<div>&nbsp;</div>");
      } else {
        html.append("<div>").append(inlineMarkdown(line)).append("</div>");
      }
    }
    closeList(html, openList);
    if (inCodeBlock) {
      html.append("</code></pre>");
    }
    html.append("</body></html>");
    return html.toString();
  }

  private static String inlineMarkdown(String text) {
    StringBuilder html = new StringBuilder();
    int index = 0;
    while (index < text.length()) {
      if (text.charAt(index) == '`') {
        int end = text.indexOf('`', index + 1);
        if (end > index + 1) {
          html.append("<code>");
          appendEscaped(html, text.substring(index + 1, end));
          html.append("</code>");
          index = end + 1;
          continue;
        }
      }
      if (text.startsWith("**", index)) {
        int end = text.indexOf("**", index + 2);
        if (end > index + 2) {
          html.append("<strong>");
          appendEscaped(html, text.substring(index + 2, end));
          html.append("</strong>");
          index = end + 2;
          continue;
        }
      }
      if (text.charAt(index) == '*') {
        int end = text.indexOf('*', index + 1);
        if (end > index + 1) {
          html.append("<em>");
          appendEscaped(html, text.substring(index + 1, end));
          html.append("</em>");
          index = end + 1;
          continue;
        }
      }
      appendEscaped(html, text.charAt(index));
      index++;
    }
    return html.toString();
  }

  private static String closeList(StringBuilder html, String openList) {
    if (openList != null) {
      html.append("</").append(openList).append('>');
    }
    return null;
  }

  private static void appendEscaped(StringBuilder html, String text) {
    for (int index = 0; index < text.length(); index++) {
      appendEscaped(html, text.charAt(index));
    }
  }

  private static void appendEscaped(StringBuilder html, char character) {
    switch (character) {
      case '&':
        html.append("&amp;");
        break;
      case '<':
        html.append("&lt;");
        break;
      case '>':
        html.append("&gt;");
        break;
      case '"':
        html.append("&quot;");
        break;
      case '\'':
        html.append("&#39;");
        break;
      default:
        html.append(character);
        break;
    }
  }

  private static Color uiColor(String key, Color fallback) {
    Color color = UIManager.getColor(key);
    return color == null ? fallback : color;
  }

  private static String cssColor(Color color) {
    Color safe = color == null ? Color.BLACK : color;
    return String.format("#%02x%02x%02x", safe.getRed(), safe.getGreen(), safe.getBlue());
  }
}
