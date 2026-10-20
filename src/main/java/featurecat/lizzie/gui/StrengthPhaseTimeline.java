package featurecat.lizzie.gui;

import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/**
 * The whole game as a bar split into opening, middle game and endgame. The two dividers can be
 * dragged, or moved with the arrow keys once focused, to change where each phase starts.
 */
final class StrengthPhaseTimeline extends JPanel {
  private static final long serialVersionUID = 1L;
  private final transient StrengthStyle style;
  private final transient ResourceBundle bundle;
  private final transient Consumer<int[]> onChange;
  private final transient Consumer<int[]> onCommit;
  private final Divider[] dividers = new Divider[2];
  private int[] bounds;
  private int lastMove;
  private int frozenAxisEnd = -1;

  /**
   * {@code onChange} hears every step of a drag, {@code onCommit} the boundaries a drag or key
   * press ended with.
   */
  StrengthPhaseTimeline(
      int[] bounds,
      int lastMove,
      ResourceBundle bundle,
      StrengthStyle style,
      Consumer<int[]> onChange,
      Consumer<int[]> onCommit) {
    super(null);
    this.style = style;
    this.bundle = bundle;
    this.onChange = onChange;
    this.onCommit = onCommit;
    this.bounds = StrengthPhases.normalize(bounds);
    this.lastMove = Math.max(0, lastMove);
    setOpaque(false);
    for (int i = 0; i < dividers.length; i++) {
      dividers[i] = new Divider(i);
      add(dividers[i]);
    }
    updateNames();
  }

  int[] phaseBounds() {
    return bounds.clone();
  }

  Divider divider(int index) {
    return dividers[index];
  }

  /** Puts the dividers back where they start out, and says so like a drag would. */
  void reset() {
    bounds = StrengthPhases.DEFAULT_BOUNDS.clone();
    updateNames();
    doLayout();
    repaint();
    onChange.accept(bounds.clone());
    onCommit.accept(bounds.clone());
  }

  /**
   * The last move the bar shows: the whole game, or further when the game ended before the last
   * divider. It holds still while a divider is dragged.
   */
  int axisEnd() {
    if (frozenAxisEnd > 0) {
      return frozenAxisEnd;
    }
    return Math.max(
        lastMove,
        Math.max(StrengthPhases.DEFAULT_BOUNDS[1], bounds[1]) + StrengthPhases.MIN_PHASE_MOVES);
  }

  private int barLeft() {
    return style.px(1);
  }

  private int barWidth() {
    return Math.max(1, getWidth() - style.px(2));
  }

  private int barHeight() {
    return style.px(34);
  }

  private int barTop() {
    return style.px(5);
  }

  /** Where the boundary after move {@code move} lies. */
  double xOf(double move) {
    return barLeft() + barWidth() * Math.max(0, Math.min(1, move / axisEnd()));
  }

  /** The move whose boundary lies nearest to {@code x}. */
  int moveAt(double x) {
    return (int) Math.round((x - barLeft()) / barWidth() * axisEnd());
  }

  private Font axisFont() {
    return style.font(Font.PLAIN, -3);
  }

  @Override
  public Dimension getPreferredSize() {
    FontMetrics axis = getFontMetrics(axisFont());
    return new Dimension(
        style.px(600), barTop() + barHeight() + style.px(5) + style.px(6) + axis.getHeight());
  }

  @Override
  public void doLayout() {
    for (Divider divider : dividers) {
      int width = style.px(16);
      int height = barHeight() + style.px(10);
      int center = (int) Math.round(xOf(bounds[divider.index]));
      divider.setBounds(center - width / 2, barTop() - style.px(5), width, height);
    }
  }

  /** Moves divider {@code index} to the boundary after {@code move}, within its neighbours. */
  boolean moveDivider(int index, int move) {
    int low =
        index == 0 ? StrengthPhases.MIN_PHASE_MOVES : bounds[0] + StrengthPhases.MIN_PHASE_MOVES;
    int high =
        index == 0
            ? bounds[1] - StrengthPhases.MIN_PHASE_MOVES
            : Math.max(low, axisEnd() - StrengthPhases.MIN_PHASE_MOVES);
    int target = Math.max(low, Math.min(high, move));
    if (target == bounds[index]) {
      return false;
    }
    bounds[index] = target;
    updateNames();
    doLayout();
    repaint();
    onChange.accept(bounds.clone());
    return true;
  }

  private void updateNames() {
    String[] names = phaseNames();
    for (Divider divider : dividers) {
      divider
          .getAccessibleContext()
          .setAccessibleName(
              MessageFormat.format(
                  bundle.getString("PlayerStrengthEstimate.phase.divider"),
                  names[divider.index],
                  names[divider.index + 1],
                  bounds[divider.index]));
    }
  }

  private String[] phaseNames() {
    String[] names = new String[StrengthPhases.NAMES.length];
    for (int i = 0; i < names.length; i++) {
      names[i] = bundle.getString("PlayerStrengthEstimate.phase." + StrengthPhases.NAMES[i]);
    }
    return names;
  }

  /** The text inside phase {@code index}: its name and moves, or that the game ended before it. */
  String bandLabel(int index) {
    int first = index == 0 ? 1 : bounds[index - 1] + 1;
    int last = index == 2 ? lastMove : Math.min(bounds[index], lastMove);
    return phaseNames()[index]
        + " \u00b7 "
        + (last < first
            ? bundle.getString("PlayerStrengthEstimate.phase.unplayed")
            : first + "\u2013" + last);
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = StrengthStyle.smooth(graphics);
    try {
      double top = barTop();
      double height = barHeight();
      double arc = style.px(10);
      Shape bar = new RoundRectangle2D.Double(barLeft(), top, barWidth(), height, arc, arc);
      g.setColor(style.sunken);
      g.fill(bar);
      double[] edges = {barLeft(), xOf(bounds[0]), xOf(bounds[1]), barLeft() + barWidth()};
      int[] tints = {26, 52, 84};
      Font labelFont = style.font(Font.BOLD, -1);
      FontMetrics labelMetrics = g.getFontMetrics(labelFont);
      int labelBaseline =
          (int)
              (top
                  + (height - labelMetrics.getAscent() - labelMetrics.getDescent()) / 2
                  + labelMetrics.getAscent());
      for (int i = 0; i < 3; i++) {
        Area band = new Area(bar);
        band.intersect(
            new Area(new Rectangle2D.Double(edges[i], top, edges[i + 1] - edges[i], height)));
        g.setColor(StrengthStyle.alpha(style.accent, tints[i]));
        g.fill(band);
        int room = (int) Math.max(0, edges[i + 1] - edges[i] - style.px(20));
        String label = bandLabel(i);
        if (labelMetrics.stringWidth(label) > room) {
          label = phaseNames()[i];
        }
        if (labelMetrics.stringWidth(label) <= room) {
          g.setFont(labelFont);
          g.setColor(style.text);
          g.drawString(
              label,
              (float) ((edges[i] + edges[i + 1]) / 2 - labelMetrics.stringWidth(label) / 2.0),
              labelBaseline);
        }
      }
      if (lastMove < axisEnd()) {
        double end = xOf(lastMove);
        Area beyond = new Area(bar);
        beyond.intersect(
            new Area(new Rectangle2D.Double(end, top, barLeft() + barWidth() - end, height)));
        g.setColor(StrengthStyle.alpha(style.background, 150));
        g.fill(beyond);
        g.setColor(StrengthStyle.alpha(style.tertiaryText, 110));
        g.setStroke(
            new BasicStroke(
                1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {3f, 3f}, 0f));
        g.draw(new Line2D.Double(end, top + style.px(4), end, top + height - style.px(4)));
      }
      paintAxis(g, top + height + style.px(5));
    } finally {
      g.dispose();
    }
  }

  private void paintAxis(Graphics2D g, double top) {
    Font font = axisFont();
    FontMetrics metrics = g.getFontMetrics(font);
    int end = axisEnd();
    int step = end <= 120 ? 20 : end <= 400 ? 50 : 100;
    g.setFont(font);
    g.setStroke(new BasicStroke(1f));
    int baseline = (int) (top + style.px(6) + metrics.getAscent());
    int previousRight = Integer.MIN_VALUE;
    for (int move = step; move < end; move += step) {
      double x = xOf(move);
      g.setColor(StrengthStyle.alpha(style.tertiaryText, 150));
      g.draw(new Line2D.Double(x, top, x, top + style.px(3)));
      String label = String.valueOf(move);
      int width = metrics.stringWidth(label);
      int left = (int) Math.round(x - width / 2.0);
      if (left > previousRight + style.px(6) && left + width <= getWidth()) {
        g.setColor(style.tertiaryText);
        g.drawString(label, left, baseline);
        previousRight = left + width;
      }
    }
  }

  /** A grip on a boundary between two phases. */
  final class Divider extends JPanel {
    private static final long serialVersionUID = 1L;
    private final int index;
    private boolean dragging;
    private boolean hover;

    private Divider(int index) {
      this.index = index;
      setOpaque(false);
      setFocusable(true);
      setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
      StrengthFocus.install(this);
      MouseAdapter mouse =
          new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
              frozenAxisEnd = axisEnd();
              dragging = true;
              requestFocusInWindow();
              repaint();
            }

            @Override
            public void mouseDragged(MouseEvent event) {
              StrengthPhaseTimeline.this.moveDivider(index, moveAt(getX() + event.getX()));
            }

            @Override
            public void mouseReleased(MouseEvent event) {
              frozenAxisEnd = -1;
              dragging = false;
              StrengthPhaseTimeline.this.doLayout();
              StrengthPhaseTimeline.this.repaint();
              onCommit.accept(bounds.clone());
            }

            @Override
            public void mouseEntered(MouseEvent event) {
              hover = true;
              repaint();
            }

            @Override
            public void mouseExited(MouseEvent event) {
              hover = false;
              repaint();
            }
          };
      addMouseListener(mouse);
      addMouseMotionListener(mouse);
      bindStep("LEFT", -1);
      bindStep("RIGHT", 1);
      bindStep("shift LEFT", -10);
      bindStep("shift RIGHT", 10);
    }

    int index() {
      return index;
    }

    private void bindStep(String key, int step) {
      String name = "strength-phase-" + key;
      getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), name);
      getActionMap()
          .put(
              name,
              new AbstractAction() {
                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(ActionEvent event) {
                  if (StrengthPhaseTimeline.this.moveDivider(index, bounds[index] + step)) {
                    onCommit.accept(bounds.clone());
                  }
                }
              });
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      Graphics2D g = StrengthStyle.smooth(graphics);
      try {
        double width = style.px(8);
        double x = (getWidth() - width) / 2.0;
        double height = getHeight();
        RoundRectangle2D grip =
            new RoundRectangle2D.Double(x, 0.5, width, height - 1, width, width);
        g.setColor(style.surface);
        g.fill(grip);
        g.setColor(dragging || hover ? style.accent : style.border);
        g.setStroke(new BasicStroke(dragging ? 1.6f : 1f));
        g.draw(grip);
        g.setColor(dragging || hover ? style.accent : style.tertiaryText);
        double middle = height / 2.0;
        double line = style.px(3);
        g.draw(new Line2D.Double(getWidth() / 2.0, middle - line, getWidth() / 2.0, middle + line));
        if (StrengthFocus.visible(this)) {
          StrengthPaint.focusRing(
              g,
              style,
              new RoundRectangle2D.Double(
                  x - 2, -1.5, width + 4, height + 3, width + 4, width + 4));
        }
      } finally {
        g.dispose();
      }
    }
  }
}
