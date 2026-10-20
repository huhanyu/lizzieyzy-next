package featurecat.lizzie.gui;

import featurecat.lizzie.analysis.PlayerStrengthEstimator;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Function;
import javax.swing.JPanel;
import javax.swing.JToolTip;

/** Every figure of each phase, one row per figure with Black's and White's value for each phase. */
final class StrengthPhaseTable extends JPanel {
  private static final long serialVersionUID = 1L;
  private final transient StrengthStyle style;
  private final transient List<StrengthPhases.Phase> phases;
  private final List<String> headings = new ArrayList<>();
  private final List<String> ranges = new ArrayList<>();

  /** A row: its label, its explanation (or null) and the values, two per phase. */
  record Row(String label, String help, List<String> values) {}

  private final List<Row> rows = new ArrayList<>();

  StrengthPhaseTable(
      List<StrengthPhases.Phase> phases, ResourceBundle bundle, StrengthStyle style) {
    this.style = style;
    this.phases = phases;
    for (StrengthPhases.Phase phase : phases) {
      headings.add(bundle.getString("PlayerStrengthEstimate.phase." + phase.name()));
      ranges.add(
          phase.played()
              ? MessageFormat.format(
                  bundle.getString("PlayerStrengthEstimate.phase.range"),
                  phase.first(),
                  phase.last())
              : bundle.getString("PlayerStrengthEstimate.phase.unplayed"));
    }
    String help = "PlayerStrengthEstimate.help.";
    add(
        bundle,
        "PlayerStrengthEstimate.detail.rank",
        help + "rank",
        true,
        side -> StrengthText.rank(side, bundle));
    add(
        bundle,
        "PlayerStrengthEstimate.confidence",
        help + "confidence",
        true,
        side -> StrengthText.confidence(side.confidence, bundle));
    add(
        bundle,
        "PlayerStrengthEstimate.moves",
        help + "moves",
        false,
        side -> String.valueOf(side.sampleCount));
    add(
        bundle,
        "PlayerStrengthEstimate.firstChoice",
        help + "firstChoice",
        false,
        side -> StrengthText.precisePercent(side.firstChoiceRate));
    add(
        bundle,
        "PlayerStrengthEstimate.goodMoveRate",
        help + "goodMoveRate",
        false,
        side -> StrengthText.precisePercent(side.moveRankGoodMoveRate));
    add(
        bundle,
        "PlayerStrengthEstimate.weightedScoreLoss",
        help + "weightedScoreLoss",
        false,
        PlayerStrengthEstimator.SideReport::weightedScoreLossText);
    add(
        bundle,
        "PlayerStrengthEstimate.match.avgScoreLoss",
        help + "avgScoreLoss",
        false,
        PlayerStrengthEstimator.SideReport::averageScoreLossText);
    add(
        bundle,
        "PlayerStrengthEstimate.mistakeRate",
        help + "mistakeRate",
        false,
        side -> StrengthText.precisePercent(side.mistakeRate));
    setOpaque(false);
    setToolTipText("");
    StringBuilder description = new StringBuilder();
    for (int i = 0; i < headings.size(); i++) {
      description.append(headings.get(i)).append(' ').append(ranges.get(i)).append(": ");
      for (Row row : rows) {
        description
            .append(row.label())
            .append(' ')
            .append(row.values().get(i * 2))
            .append(" / ")
            .append(row.values().get(i * 2 + 1))
            .append("; ");
      }
    }
    getAccessibleContext().setAccessibleDescription(description.toString().trim());
  }

  private void add(
      ResourceBundle bundle,
      String key,
      String helpKey,
      boolean needsRank,
      Function<PlayerStrengthEstimator.SideReport, String> value) {
    List<String> values = new ArrayList<>();
    for (StrengthPhases.Phase phase : phases) {
      for (boolean isBlack : new boolean[] {true, false}) {
        PlayerStrengthEstimator.SideReport side = phase.side(isBlack);
        boolean shown = needsRank ? phase.ranked(isBlack) : side != null && side.hasSamples();
        String text = shown ? value.apply(side) : null;
        values.add(text == null || text.isBlank() ? "\u2014" : text);
      }
    }
    rows.add(new Row(bundle.getString(key), bundle.getString(helpKey), values));
  }

  List<Row> rows() {
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
    FontMetrics heading = getFontMetrics(style.font(Font.BOLD, 2));
    FontMetrics range = getFontMetrics(style.font(Font.PLAIN, -1));
    return heading.getHeight() + range.getHeight() + style.px(8) + style.px(24);
  }

  private int labelWidth(int width) {
    return (width - padding() * 2) * 2 / 9;
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(style.px(900), padding() * 2 + headerHeight() + rows.size() * rowHeight());
  }

  @Override
  public String getToolTipText(MouseEvent event) {
    int index = Math.floorDiv(event.getY() - padding() - headerHeight(), rowHeight());
    if (index < 0 || index >= rows.size() || event.getX() > padding() + labelWidth(getWidth())) {
      return null;
    }
    return StrengthText.wrappedTooltip(
        rows.get(index).help(), getFontMetrics(style.font(Font.PLAIN, 0)), style.px(300));
  }

  @Override
  public JToolTip createToolTip() {
    JToolTip tip = super.createToolTip();
    tip.setFont(style.font(Font.PLAIN, 0));
    return tip;
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = StrengthStyle.smooth(graphics);
    try {
      int width = getWidth();
      int pad = padding();
      style.paintCard(g, width, getHeight());
      int labelWidth = labelWidth(width);
      int columns = phases.size() * 2;
      double column = (width - pad * 2 - labelWidth) / (double) columns;
      double left = pad + labelWidth;

      Font heading = style.font(Font.BOLD, 2);
      Font rangeFont = style.font(Font.PLAIN, -1);
      FontMetrics headingMetrics = g.getFontMetrics(heading);
      FontMetrics rangeMetrics = g.getFontMetrics(rangeFont);
      int headingBaseline = pad + headingMetrics.getAscent();
      int rangeBaseline = pad + headingMetrics.getHeight() + rangeMetrics.getAscent();
      double stone = style.px(14);
      double stoneTop = pad + headingMetrics.getHeight() + rangeMetrics.getHeight() + style.px(8);
      for (int i = 0; i < phases.size(); i++) {
        double phaseLeft = left + column * 2 * i;
        float center = (float) (phaseLeft + column);
        double dot = style.px(8);
        int headingWidth =
            Math.min(
                headingMetrics.stringWidth(headings.get(i)),
                (int) (column * 2 - dot - style.px(16)));
        double start = center - (dot + style.px(6) + headingWidth) / 2.0;
        StrengthPaint.dot(
            g,
            StrengthPhaseCard.tint(style, i),
            start + dot / 2,
            headingBaseline - headingMetrics.getAscent() / 2.0 + style.px(1),
            dot);
        StrengthPaint.text(
            g,
            headings.get(i),
            heading,
            heading.getSize() - 3,
            style.text,
            (float) (start + dot + style.px(6)),
            headingBaseline,
            headingWidth,
            StrengthPaint.LEFT);
        StrengthPaint.text(
            g,
            ranges.get(i),
            rangeFont,
            rangeFont.getSize() - 2,
            style.secondaryText,
            center,
            rangeBaseline,
            (int) (column * 2) - style.px(8),
            StrengthPaint.CENTER);
        StoneGlyph.paint(g, phaseLeft + column / 2 - stone / 2, stoneTop, stone, true);
        StoneGlyph.paint(g, phaseLeft + column * 1.5 - stone / 2, stoneTop, stone, false);
        if (i > 0) {
          g.setColor(style.divider);
          g.draw(new Line2D.Double(phaseLeft, pad, phaseLeft, getHeight() - pad));
        }
      }

      Font label = style.font(Font.PLAIN, 1);
      Font value = style.font(Font.BOLD, 1);
      FontMetrics valueMetrics = g.getFontMetrics(value);
      int top = pad + headerHeight();
      for (Row row : rows) {
        g.setColor(style.divider);
        g.draw(new Line2D.Double(pad, top + 0.5, width - pad, top + 0.5));
        int baseline =
            top
                + (rowHeight() - valueMetrics.getAscent() - valueMetrics.getDescent()) / 2
                + valueMetrics.getAscent();
        int labelDrawn =
            StrengthPaint.text(
                g,
                row.label(),
                label,
                label.getSize() - 3,
                style.secondaryText,
                pad,
                baseline,
                labelWidth - style.px(28),
                StrengthPaint.LEFT);
        int mark = style.px(13);
        StrengthPaint.infoMark(
            g,
            style.tertiaryText,
            pad + labelDrawn + style.px(6),
            top + (rowHeight() - mark) / 2.0,
            mark);
        for (int i = 0; i < columns; i++) {
          StrengthPaint.text(
              g,
              row.values().get(i),
              value,
              value.getSize() - 4,
              style.text,
              (float) (left + column * i + column / 2),
              baseline,
              (int) column - style.px(8),
              StrengthPaint.CENTER);
        }
        top += rowHeight();
      }
    } finally {
      g.dispose();
    }
  }
}
