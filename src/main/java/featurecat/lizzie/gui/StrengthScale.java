package featurecat.lizzie.gui;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;

/**
 * The rank ruler: kyu, amateur dan, professional, top professional and AI, with labelled ticks. The
 * dan ranks get the most room because most players are there, so that 1 kyu and 2 dan sit clearly
 * apart.
 */
final class StrengthScale {
  /** The rank values where each tier starts and ends; 1 is 1 dan, below it are the kyu ranks. */
  static final double[][] TIERS = {{-18, 1}, {1, 10}, {10, 11}, {11, 12}, {12, 13}};

  /** Each tier's share of the ruler. */
  static final double[] WEIGHTS = {0.22, 0.40, 0.126, 0.127, 0.127};

  /**
   * A tick: its rank value, label (or null), how much the label matters, and whether it is where
   * one tier ends and the next begins. Labels between two whole ranks name a tier and have no tick.
   */
  record Tick(double value, String label, int priority, boolean boundary) {}

  /**
   * A point on the ruler: a black or white stone, or the accent knob when {@code stone} is null.
   */
  record Marker(double rankValue, Boolean stone) {}

  private final StrengthStyle style;
  private final List<Tick> ticks;

  StrengthScale(ResourceBundle bundle, StrengthStyle style) {
    this.style = style;
    ticks = ticks(bundle);
  }

  /** The ticks of the ruler, from the weakest rank to AI. */
  static List<Tick> ticks(ResourceBundle bundle) {
    String kyu = bundle.getString("PlayerStrengthEstimate.scale.kyu");
    String dan = bundle.getString("PlayerStrengthEstimate.scale.dan");
    List<Tick> ticks = new ArrayList<>();
    ticks.add(new Tick(-13, MessageFormat.format(kyu, 15), 5, false));
    ticks.add(new Tick(-8, MessageFormat.format(kyu, 10), 3, false));
    ticks.add(new Tick(-3, MessageFormat.format(kyu, 5), 4, false));
    for (int rank = 1; rank <= 9; rank++) {
      int priority = rank == 1 ? 0 : rank == 5 || rank == 9 ? 1 : rank % 2 == 1 ? 2 : -1;
      ticks.add(
          new Tick(
              rank, priority < 0 ? null : MessageFormat.format(dan, rank), priority, rank == 1));
    }
    for (int boundary = 10; boundary <= 12; boundary++) {
      ticks.add(new Tick(boundary, null, -1, true));
    }
    ticks.add(new Tick(10.5, bundle.getString("PlayerStrengthEstimate.scale.pro"), 1, false));
    ticks.add(new Tick(11.5, bundle.getString("PlayerStrengthEstimate.scale.topPro"), 2, false));
    ticks.add(new Tick(12.5, bundle.getString("PlayerStrengthEstimate.scale.ai"), 0, false));
    return ticks;
  }

  List<Tick> ticks() {
    return ticks;
  }

  /** How far from the left of a ruler {@code width} wide the rank {@code value} sits. */
  double x(double value, double width) {
    double clamped = Math.max(TIERS[0][0], Math.min(TIERS[TIERS.length - 1][1], value));
    double x = 0;
    for (int i = 0; i < TIERS.length; i++) {
      double tierWidth = width * WEIGHTS[i];
      if (clamped < TIERS[i][1] || i == TIERS.length - 1) {
        double share = (clamped - TIERS[i][0]) / (TIERS[i][1] - TIERS[i][0]);
        return x + tierWidth * Math.max(0, Math.min(1, share));
      }
      x += tierWidth;
    }
    return x;
  }

  /** The tier {@code value} is in, from 0 (kyu) to 4 (AI). */
  static int tier(double value) {
    for (int i = TIERS.length - 1; i > 0; i--) {
      if (value >= TIERS[i][0]) {
        return i;
      }
    }
    return 0;
  }

  int markerSize() {
    return style.px(12);
  }

  private int trackHeight() {
    return style.px(6);
  }

  private Font labelFont() {
    return style.font(Font.PLAIN, -2);
  }

  /** The height of the ruler with its labels, measured with {@code component}'s font metrics. */
  int height(java.awt.Component component) {
    return markerSize() + style.px(7) + component.getFontMetrics(labelFont()).getHeight();
  }

  /**
   * Paints the ruler {@code width} wide from {@code x}, {@code top} with {@code markers} on it: one
   * unbroken track, with {@code fill} filled in one colour up to the first marker.
   */
  void paint(Graphics2D g, int x, int top, int width, List<Marker> markers, boolean fill) {
    double track = trackHeight();
    double marker = markerSize();
    double trackY = top + (marker - track) / 2.0;
    g.setColor(style.sunken);
    g.fill(new RoundRectangle2D.Double(x, trackY, width, track, track, track));
    if (fill && !markers.isEmpty() && Double.isFinite(markers.get(0).rankValue())) {
      double filled = Math.max(track, x(markers.get(0).rankValue(), width));
      g.setColor(style.accent);
      g.fill(new RoundRectangle2D.Double(x, trackY, filled, track, track, track));
    }
    paintTicks(g, x, top, width, trackY + track);
    paintMarkers(g, x, top, width, markers);
  }

  private void paintTicks(Graphics2D g, int x, int top, int width, double trackBottom) {
    Font font = labelFont();
    FontMetrics metrics = g.getFontMetrics(font);
    double tickTop = trackBottom + style.px(2);
    int baseline = top + markerSize() + style.px(7) + metrics.getAscent();
    g.setFont(font);
    List<Rectangle> placed = new ArrayList<>();
    List<Tick> byPriority = new ArrayList<>(ticks);
    byPriority.sort(Comparator.comparingInt(Tick::priority));
    g.setStroke(new BasicStroke(1f));
    for (Tick tick : byPriority) {
      double tickX = x + x(tick.value(), width);
      if (tick.value() % 1 == 0) {
        double length = style.px(tick.boundary() ? 5 : 3);
        g.setColor(StrengthStyle.alpha(style.tertiaryText, tick.boundary() ? 200 : 150));
        g.draw(new Line2D.Double(tickX, tickTop, tickX, tickTop + length));
      }
      if (tick.label() == null) {
        continue;
      }
      int labelWidth = metrics.stringWidth(tick.label());
      int left = (int) Math.round(tickX - labelWidth / 2.0);
      left = Math.max(x, Math.min(x + width - labelWidth, left));
      Rectangle bounds =
          new Rectangle(
              left - style.px(3),
              baseline - metrics.getAscent(),
              labelWidth + style.px(6),
              metrics.getHeight());
      boolean free = true;
      for (Rectangle other : placed) {
        if (other.intersects(bounds)) {
          free = false;
          break;
        }
      }
      if (!free) {
        continue;
      }
      placed.add(bounds);
      g.setColor(style.tertiaryText);
      g.drawString(tick.label(), left, baseline);
    }
  }

  private void paintMarkers(Graphics2D g, int x, int top, int width, List<Marker> markers) {
    double size = markerSize();
    double[] centers = new double[markers.size()];
    for (int i = 0; i < markers.size(); i++) {
      double value = markers.get(i).rankValue();
      centers[i] = Double.isFinite(value) ? x + x(value, width) : Double.NaN;
    }
    if (centers.length == 2 && Double.isFinite(centers[0]) && Double.isFinite(centers[1])) {
      double apart = size * 0.85;
      double distance = Math.abs(centers[0] - centers[1]);
      if (distance < apart) {
        double middle = (centers[0] + centers[1]) / 2;
        boolean firstLeft = centers[0] <= centers[1];
        centers[0] = middle + (firstLeft ? -apart / 2 : apart / 2);
        centers[1] = middle + (firstLeft ? apart / 2 : -apart / 2);
      }
    }
    for (int i = 0; i < markers.size(); i++) {
      if (!Double.isFinite(centers[i])) {
        continue;
      }
      double left = Math.max(x, Math.min(x + width - size, centers[i] - size / 2.0));
      Boolean stone = markers.get(i).stone();
      if (stone == null) {
        Ellipse2D knob = new Ellipse2D.Double(left, top, size, size);
        g.setColor(style.surface);
        g.fill(knob);
        g.setColor(style.accent);
        g.setStroke(new BasicStroke(2f));
        g.draw(new Ellipse2D.Double(left + 1, top + 1, size - 2, size - 2));
      } else {
        double stoneSize = size + style.px(2);
        StoneGlyph.paint(g, left - style.px(1), top - style.px(1), stoneSize, stone);
      }
    }
  }
}
