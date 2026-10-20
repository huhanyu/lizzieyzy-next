package featurecat.lizzie.gui;

import featurecat.lizzie.analysis.MoveRankDefinition;
import featurecat.lizzie.analysis.PlayerStrengthEstimator;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.swing.JPanel;

/**
 * Black's and White's moves per quality tier, mirrored around the tier names: Black's bars grow to
 * the left, White's to the right. Above them, one strip per side shows the whole mix at a glance.
 */
final class StrengthDistributionCard extends JPanel {
  private static final long serialVersionUID = 1L;
  private static final MoveRankDefinition.Rank[] TIERS = MoveRankDefinition.Rank.values();
  private final transient StrengthStyle style;
  private final transient ResourceBundle bundle;
  private final String blackName;
  private final String whiteName;
  private final String title;
  private final String hint;
  private final int[][] counts;
  private final int[] totals;

  StrengthDistributionCard(
      PlayerStrengthEstimator.Report report,
      String blackName,
      String whiteName,
      ResourceBundle bundle,
      StrengthStyle style) {
    this.style = style;
    this.bundle = bundle;
    this.blackName = blackName;
    this.whiteName = whiteName;
    title = bundle.getString("PlayerStrengthEstimate.performance.title");
    hint = bundle.getString("PlayerStrengthEstimate.performance.hint");
    counts =
        new int[][] {
          StrengthStats.tierCounts(report.black), StrengthStats.tierCounts(report.white)
        };
    totals =
        new int[] {
          report.black == null ? 0 : report.black.sampleCount,
          report.white == null ? 0 : report.white.sampleCount
        };
    setOpaque(false);
    getAccessibleContext().setAccessibleName(title);
    getAccessibleContext().setAccessibleDescription(describe());
  }

  /** Moves per tier of one side, indexed by {@link MoveRankDefinition.Rank#ordinal()}. */
  int[] counts(boolean black) {
    return counts[black ? 0 : 1].clone();
  }

  private String describe() {
    StringBuilder text = new StringBuilder();
    for (int side = 0; side < 2; side++) {
      if (side > 0) {
        text.append(". ");
      }
      text.append(side == 0 ? blackName : whiteName);
      for (MoveRankDefinition.Rank tier : TIERS) {
        text.append(", ")
            .append(bundle.getString(tier.nameKey()))
            .append(' ')
            .append(counts[side][tier.ordinal()]);
      }
    }
    return text.toString();
  }

  private boolean hasMoves() {
    return totals[0] > 0 || totals[1] > 0;
  }

  private int padding() {
    return style.px(22);
  }

  private Font titleFont() {
    return style.font(Font.BOLD, 3);
  }

  private Font smallFont() {
    return style.font(Font.PLAIN, -1);
  }

  private Font bodyFont(boolean bold) {
    return style.font(bold ? Font.BOLD : Font.PLAIN, 1);
  }

  private List<String> hintLines(int width) {
    return StrengthPaint.wrap(
        hint, getFontMetrics(smallFont()), Math.max(80, width - padding() * 2), 2);
  }

  private int headerHeight(int width) {
    return getFontMetrics(titleFont()).getHeight()
        + style.px(4)
        + hintLines(width).size() * getFontMetrics(smallFont()).getHeight();
  }

  private int sideRowHeight() {
    return Math.max(style.px(26), getFontMetrics(titleFont()).getHeight());
  }

  private int minimumRowHeight() {
    return Math.max(style.px(30), getFontMetrics(bodyFont(true)).getHeight() + style.px(10));
  }

  @Override
  public Dimension getPreferredSize() {
    int width = style.px(760);
    return new Dimension(
        width,
        padding() * 2
            + headerHeight(width)
            + style.px(18)
            + sideRowHeight()
            + style.px(10)
            + style.px(8)
            + style.px(22)
            + TIERS.length * (minimumRowHeight() + style.px(8)));
  }

  @Override
  public Dimension getMinimumSize() {
    int width = style.px(560);
    return new Dimension(
        width,
        padding() * 2
            + headerHeight(width)
            + style.px(18)
            + sideRowHeight()
            + style.px(32)
            + TIERS.length * minimumRowHeight());
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = StrengthStyle.smooth(graphics);
    try {
      int width = getWidth();
      int height = getHeight();
      int pad = padding();
      style.paintCard(g, width, height);

      Font titleFont = titleFont();
      FontMetrics titleMetrics = g.getFontMetrics(titleFont);
      StrengthPaint.text(
          g,
          title,
          titleFont,
          titleFont.getSize() - 3,
          style.text,
          pad,
          pad + titleMetrics.getAscent(),
          width - pad * 2,
          StrengthPaint.LEFT);
      Font small = smallFont();
      FontMetrics smallMetrics = g.getFontMetrics(small);
      int baseline = pad + titleMetrics.getHeight() + style.px(4) + smallMetrics.getAscent();
      g.setFont(small);
      g.setColor(style.secondaryText);
      for (String line : hintLines(width)) {
        g.drawString(line, pad, baseline);
        baseline += smallMetrics.getHeight();
      }

      int top = pad + headerHeight(width) + style.px(18);
      int available = height - pad - top;
      int fixed = sideRowHeight() + style.px(10) + style.px(8) + style.px(22);
      int rowHeight =
          Math.max(minimumRowHeight(), Math.min(style.px(52), (available - fixed) / TIERS.length));
      top += Math.max(0, (available - fixed - rowHeight * TIERS.length) / 2);
      if (!hasMoves()) {
        StrengthPaint.text(
            g,
            bundle.getString("PlayerStrengthEstimate.performance.noMoveSamples"),
            bodyFont(false),
            style.font(Font.PLAIN, -2).getSize(),
            style.tertiaryText,
            width / 2f,
            top + (height - top - pad) / 2f,
            width - pad * 2,
            StrengthPaint.CENTER);
        return;
      }
      int center = width / 2;
      paintSideHeader(g, true, pad, top, center - style.px(12) - pad);
      paintSideHeader(g, false, center + style.px(12), top, width - pad - center - style.px(12));
      int stripTop = top + sideRowHeight() + style.px(10);
      paintStrip(g, 0, pad, stripTop, center - style.px(12) - pad);
      paintStrip(g, 1, center + style.px(12), stripTop, width - pad - center - style.px(12));

      paintTable(g, pad, width - pad, stripTop + style.px(8) + style.px(22), rowHeight);
    } finally {
      g.dispose();
    }
  }

  private void paintSideHeader(Graphics2D g, boolean black, int x, int top, int width) {
    int row = sideRowHeight();
    double stone = style.px(20);
    Font name = titleFont();
    FontMetrics nameMetrics = g.getFontMetrics(name);
    Font chipFont = style.font(Font.PLAIN, -1);
    FontMetrics chipMetrics = g.getFontMetrics(chipFont);
    String chip =
        String.format(
            Locale.US,
            bundle.getString("PlayerStrengthEstimate.performance.sideMoves"),
            totals[black ? 0 : 1]);
    int chipHeight = style.px(22);
    int chipWidth = chipMetrics.stringWidth(chip) + style.px(18);
    String sideName = black ? blackName : whiteName;
    int nameWidth = Math.min(nameMetrics.stringWidth(sideName), width / 2);
    int baseline =
        top
            + (row - nameMetrics.getAscent() - nameMetrics.getDescent()) / 2
            + nameMetrics.getAscent();
    double stoneY = top + (row - stone) / 2.0;
    double chipY = top + (row - chipHeight) / 2.0;
    double stoneX = black ? x : x + width - stone;
    double nameX = black ? x + stone + style.px(10) : x + width - stone - style.px(10);
    double chipX =
        black ? nameX + nameWidth + style.px(10) : nameX - nameWidth - style.px(10) - chipWidth;
    StoneGlyph.paint(g, stoneX, stoneY, stone, black);
    StrengthPaint.text(
        g,
        sideName,
        name,
        name.getSize() - 3,
        style.text,
        (float) nameX,
        baseline,
        nameWidth,
        black ? StrengthPaint.LEFT : StrengthPaint.RIGHT);
    g.setColor(style.sunken);
    g.fill(
        new RoundRectangle2D.Double(chipX, chipY, chipWidth, chipHeight, chipHeight, chipHeight));
    g.setFont(chipFont);
    g.setColor(style.secondaryText);
    g.drawString(
        chip,
        (float) (chipX + style.px(9)),
        (float) (chipY + (chipHeight - chipMetrics.getAscent() - chipMetrics.getDescent()) / 2.0)
            + chipMetrics.getAscent());
  }

  private void paintStrip(Graphics2D g, int side, int x, int top, int width) {
    double height = style.px(8);
    Shape clip = g.getClip();
    RoundRectangle2D strip = new RoundRectangle2D.Double(x, top, width, height, height, height);
    g.setColor(style.sunken);
    g.fill(strip);
    int total = totals[side];
    if (total <= 0) {
      return;
    }
    g.clip(strip);
    double gap = style.px(2);
    double cursor = x;
    for (MoveRankDefinition.Rank tier : TIERS) {
      int count = counts[side][tier.ordinal()];
      if (count <= 0) {
        continue;
      }
      double segment = width * count / (double) total;
      g.setColor(style.rankColor(tier));
      g.fill(new Rectangle2D.Double(cursor, top, Math.max(1, segment - gap), height));
      cursor += segment;
    }
    g.setClip(clip);
  }

  private void paintTable(Graphics2D g, int left, int right, int top, int rowHeight) {
    Font name = bodyFont(false);
    FontMetrics nameMetrics = g.getFontMetrics(name);
    Font count = bodyFont(true);
    FontMetrics countMetrics = g.getFontMetrics(count);
    Font percent = smallFont();
    FontMetrics percentMetrics = g.getFontMetrics(percent);
    double dot = style.px(8);
    int labelWidth = 0;
    for (MoveRankDefinition.Rank tier : TIERS) {
      labelWidth = Math.max(labelWidth, nameMetrics.stringWidth(bundle.getString(tier.nameKey())));
    }
    int column = Math.max(style.px(110), labelWidth + (int) dot + style.px(8) + style.px(24));
    int center = (left + right) / 2;
    int countWidth = countMetrics.stringWidth("888") + style.px(4);
    int percentWidth = percentMetrics.stringWidth("100.0%") + style.px(4);
    int numbers = percentWidth + style.px(10) + countWidth + style.px(12);
    int blackBarRight = center - column / 2;
    int blackBarLeft = left + numbers;
    int whiteBarLeft = center + column / 2;
    int whiteBarRight = right - numbers;
    double maxFraction = 0;
    for (int side = 0; side < 2; side++) {
      for (MoveRankDefinition.Rank tier : TIERS) {
        maxFraction = Math.max(maxFraction, fraction(side, tier));
      }
    }
    double barHeight = style.px(10);
    for (int i = 0; i < TIERS.length; i++) {
      MoveRankDefinition.Rank tier = TIERS[i];
      int rowTop = top + i * rowHeight;
      double barY = rowTop + (rowHeight - barHeight) / 2.0;
      int baseline =
          rowTop
              + (rowHeight - countMetrics.getAscent() - countMetrics.getDescent()) / 2
              + countMetrics.getAscent();
      String label = bundle.getString(tier.nameKey());
      int groupWidth = (int) dot + style.px(8) + Math.min(labelWidth, column - style.px(24));
      double dotX = center - groupWidth / 2.0;
      StrengthPaint.dot(g, style.rankColor(tier), dotX + dot / 2, rowTop + rowHeight / 2.0, dot);
      StrengthPaint.text(
          g,
          label,
          name,
          name.getSize() - 3,
          style.text,
          (float) (dotX + dot + style.px(8)),
          baseline,
          column - style.px(24) - (int) dot - style.px(8),
          StrengthPaint.LEFT);
      for (int side = 0; side < 2; side++) {
        boolean black = side == 0;
        int barLeft = black ? blackBarLeft : whiteBarLeft;
        int barRight = black ? blackBarRight : whiteBarRight;
        double zone = Math.max(0, barRight - barLeft - style.px(8));
        g.setColor(style.divider);
        g.fill(
            new RoundRectangle2D.Double(
                black ? barLeft + style.px(8) : barLeft,
                barY,
                zone,
                barHeight,
                barHeight,
                barHeight));
        double share = maxFraction <= 0 ? 0 : fraction(side, tier) / maxFraction;
        if (share > 0) {
          double length = Math.max(barHeight, zone * share);
          g.setColor(style.rankColor(tier));
          g.fill(
              new RoundRectangle2D.Double(
                  black ? barRight - length : barLeft,
                  barY,
                  length,
                  barHeight,
                  barHeight,
                  barHeight));
        }
        String countText = String.valueOf(counts[side][tier.ordinal()]);
        String percentText = StrengthText.precisePercent(fraction(side, tier));
        if (black) {
          StrengthPaint.text(
              g,
              countText,
              count,
              count.getSize(),
              style.text,
              blackBarLeft - style.px(12),
              baseline,
              countWidth,
              StrengthPaint.RIGHT);
          StrengthPaint.text(
              g,
              percentText,
              percent,
              percent.getSize() - 2,
              style.secondaryText,
              left + percentWidth,
              baseline,
              percentWidth,
              StrengthPaint.RIGHT);
        } else {
          StrengthPaint.text(
              g,
              countText,
              count,
              count.getSize(),
              style.text,
              whiteBarRight + style.px(12),
              baseline,
              countWidth,
              StrengthPaint.LEFT);
          StrengthPaint.text(
              g,
              percentText,
              percent,
              percent.getSize() - 2,
              style.secondaryText,
              right - percentWidth,
              baseline,
              percentWidth,
              StrengthPaint.LEFT);
        }
      }
    }
  }

  private double fraction(int side, MoveRankDefinition.Rank tier) {
    return totals[side] <= 0 ? 0 : counts[side][tier.ordinal()] / (double) totals[side];
  }
}
