package featurecat.lizzie.gui;

import featurecat.lizzie.Config;
import featurecat.lizzie.Lizzie;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.MissingResourceException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * The AI Coach panel at the right of the window while a coaching game runs: who you play, the
 * game's state, captures and time, your win chance while analysis is shown, your last move, the
 * result, and the game's actions. It replaces the old bottom training bar. Its buttons never take
 * keyboard focus, so the board keeps Space and its other shortcuts.
 */
public final class CoachPanel extends JPanel {
  static final int WIDTH = 360;

  static final Color BACKGROUND = new Color(0x17, 0x19, 0x1a);
  static final Color SURFACE = new Color(0x20, 0x24, 0x26);
  static final Color RAISED = new Color(0x26, 0x2b, 0x2d);
  static final Color LINE = new Color(0x2b, 0x30, 0x32);
  static final Color TEXT = new Color(0xec, 0xef, 0xee);
  static final Color MUTED = new Color(0xa2, 0xab, 0xa7);
  static final Color ACCENT = new Color(0x3d, 0xbe, 0xaa);
  static final Color ACCENT_TEXT = new Color(0x8e, 0xe6, 0xd6);
  static final Color WARNING = new Color(0xf2, 0xc1, 0x4e);
  static final Color DANGER = new Color(0xff, 0x8a, 0x7f);

  private final LizzieFrame frame;
  private final Supplier<Config> config;
  private final CoachFocusLayout focusLayout = new CoachFocusLayout();
  private final Summary summary = new Summary();
  private final CoachButton takeBackButton = CoachButton.secondary();
  private final CoachButton passButton = CoachButton.secondary();
  private final CoachButton resignButton = CoachButton.secondary();
  private final CoachButton retryButton = CoachButton.primary();
  private final CoachButton finishButton = CoachButton.quiet();
  private final Timer refreshTimer = new Timer(500, event -> refreshFromController());
  private HumanSlGameController controller;
  private CoachPanelState state = CoachPanelState.EMPTY;

  CoachPanel(LizzieFrame frame) {
    this(frame, () -> Lizzie.config);
  }

  CoachPanel(LizzieFrame frame, Supplier<Config> config) {
    super(new BorderLayout(0, 16));
    this.frame = frame;
    this.config = config;
    setOpaque(true);
    setBackground(BACKGROUND);
    setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 1, 0, 0, LINE),
            BorderFactory.createEmptyBorder(18, 18, 18, 18)));
    setVisible(false);
    add(summary, BorderLayout.NORTH);
    add(buildActions(), BorderLayout.SOUTH);
  }

  public void attach(HumanSlGameController value) {
    controller = value;
    if (value == null) {
      return;
    }
    focusLayout.enter(frame, config.get());
    setVisible(true);
    refreshTimer.start();
    refreshFromController();
  }

  public void detach(HumanSlGameController value) {
    if (value != null && controller != value) {
      return;
    }
    controller = null;
    refreshTimer.stop();
    setVisible(false);
    focusLayout.exit(frame, config.get());
  }

  /** Re-reads the game; the controller calls this after every change. */
  public void refresh() {
    refreshFromController();
  }

  CoachPanelState state() {
    return state;
  }

  List<CoachButton> buttons() {
    return List.of(takeBackButton, passButton, resignButton, retryButton, finishButton);
  }

  private JPanel buildActions() {
    JPanel actions = new JPanel(new BorderLayout(0, 8));
    actions.setOpaque(false);
    JPanel grid = new JPanel(new GridLayout(0, 2, 8, 8));
    grid.setOpaque(false);
    takeBackButton.setText(text("HumanSlTraining.takeBack", "Take back"));
    passButton.setText(text("HumanSlGame.btn.pass", "Pass"));
    resignButton.setText(text("HumanSlGame.btn.resign", "Resign"));
    retryButton.setText(text("HumanSlTraining.retryAi", "Retry AI"));
    finishButton.setText(text("CoachPanel.finish", "End game"));
    takeBackButton.addActionListener(event -> withController(HumanSlGameController::takeBack));
    passButton.addActionListener(event -> withController(HumanSlGameController::humanPass));
    resignButton.addActionListener(event -> withController(HumanSlGameController::resign));
    retryButton.addActionListener(event -> withController(HumanSlGameController::retryAiMove));
    finishButton.addActionListener(
        event ->
            withController(
                active -> {
                  if (active.isStopRequested() || active.isReviewing()) {
                    active.abort();
                  } else {
                    active.finishAndReturnToBoard();
                  }
                }));
    grid.add(takeBackButton);
    grid.add(passButton);
    grid.add(resignButton);
    grid.add(retryButton);
    actions.add(grid, BorderLayout.CENTER);
    actions.add(finishButton, BorderLayout.SOUTH);
    return actions;
  }

  private void refreshFromController() {
    HumanSlGameController active = controller;
    if (active == null) {
      return;
    }
    if (active.isFinished()) {
      detach(active);
      return;
    }
    state = CoachPanelState.of(active, this::text);
    boolean stopping = active.isStopRequested();
    takeBackButton.setEnabled(!stopping && active.canTakeBack());
    passButton.setEnabled(
        !stopping
            && active.isHumanTurn()
            && !active.isReviewing()
            && !active.isGameOver()
            && !active.isCounting());
    resignButton.setEnabled(!stopping && active.canResign());
    retryButton.setVisible(!stopping && active.hasAiFailure() && !active.isReviewing());
    if (active.isExitRecoveryPending()) {
      finishButton.setText(text("HumanSlTraining.retryCleanup", "Retry cleanup"));
      finishButton.setKind(CoachButton.Kind.PRIMARY);
    } else if (stopping) {
      finishButton.setText(text("HumanSlTraining.stopping", "Stopping..."));
      finishButton.setKind(CoachButton.Kind.QUIET);
    } else if (active.isGameOver()) {
      finishButton.setText(text("CoachPanel.review", "Start review"));
      finishButton.setKind(CoachButton.Kind.PRIMARY);
    } else {
      finishButton.setText(text("CoachPanel.finish", "End game"));
      finishButton.setKind(CoachButton.Kind.QUIET);
    }
    finishButton.setEnabled(!active.isExitInProgress());
    summary.revalidate();
    revalidate();
    repaint();
  }

  private void withController(Consumer<HumanSlGameController> action) {
    HumanSlGameController active = controller;
    if (active != null) {
      action.accept(active);
    }
    refreshFromController();
  }

  private String text(String key, String fallback) {
    try {
      return Lizzie.resourceBundle == null ? fallback : Lizzie.resourceBundle.getString(key);
    } catch (MissingResourceException e) {
      return fallback;
    }
  }

  private static Font font(String text, int style, float size) {
    return HumanSlTrainingStyle.fontForText(text, style, Math.round(size));
  }

  /** Everything above the buttons, drawn by hand from {@link #state}. */
  private final class Summary extends JComponent {
    private static final int GAP = 12;

    @Override
    public Dimension getPreferredSize() {
      int width = Math.max(0, WIDTH - 37);
      return new Dimension(width, layout(null, width));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      Graphics2D g = (Graphics2D) graphics.create();
      try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        layout(g, getWidth());
      } finally {
        g.dispose();
      }
    }

    /** Draws when {@code g} is set, and returns the height either way. */
    private int layout(Graphics2D g, int width) {
      CoachPanelState s = state;
      int y = header(g, s, width, 0);
      y = players(g, s, width, y + GAP);
      if (s.winrates().length > 0) {
        y = winChance(g, s, width, y + GAP);
      }
      if (s.lastMove() != null) {
        y = lastMove(g, s, width, y + GAP);
      }
      if (s.result() != null) {
        y = result(g, s, width, y + GAP);
      }
      return y;
    }

    private int header(Graphics2D g, CoachPanelState s, int width, int y) {
      if (g != null) {
        g.setColor(RAISED);
        g.fillOval(0, y, 44, 44);
        stone(g, 22, y + 22, 13, s.aiPlaysBlack());
        Font pillFont = font(s.status(), Font.BOLD, 12.5f);
        FontMetrics pill = g.getFontMetrics(pillFont);
        int pillWidth = pill.stringWidth(s.status()) + 20;
        int pillX = width - pillWidth;
        Color[] tone = tone(s.tone());
        g.setColor(tone[0]);
        g.fillRoundRect(pillX, y + 9, pillWidth, 26, 26, 26);
        g.setColor(tone[1]);
        g.setFont(pillFont);
        g.drawString(
            s.status(), pillX + 10, y + 9 + (26 - pill.getHeight()) / 2 + pill.getAscent());
        int textWidth = pillX - 56 - 8;
        g.setColor(TEXT);
        Font titleFont = font(s.title(), Font.BOLD, 17f);
        g.setFont(titleFont);
        g.drawString(fit(g, s.title(), textWidth), 56, y + 20);
        g.setColor(MUTED);
        Font subtitleFont = font(s.subtitle(), Font.PLAIN, 12.5f);
        g.setFont(subtitleFont);
        g.drawString(fit(g, s.subtitle(), textWidth), 56, y + 39);
      }
      return y + 44;
    }

    private int players(Graphics2D g, CoachPanelState s, int width, int y) {
      int chipWidth = (width - 8) / 2;
      if (g != null) {
        chip(g, 0, y, chipWidth, !s.aiPlaysBlack(), s.humanLine(), s.humanDetail());
        chip(
            g, chipWidth + 8, y, width - chipWidth - 8, s.aiPlaysBlack(), s.aiLine(), s.aiDetail());
        g.setColor(MUTED);
        g.setFont(font(s.timeLine(), Font.PLAIN, 12f));
        g.drawString(fit(g, s.timeLine(), width), 2, y + 40 + 20);
      }
      return y + 40 + 26;
    }

    private void chip(
        Graphics2D g, int x, int y, int width, boolean black, String label, String detail) {
      g.setColor(SURFACE);
      g.fillRoundRect(x, y, width, 40, 20, 20);
      stone(g, x + 18, y + 20, 6, black);
      g.setFont(font(detail, Font.PLAIN, 12.5f));
      FontMetrics detailMetrics = g.getFontMetrics();
      int detailWidth = detailMetrics.stringWidth(detail);
      g.setColor(MUTED);
      g.drawString(detail, x + width - 12 - detailWidth, y + 25);
      g.setColor(TEXT);
      g.setFont(font(label, Font.PLAIN, 13f));
      g.drawString(fit(g, label, width - 30 - detailWidth - 18), x + 30, y + 25);
    }

    private int winChance(Graphics2D g, CoachPanelState s, int width, int y) {
      int height = 124;
      if (g != null) {
        double[] values = s.winrates();
        double current = values[values.length - 1];
        g.setColor(SURFACE);
        g.fillRoundRect(0, y, width, height, 24, 24);
        String label = text("CoachPanel.winChance", "Your win chance");
        g.setColor(TEXT);
        g.setFont(font(label, Font.BOLD, 14f));
        g.drawString(label, 14, y + 27);
        int labelWidth = g.getFontMetrics().stringWidth(label);
        String caption = text("CoachPanel.winChanceLive", "KataGo live analysis");
        g.setColor(MUTED);
        g.setFont(font(caption, Font.PLAIN, 11.5f));
        String percent = Math.round(current) + "%";
        Font valueFont = font(percent, Font.BOLD, 24f);
        int valueWidth = g.getFontMetrics(valueFont).stringWidth(percent);
        g.drawString(
            fit(g, caption, width - 28 - labelWidth - 8 - valueWidth - 8),
            14 + labelWidth + 8,
            y + 27);
        g.setColor(TEXT);
        g.setFont(valueFont);
        g.drawString(percent, width - 14 - valueWidth, y + 31);
        int trackX = 14;
        int trackWidth = width - 28;
        int trackY = y + 44;
        g.setColor(new Color(0x2c, 0x32, 0x35));
        g.fillRoundRect(trackX, trackY, trackWidth, 8, 8, 8);
        g.setColor(ACCENT);
        int fill = (int) Math.round(trackWidth * Math.max(0.0, Math.min(100.0, current)) / 100.0);
        if (fill > 0) {
          g.fillRoundRect(trackX, trackY, Math.max(8, fill), 8, 8, 8);
        }
        g.setColor(new Color(236, 239, 238, 140));
        g.fillRect(trackX + trackWidth / 2 - 1, trackY - 3, 2, 14);
        curve(g, values, trackX, y + 64, trackWidth, 46);
      }
      return y + height;
    }

    private void curve(Graphics2D g, double[] values, int x, int y, int width, int height) {
      Graphics2D dashed = (Graphics2D) g.create();
      dashed.setColor(new Color(0x3a, 0x41, 0x44));
      dashed.setStroke(
          new BasicStroke(
              1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] {3f, 4f}, 0f));
      dashed.drawLine(x, y + height / 2, x + width, y + height / 2);
      dashed.dispose();
      if (values.length < 2) {
        return;
      }
      Path2D line = new Path2D.Double();
      Path2D area = new Path2D.Double();
      for (int i = 0; i < values.length; i++) {
        double px = x + width * (double) i / (values.length - 1);
        double py = y + height * (1.0 - Math.max(0.0, Math.min(100.0, values[i])) / 100.0);
        if (i == 0) {
          line.moveTo(px, py);
          area.moveTo(px, y + height);
          area.lineTo(px, py);
        } else {
          line.lineTo(px, py);
          area.lineTo(px, py);
        }
      }
      area.lineTo(x + width, y + height);
      area.closePath();
      g.setPaint(
          new GradientPaint(
              0, y, new Color(61, 190, 170, 70), 0, y + height, new Color(61, 190, 170, 0)));
      g.fill(area);
      g.setColor(ACCENT);
      g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.draw(line);
      g.setStroke(new BasicStroke(1f));
    }

    private int lastMove(Graphics2D g, CoachPanelState s, int width, int y) {
      int height = 62;
      if (g != null) {
        g.setColor(SURFACE);
        g.fillRoundRect(0, y, width, height, 24, 24);
        String label = text("CoachPanel.lastMove", "Your last move");
        g.setColor(MUTED);
        g.setFont(font(label, Font.PLAIN, 12.5f));
        g.drawString(label, 14, y + 23);
        g.setColor(TEXT);
        g.setFont(font(s.lastMove(), Font.BOLD, 15f));
        g.drawString(fit(g, s.lastMove(), width - 28), 14, y + 47);
      }
      return y + height;
    }

    private int result(Graphics2D g, CoachPanelState s, int width, int y) {
      Font hintFont = font(s.resultHint(), Font.PLAIN, 12.5f);
      List<String> hint = wrap(s.resultHint(), getFontMetrics(hintFont), width - 32);
      int height = 92 + hint.size() * 19;
      if (g != null) {
        g.setColor(RAISED);
        g.fillRoundRect(0, y, width, height, 28, 28);
        g.setColor(new Color(61, 190, 170, 90));
        g.drawRoundRect(0, y, width - 1, height - 1, 28, 28);
        String label = text("CoachPanel.gameOver", "Game over");
        g.setColor(MUTED);
        g.setFont(font(label, Font.PLAIN, 12.5f));
        g.drawString(label, 16, y + 26);
        g.setColor(TEXT);
        g.setFont(font(s.result(), Font.BOLD, 24f));
        g.drawString(fit(g, s.result(), width - 32), 16, y + 60);
        g.setColor(MUTED);
        g.setFont(hintFont);
        for (int i = 0; i < hint.size(); i++) {
          g.drawString(hint.get(i), 16, y + 86 + i * 19);
        }
      }
      return y + height;
    }

    private void stone(Graphics2D g, int cx, int cy, int radius, boolean black) {
      Color[] colors =
          black
              ? new Color[] {new Color(0x6c, 0x6c, 0x6c), new Color(0x11, 0x11, 0x11)}
              : new Color[] {Color.WHITE, new Color(0xcf, 0xcb, 0xc2)};
      g.setPaint(
          new RadialGradientPaint(
              cx - radius * 0.3f, cy - radius * 0.4f, radius * 1.4f, new float[] {0f, 1f}, colors));
      g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
      if (black) {
        // A light rim keeps a black stone visible on the dark panel.
        g.setColor(new Color(0x6a, 0x74, 0x78));
        g.setStroke(new BasicStroke(1.2f));
        g.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
        g.setStroke(new BasicStroke(1f));
      }
    }

    private Color[] tone(CoachPanelState.Tone tone) {
      switch (tone) {
        case ACTIVE:
          return new Color[] {new Color(61, 190, 170, 42), ACCENT_TEXT};
        case WARNING:
          return new Color[] {new Color(242, 193, 78, 42), WARNING};
        case ALERT:
          return new Color[] {new Color(255, 122, 110, 42), DANGER};
        case DONE:
          return new Color[] {RAISED, TEXT};
        case WAITING:
        default:
          return new Color[] {RAISED, MUTED};
      }
    }

    private String fit(Graphics2D g, String value, int width) {
      if (value == null) {
        return "";
      }
      FontMetrics metrics = g.getFontMetrics();
      if (metrics.stringWidth(value) <= width) {
        return value;
      }
      String ellipsis = "…";
      for (int end = value.length() - 1; end > 0; end--) {
        String candidate = value.substring(0, end) + ellipsis;
        if (metrics.stringWidth(candidate) <= width) {
          return candidate;
        }
      }
      return ellipsis;
    }

    /** Splits {@code value} into lines; CJK text may break between any two characters. */
    private List<String> wrap(String value, FontMetrics metrics, int width) {
      List<String> lines = new ArrayList<>();
      if (value == null || value.isEmpty()) {
        return lines;
      }
      StringBuilder line = new StringBuilder();
      int lastSpace = -1;
      for (int i = 0; i < value.length(); i++) {
        char c = value.charAt(i);
        line.append(c);
        if (c == ' ') {
          lastSpace = line.length() - 1;
        }
        if (metrics.stringWidth(line.toString()) > width && line.length() > 1) {
          if (lastSpace > 0) {
            lines.add(line.substring(0, lastSpace));
            line.delete(0, lastSpace + 1);
          } else {
            lines.add(line.substring(0, line.length() - 1));
            line.delete(0, line.length() - 1);
          }
          lastSpace = line.lastIndexOf(" ");
        }
      }
      if (line.length() > 0) {
        lines.add(line.toString());
      }
      return lines;
    }
  }

  /** Keeps translucent overlays from tinting the panel's own background in screenshots. */
  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      g.setComposite(AlphaComposite.Src);
      g.setColor(BACKGROUND);
      g.fillRect(0, 0, getWidth(), getHeight());
    } finally {
      g.dispose();
    }
  }
}
