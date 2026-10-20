package featurecat.lizzie.gui;

import featurecat.lizzie.analysis.PlayerStrengthEstimator;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Function;
import javax.swing.JPanel;

/**
 * Every figure of the estimate, one row per figure with Black's and White's values side by side.
 */
final class StrengthDetailTable extends JPanel {
  private static final long serialVersionUID = 1L;
  private final transient StrengthStyle style;
  private final String blackName;
  private final String whiteName;
  private final List<String[]> rows = new ArrayList<>();

  StrengthDetailTable(
      PlayerStrengthEstimator.Report report,
      String blackName,
      String whiteName,
      ResourceBundle bundle,
      StrengthStyle style) {
    this.style = style;
    this.blackName = blackName;
    this.whiteName = whiteName;
    add(
        report,
        bundle,
        "PlayerStrengthEstimate.match.strength",
        side -> StrengthText.band(side.strengthBand, bundle));
    add(
        report,
        bundle,
        "PlayerStrengthEstimate.rank.scale",
        side -> StrengthText.rank(side, bundle));
    add(
        report,
        bundle,
        "PlayerStrengthEstimate.confidence",
        side -> StrengthText.confidence(side.confidence, bundle));
    add(report, bundle, "PlayerStrengthEstimate.moves", side -> String.valueOf(side.sampleCount));
    add(
        report,
        bundle,
        "PlayerStrengthEstimate.firstChoice",
        side -> StrengthText.precisePercent(side.firstChoiceRate));
    add(
        report,
        bundle,
        "PlayerStrengthEstimate.goodMoveRate",
        side -> StrengthText.precisePercent(side.moveRankGoodMoveRate));
    add(
        report,
        bundle,
        "PlayerStrengthEstimate.weightedScoreLoss",
        PlayerStrengthEstimator.SideReport::weightedScoreLossText);
    add(
        report,
        bundle,
        "PlayerStrengthEstimate.match.avgScoreLoss",
        PlayerStrengthEstimator.SideReport::averageScoreLossText);
    add(
        report,
        bundle,
        "PlayerStrengthEstimate.mistakeRate",
        side -> StrengthText.precisePercent(side.mistakeRate));
    add(
        report,
        bundle,
        "PlayerStrengthEstimate.match.overallMatch",
        PlayerStrengthEstimator.SideReport::matchRateText);
    if (report.model.isHumanSl40()) {
      add(
          report,
          bundle,
          "HumanSL40.difficulty",
          PlayerStrengthEstimator.SideReport::difficultyText);
      add(
          report,
          bundle,
          "HumanSL40.coverage",
          PlayerStrengthEstimator.SideReport::humanSlCoverageText);
    }
    setOpaque(false);
    StringBuilder description = new StringBuilder();
    for (String[] row : rows) {
      description
          .append(row[0])
          .append(": ")
          .append(row[1])
          .append(" / ")
          .append(row[2])
          .append("; ");
    }
    getAccessibleContext().setAccessibleDescription(description.toString().trim());
  }

  private void add(
      PlayerStrengthEstimator.Report report,
      ResourceBundle bundle,
      String key,
      Function<PlayerStrengthEstimator.SideReport, String> value) {
    rows.add(
        new String[] {bundle.getString(key), text(report.black, value), text(report.white, value)});
  }

  private static String text(
      PlayerStrengthEstimator.SideReport side,
      Function<PlayerStrengthEstimator.SideReport, String> value) {
    if (side == null) {
      return "-";
    }
    String text = value.apply(side);
    return text == null || text.isBlank() ? "-" : text;
  }

  /** The label, Black's value and White's value of each row. */
  List<String[]> rows() {
    return rows;
  }

  private int padding() {
    return style.px(22);
  }

  private int rowHeight() {
    return Math.max(
        style.px(34), getFontMetrics(style.font(Font.BOLD, 1)).getHeight() + style.px(14));
  }

  private int headerHeight() {
    return Math.max(
        style.px(40), getFontMetrics(style.font(Font.BOLD, 3)).getHeight() + style.px(16));
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(style.px(720), padding() * 2 + headerHeight() + rows.size() * rowHeight());
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = StrengthStyle.smooth(graphics);
    try {
      int width = getWidth();
      int pad = padding();
      style.paintCard(g, width, getHeight());
      int labelWidth = (width - pad * 2) * 2 / 5;
      int valueWidth = (width - pad * 2 - labelWidth) / 2;
      int blackCenter = pad + labelWidth + valueWidth / 2;
      int whiteCenter = pad + labelWidth + valueWidth + valueWidth / 2;

      Font headline = style.font(Font.BOLD, 3);
      FontMetrics headlineMetrics = g.getFontMetrics(headline);
      int header = headerHeight();
      int headerBaseline =
          pad
              + (header - headlineMetrics.getAscent() - headlineMetrics.getDescent()) / 2
              + headlineMetrics.getAscent();
      paintSideHeading(g, blackName, true, blackCenter, pad, header, valueWidth, headerBaseline);
      paintSideHeading(g, whiteName, false, whiteCenter, pad, header, valueWidth, headerBaseline);

      Font label = style.font(Font.PLAIN, 1);
      Font value = style.font(Font.BOLD, 1);
      FontMetrics valueMetrics = g.getFontMetrics(value);
      int top = pad + header;
      for (String[] row : rows) {
        g.setColor(style.divider);
        g.draw(new Line2D.Double(pad, top + 0.5, width - pad, top + 0.5));
        int baseline =
            top
                + (rowHeight() - valueMetrics.getAscent() - valueMetrics.getDescent()) / 2
                + valueMetrics.getAscent();
        StrengthPaint.text(
            g,
            row[0],
            label,
            label.getSize() - 3,
            style.secondaryText,
            pad,
            baseline,
            labelWidth - style.px(12),
            StrengthPaint.LEFT);
        StrengthPaint.text(
            g,
            row[1],
            value,
            value.getSize() - 3,
            style.text,
            blackCenter,
            baseline,
            valueWidth - style.px(12),
            StrengthPaint.CENTER);
        StrengthPaint.text(
            g,
            row[2],
            value,
            value.getSize() - 3,
            style.text,
            whiteCenter,
            baseline,
            valueWidth - style.px(12),
            StrengthPaint.CENTER);
        top += rowHeight();
      }
    } finally {
      g.dispose();
    }
  }

  private void paintSideHeading(
      Graphics2D g,
      String name,
      boolean black,
      int center,
      int top,
      int height,
      int width,
      int baseline) {
    Font font = style.font(Font.BOLD, 3);
    FontMetrics metrics = g.getFontMetrics(font);
    double stone = style.px(18);
    int textWidth = Math.min(metrics.stringWidth(name), width - (int) stone - style.px(20));
    double left = center - (stone + style.px(8) + textWidth) / 2.0;
    StoneGlyph.paint(g, left, top + (height - stone) / 2.0, stone, black);
    StrengthPaint.text(
        g,
        name,
        font,
        font.getSize() - 3,
        style.text,
        (float) (left + stone + style.px(8)),
        baseline,
        textWidth,
        StrengthPaint.LEFT);
  }
}
