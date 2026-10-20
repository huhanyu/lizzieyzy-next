package featurecat.lizzie.gui;

import featurecat.lizzie.analysis.PlayerStrengthEstimator;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ResourceBundle;
import javax.swing.JPanel;

/** One side's predicted rank, and where it sits on the scale from amateur to AI. */
final class StrengthRankCard extends JPanel {
  private static final long serialVersionUID = 1L;
  private static final double[] TIER_WEIGHTS = {0.46, 0.18, 0.18, 0.18};
  private static final double[][] TIER_RANGES = {{-18, 10}, {10, 11}, {11, 12}, {12, 13}};
  private final transient StrengthStyle style;
  private final transient ResourceBundle bundle;
  private final transient PlayerStrengthEstimator.SideReport report;
  private final String sideName;
  private final boolean black;

  StrengthRankCard(
      String sideName,
      boolean black,
      PlayerStrengthEstimator.SideReport report,
      ResourceBundle bundle,
      StrengthStyle style) {
    this.sideName = sideName;
    this.black = black;
    this.report = report;
    this.bundle = bundle;
    this.style = style;
    setOpaque(false);
    setToolTipText(StrengthText.scaleTooltip(report, bundle));
    getAccessibleContext().setAccessibleName(sideName + " " + StrengthText.rank(report, bundle));
    getAccessibleContext().setAccessibleDescription(StrengthText.scaleDescription(report, bundle));
  }

  PlayerStrengthEstimator.SideReport report() {
    return report;
  }

  private boolean hasRank() {
    return report != null && report.hasSamples() && Double.isFinite(report.rankValue);
  }

  /** The font of the rank number; its kerning keeps macOS from dropping the digits. */
  Font numberFont() {
    return style.font(Font.BOLD, 22);
  }

  private Font affixFont() {
    return style.font(Font.PLAIN, 6);
  }

  private Font captionFont(boolean bold) {
    return style.font(bold ? Font.BOLD : Font.PLAIN, -1);
  }

  private int padding() {
    return style.px(22);
  }

  private int headerHeight() {
    return Math.max(style.px(24), getFontMetrics(style.font(Font.BOLD, 3)).getHeight());
  }

  private int heroHeight() {
    FontMetrics metrics = getFontMetrics(numberFont());
    return metrics.getAscent() + metrics.getDescent();
  }

  private int markerSize() {
    return style.px(12);
  }

  @Override
  public Dimension getPreferredSize() {
    FontMetrics caption = getFontMetrics(captionFont(false));
    int height =
        padding()
            + headerHeight()
            + style.px(12)
            + heroHeight()
            + style.px(12)
            + markerSize()
            + style.px(8)
            + caption.getHeight() * 2
            + padding();
    return new Dimension(style.px(400), height);
  }

  @Override
  public Dimension getMinimumSize() {
    return new Dimension(style.px(320), getPreferredSize().height);
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = StrengthStyle.smooth(graphics);
    try {
      int width = getWidth();
      style.paintCard(g, width, getHeight());
      int pad = padding();
      int contentWidth = width - pad * 2;

      int header = headerHeight();
      double stone = style.px(22);
      StoneGlyph.paint(g, pad, pad + (header - stone) / 2.0, stone, black);
      Font headline = style.font(Font.BOLD, 3);
      FontMetrics headlineMetrics = g.getFontMetrics(headline);
      int headerBaseline =
          pad
              + (header - headlineMetrics.getAscent() - headlineMetrics.getDescent()) / 2
              + headlineMetrics.getAscent();
      int chipWidth = hasRank() ? paintConfidence(g, width - pad, pad, header) : 0;
      int nameX = pad + (int) stone + style.px(10);
      StrengthPaint.text(
          g,
          sideName,
          headline,
          headline.getSize() - 3,
          style.text,
          nameX,
          headerBaseline,
          width - pad - chipWidth - style.px(12) - nameX,
          StrengthPaint.LEFT);

      int heroTop = pad + header + style.px(12);
      paintRank(g, pad, heroTop, contentWidth);

      int scaleTop = heroTop + heroHeight() + style.px(12);
      paintScale(g, pad, scaleTop, contentWidth);
    } finally {
      g.dispose();
    }
  }

  /** Paints the confidence chip ending at {@code right}; returns its width. */
  private int paintConfidence(Graphics2D g, int right, int top, int rowHeight) {
    String text =
        bundle.getString("PlayerStrengthEstimate.confidence")
            + " "
            + StrengthText.confidence(report.confidence, bundle);
    Font font = captionFont(false);
    FontMetrics metrics = g.getFontMetrics(font);
    int dot = style.px(6);
    int height = style.px(22);
    int width = style.px(9) + dot + style.px(6) + metrics.stringWidth(text) + style.px(10);
    double x = right - width;
    double y = top + (rowHeight - height) / 2.0;
    g.setColor(style.sunken);
    g.fill(new RoundRectangle2D.Double(x, y, width, height, height, height));
    Color tone =
        report.confidence == PlayerStrengthEstimator.Confidence.HIGH
            ? style.accent
            : report.confidence == PlayerStrengthEstimator.Confidence.MEDIUM
                ? style.caution
                : style.negative;
    StrengthPaint.dot(g, tone, x + style.px(9) + dot / 2.0, y + height / 2.0, dot);
    g.setFont(font);
    g.setColor(style.secondaryText);
    g.drawString(
        text,
        (float) (x + style.px(9) + dot + style.px(6)),
        (float) (y + (height - metrics.getAscent() - metrics.getDescent()) / 2.0)
            + metrics.getAscent());
    return width;
  }

  private void paintRank(Graphics2D g, int x, int top, int maxWidth) {
    String[] parts = StrengthText.splitNumber(StrengthText.rank(report, bundle));
    if (!hasRank()) {
      parts = new String[] {"", "\u2014", ""};
    }
    Font number = numberFont();
    Font affix = affixFont();
    int gap = style.px(5);
    while (number.getSize() > style.font(Font.BOLD, 8).getSize()
        && rankWidth(g, parts, number, affix, gap) > maxWidth) {
      number = number.deriveFont((float) number.getSize() - 1);
      affix = affix.deriveFont((float) Math.max(10, affix.getSize() - 0.5f));
    }
    int baseline = top + g.getFontMetrics(numberFont()).getAscent();
    float cursor = x;
    if (!parts[0].isEmpty()) {
      g.setFont(affix);
      g.setColor(style.secondaryText);
      g.drawString(parts[0], cursor, baseline);
      cursor += g.getFontMetrics(affix).stringWidth(parts[0]) + gap;
    }
    g.setFont(number);
    g.setColor(hasRank() ? style.text : style.tertiaryText);
    g.drawString(parts[1], cursor, baseline);
    cursor += g.getFontMetrics(number).stringWidth(parts[1]) + gap;
    if (!parts[2].isEmpty()) {
      StrengthPaint.text(
          g,
          parts[2],
          affix,
          affix.getSize(),
          style.secondaryText,
          cursor,
          baseline,
          (int) (x + maxWidth - cursor),
          StrengthPaint.LEFT);
    }
  }

  private static int rankWidth(Graphics2D g, String[] parts, Font number, Font affix, int gap) {
    FontMetrics affixMetrics = g.getFontMetrics(affix);
    int width = g.getFontMetrics(number).stringWidth(parts[1]);
    if (!parts[0].isEmpty()) {
      width += affixMetrics.stringWidth(parts[0]) + gap;
    }
    if (!parts[2].isEmpty()) {
      width += affixMetrics.stringWidth(parts[2]) + gap;
    }
    return width;
  }

  private void paintScale(Graphics2D g, int x, int top, int width) {
    String[][] scale = StrengthText.scale(bundle);
    int active = hasRank() ? StrengthText.tier(report.rankValue) : -1;
    double gap = style.px(3);
    double usable = width - gap * (scale.length - 1);
    double trackHeight = style.px(6);
    double marker = markerSize();
    double trackY = top + (marker - trackHeight) / 2.0;
    double markerX = Double.NaN;
    double segmentX = x;
    Font plain = captionFont(false);
    Font bold = captionFont(true);
    FontMetrics captionMetrics = g.getFontMetrics(plain);
    int nameBaseline = (int) (top + marker + style.px(8)) + captionMetrics.getAscent();
    for (int i = 0; i < scale.length; i++) {
      double segmentWidth = usable * TIER_WEIGHTS[i];
      g.setColor(style.sunken);
      g.fill(
          new RoundRectangle2D.Double(
              segmentX, trackY, segmentWidth, trackHeight, trackHeight, trackHeight));
      double filled = 0;
      if (i < active) {
        filled = segmentWidth;
      } else if (i == active) {
        double[] range = TIER_RANGES[i];
        double position =
            StrengthStats.clamp((report.rankValue - range[0]) / (range[1] - range[0]));
        filled = Math.max(trackHeight, segmentWidth * position);
        markerX = segmentX + filled;
      }
      if (filled > 0) {
        g.setColor(i == active ? style.accent : StrengthStyle.alpha(style.accent, 120));
        g.fill(
            new RoundRectangle2D.Double(
                segmentX, trackY, filled, trackHeight, trackHeight, trackHeight));
      }
      StrengthPaint.text(
          g,
          scale[i][1],
          i == active ? bold : plain,
          style.font(Font.PLAIN, -3).getSize(),
          i == active ? style.accent : style.secondaryText,
          (float) segmentX,
          nameBaseline,
          (int) segmentWidth - style.px(4),
          StrengthPaint.LEFT);
      StrengthPaint.text(
          g,
          scale[i][0],
          plain,
          style.font(Font.PLAIN, -3).getSize(),
          style.tertiaryText,
          (float) segmentX,
          nameBaseline + captionMetrics.getHeight(),
          (int) segmentWidth - style.px(4),
          StrengthPaint.LEFT);
      segmentX += segmentWidth + gap;
    }
    if (!Double.isNaN(markerX)) {
      double left = Math.max(x, Math.min(x + width - marker, markerX - marker / 2.0));
      Ellipse2D knob = new Ellipse2D.Double(left, top, marker, marker);
      g.setColor(style.surface);
      g.fill(knob);
      g.setColor(style.accent);
      g.setStroke(new BasicStroke(2f));
      g.draw(new Ellipse2D.Double(left + 1, top + 1, marker - 2, marker - 2));
    }
  }
}
