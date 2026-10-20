package featurecat.lizzie.gui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.geom.RoundRectangle2D;
import java.util.ResourceBundle;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * What the strength dialog shows instead of the figures while they cannot be computed yet: a game
 * without analysis, with the button that analyzes it, or the human-style model before its one-time
 * calculation, with the button that starts it and the switch that prepares it while analyzing.
 */
final class StrengthEmptyState extends JPanel {
  private static final long serialVersionUID = 1L;
  private static final String GAP = "strength.gapBefore";

  enum Kind {
    ANALYSIS,
    HUMAN_STYLE
  }

  private final transient StrengthStyle style;
  private final transient ResourceBundle bundle;
  private final Kind kind;
  private final StrengthParagraph title;
  private final StrengthParagraph body;
  private final StrengthButton action;
  private final JLabel status;
  private final transient StrengthDot dot;
  private final Progress progress;
  private final StrengthSwitch background;
  private final StrengthParagraph download;
  private boolean shortGame;

  StrengthEmptyState(
      Kind kind, boolean backgroundEnabled, ResourceBundle bundle, StrengthStyle style) {
    super(new GridBagLayout());
    this.kind = kind;
    this.style = style;
    this.bundle = bundle;
    setOpaque(false);
    String prefix = prefix(kind == Kind.ANALYSIS ? "analysis" : "humanStyle");
    JPanel column = new JPanel(new Column());
    column.setOpaque(false);
    stack(column, new Stones(), 0);
    title =
        new StrengthParagraph(
            bundle.getString(prefix + "title"),
            style.font(Font.BOLD, 6),
            style.text,
            StrengthPaint.CENTER,
            2,
            style);
    stack(column, title, 16);
    body =
        new StrengthParagraph(
            bundle.getString(prefix + "body"),
            style.font(Font.PLAIN, 1),
            style.secondaryText,
            StrengthPaint.CENTER,
            5,
            style);
    stack(column, body, 8);
    action =
        StrengthButton.primary(
            bundle.getString(kind == Kind.ANALYSIS ? prefix + "action" : "HumanSL40.manualRun"),
            style);
    action.setName(kind == Kind.ANALYSIS ? "strength-analyze" : "strength40-run");
    stack(column, action, 22);
    dot = new StrengthDot(style, style.px(7));
    status = new JLabel(" ", dot, SwingConstants.CENTER);
    status.setIconTextGap(style.px(7));
    status.setFont(style.font(Font.PLAIN, 0));
    status.setForeground(style.secondaryText);
    if (kind == Kind.HUMAN_STYLE) {
      status.setName("strength40-status");
    }
    stack(column, status, 16);
    progress = new Progress();
    stack(column, progress, 8);
    if (kind == Kind.HUMAN_STYLE) {
      stack(column, new Divider(), 24);
      background =
          new StrengthSwitch(
              bundle.getString("HumanSL40.backgroundToggle"), backgroundEnabled, style);
      background.setName("strength40-background");
      stack(column, background, 18);
      stack(
          column,
          new StrengthParagraph(
              bundle.getString("HumanSL40.backgroundToggle.description"),
              style.font(Font.PLAIN, -1),
              style.secondaryText,
              StrengthPaint.CENTER,
              4,
              style),
          6);
      download =
          new StrengthParagraph(
              bundle.getString(prefix + "download"),
              style.font(Font.PLAIN, -1),
              style.tertiaryText,
              StrengthPaint.CENTER,
              3,
              style);
      stack(column, download, 14);
    } else {
      background = null;
      download = null;
    }
    add(column);
    showProgress(null, StrengthDashboard.Tone.IDLE, 0, 0);
  }

  private static String prefix(String page) {
    return "PlayerStrengthEstimate.empty." + page + ".";
  }

  /** Adds {@code child} to the column, {@code gap} points below the visible child before it. */
  private void stack(JPanel column, JComponent child, int gap) {
    child.putClientProperty(GAP, style.px(gap));
    column.add(child);
  }

  Kind kind() {
    return kind;
  }

  StrengthButton actionButton() {
    return action;
  }

  /** The switch that prepares human-style data while analyzing; null for a game to analyze. */
  StrengthSwitch backgroundSwitch() {
    return background;
  }

  JLabel status() {
    return status;
  }

  String title() {
    return title.text();
  }

  /**
   * Shows a status line under the button, or hides it when {@code text} is null, with a progress
   * bar while there is work under way that knows its size.
   */
  void showProgress(String text, StrengthDashboard.Tone tone, int done, int total) {
    boolean shown = text != null && !text.isBlank();
    status.setVisible(shown);
    status.setText(shown ? text : " ");
    dot.setTone(tone);
    boolean working = shown && tone == StrengthDashboard.Tone.WORKING;
    if (kind == Kind.HUMAN_STYLE) {
      action.setEnabled(!working);
    }
    boolean measured = working && total > 0;
    progress.setVisible(measured);
    progress.set(done, total);
    revalidate();
    repaint();
  }

  void setActionEnabled(boolean enabled) {
    action.setEnabled(enabled);
  }

  /**
   * Says that the game is too short to estimate instead of asking for its analysis; the button goes
   * away because analysis would not help.
   */
  void showShortGame(boolean value) {
    if (kind != Kind.ANALYSIS || value == shortGame) {
      return;
    }
    shortGame = value;
    String prefix = prefix(value ? "short" : "analysis");
    title.setText(bundle.getString(prefix + "title"));
    body.setText(bundle.getString(prefix + "body"));
    action.setVisible(!value);
    revalidate();
    repaint();
  }

  boolean isShortGame() {
    return shortGame;
  }

  /** Mentions the one-time download of the human-style weights while they are missing. */
  void setDownloadNoteVisible(boolean visible) {
    if (download != null) {
      download.setVisible(visible);
    }
  }

  boolean isDownloadNoteVisible() {
    return download != null && download.isVisible();
  }

  private int columnWidth() {
    return style.px(540);
  }

  /**
   * Stacks its visible children, each centred, in a column of a fixed width, each one its gap below
   * the one before.
   */
  private final class Column implements LayoutManager {
    @Override
    public void addLayoutComponent(String name, Component component) {}

    @Override
    public void removeLayoutComponent(Component component) {}

    private int width(Container parent) {
      Container outer = parent.getParent();
      int available = outer == null || outer.getWidth() <= 0 ? columnWidth() : outer.getWidth();
      return Math.max(style.px(240), Math.min(columnWidth(), available - style.px(32)));
    }

    private Dimension size(Component child, int width) {
      if (child instanceof StrengthParagraph paragraph) {
        return new Dimension(width, paragraph.heightFor(width));
      }
      Dimension preferred = child.getPreferredSize();
      return new Dimension(Math.min(width, preferred.width), preferred.height);
    }

    private int gap(Component child, boolean first) {
      return !first && ((JComponent) child).getClientProperty(GAP) instanceof Integer gap ? gap : 0;
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      int width = width(parent);
      int height = 0;
      boolean first = true;
      for (Component child : parent.getComponents()) {
        if (child.isVisible()) {
          height += gap(child, first) + size(child, width).height;
          first = false;
        }
      }
      Insets insets = parent.getInsets();
      return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
      return preferredLayoutSize(parent);
    }

    @Override
    public void layoutContainer(Container parent) {
      Insets insets = parent.getInsets();
      int width = parent.getWidth() - insets.left - insets.right;
      int y = insets.top;
      boolean first = true;
      for (Component child : parent.getComponents()) {
        if (!child.isVisible()) {
          continue;
        }
        y += gap(child, first);
        first = false;
        Dimension size = size(child, width);
        child.setBounds(insets.left + (width - size.width) / 2, y, size.width, size.height);
        y += size.height;
      }
    }
  }

  /** A black and a white stone, overlapping. */
  private final class Stones extends JComponent {
    private static final long serialVersionUID = 1L;

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(style.px(62), style.px(42));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      Graphics2D g = StrengthStyle.smooth(graphics);
      try {
        double stone = style.px(38);
        double x = (getWidth() - stone * 1.55) / 2.0;
        StoneGlyph.paint(g, x, (getHeight() - stone) / 2.0, stone, true);
        StoneGlyph.paint(g, x + stone * 0.55, (getHeight() - stone) / 2.0, stone, false);
      } finally {
        g.dispose();
      }
    }
  }

  /** A thin hairline across the column. */
  private final class Divider extends JComponent {
    private static final long serialVersionUID = 1L;

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(style.px(420), 1);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      graphics.setColor(style.divider);
      graphics.fillRect(0, 0, getWidth(), 1);
    }
  }

  /** A slim rounded bar of how much of a known amount of work is done. */
  private final class Progress extends JComponent {
    private static final long serialVersionUID = 1L;
    private int done;
    private int total;

    void set(int done, int total) {
      this.done = Math.max(0, done);
      this.total = Math.max(0, total);
      repaint();
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(style.px(320), style.px(6));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      Graphics2D g = StrengthStyle.smooth(graphics);
      try {
        double height = getHeight();
        g.setColor(style.sunken);
        g.fill(new RoundRectangle2D.Double(0, 0, getWidth(), height, height, height));
        if (total > 0 && done > 0) {
          double width = Math.max(height, getWidth() * Math.min(1.0, done / (double) total));
          g.setColor(style.accent);
          g.fill(new RoundRectangle2D.Double(0, 0, width, height, height, height));
        }
      } finally {
        g.dispose();
      }
    }
  }
}
