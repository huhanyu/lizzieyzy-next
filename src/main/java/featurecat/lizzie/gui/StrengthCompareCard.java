package featurecat.lizzie.gui;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JToolTip;

/**
 * Black's and White's figures in pairs, Black on the left and White on the right, each pair over a
 * bar split by their shares. The better figure of a pair is set in bold.
 */
final class StrengthCompareCard extends JPanel {
  private static final long serialVersionUID = 1L;

  /**
   * One compared figure with a sentence that explains it; {@code better} is 1 when more is better,
   * -1 when less is, else 0.
   */
  record Row(
      String label,
      String help,
      String blackText,
      String whiteText,
      double blackValue,
      double whiteValue,
      int better) {

    /** -1 when Black's figure is the better one, 1 when White's is, 0 when neither is. */
    int leader() {
      if (better == 0
          || !Double.isFinite(blackValue)
          || !Double.isFinite(whiteValue)
          || Math.abs(blackValue - whiteValue) < 1e-9) {
        return 0;
      }
      return (blackValue > whiteValue) == (better > 0) ? -1 : 1;
    }
  }

  private final transient StrengthStyle style;
  private final transient List<Row> rows;

  StrengthCompareCard(List<Row> rows, StrengthStyle style) {
    this.rows = List.copyOf(rows);
    this.style = style;
    setOpaque(false);
    setToolTipText("");
    StringBuilder description = new StringBuilder();
    for (Row row : rows) {
      if (description.length() > 0) {
        description.append("; ");
      }
      description
          .append(row.label())
          .append(": ")
          .append(row.blackText())
          .append(" / ")
          .append(row.whiteText());
    }
    getAccessibleContext().setAccessibleDescription(description.toString());
  }

  List<Row> rows() {
    return rows;
  }

  /** The explanation of the row under the pointer. */
  @Override
  public String getToolTipText(MouseEvent event) {
    int index = rowAt(event.getY());
    if (index < 0 || rows.get(index).help() == null) {
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

  private int rowHeight() {
    return Math.max(
        minimumRowHeight(),
        Math.min(minimumRowHeight() + style.px(22), (getHeight() - padding() * 2) / rows.size()));
  }

  private int rowsTop() {
    return (getHeight() - rowHeight() * rows.size()) / 2;
  }

  /** The row at height {@code y}, or -1. */
  int rowAt(int y) {
    if (rows.isEmpty()) {
      return -1;
    }
    int index = Math.floorDiv(y - rowsTop(), rowHeight());
    return index >= 0 && index < rows.size() ? index : -1;
  }

  /** The vertical middle of row {@code index}. */
  int rowCenter(int index) {
    return rowsTop() + rowHeight() * index + rowHeight() / 2;
  }

  private Font valueFont(boolean bold) {
    return style.font(bold ? Font.BOLD : Font.PLAIN, 3);
  }

  private int padding() {
    return style.px(22);
  }

  private int minimumRowHeight() {
    return getFontMetrics(valueFont(true)).getHeight() + style.px(6) + style.px(6) + style.px(8);
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(
        style.px(600), padding() * 2 + rows.size() * (minimumRowHeight() + style.px(6)));
  }

  @Override
  public Dimension getMinimumSize() {
    return new Dimension(style.px(360), padding() * 2 + rows.size() * minimumRowHeight());
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = StrengthStyle.smooth(graphics);
    try {
      int width = getWidth();
      int height = getHeight();
      style.paintCard(g, width, height);
      if (rows.isEmpty()) {
        return;
      }
      int pad = padding();
      int rowHeight = rowHeight();
      int top = rowsTop();
      for (Row row : rows) {
        paintRow(g, row, pad, top, width - pad * 2, rowHeight);
        top += rowHeight;
      }
    } finally {
      g.dispose();
    }
  }

  private void paintRow(Graphics2D g, Row row, int x, int top, int width, int rowHeight) {
    FontMetrics valueMetrics = g.getFontMetrics(valueFont(true));
    double barHeight = style.px(6);
    int block = valueMetrics.getHeight() + style.px(6) + (int) barHeight;
    int blockTop = top + (rowHeight - block) / 2;
    int baseline = blockTop + valueMetrics.getAscent();
    int leader = row.leader();
    int valueWidth = Math.max(style.px(64), width / 4);
    int mark = row.help() == null ? 0 : style.px(13);
    int markGap = mark == 0 ? 0 : style.px(6);
    Font label =
        StrengthPaint.fit(
            g,
            row.label(),
            style.font(Font.PLAIN, 1),
            style.font(Font.PLAIN, -1).getSize(),
            width - valueWidth * 2 - mark - markGap);
    FontMetrics labelMetrics = g.getFontMetrics(label);
    String labelText =
        StrengthPaint.ellipsize(row.label(), labelMetrics, width - valueWidth * 2 - mark - markGap);
    int group = labelMetrics.stringWidth(labelText) + markGap + mark;
    float labelX = x + (width - group) / 2f;
    g.setFont(label);
    g.setColor(style.secondaryText);
    g.drawString(labelText, labelX, baseline);
    if (mark > 0) {
      StrengthPaint.infoMark(
          g,
          style.tertiaryText,
          labelX + labelMetrics.stringWidth(labelText) + markGap,
          baseline - labelMetrics.getAscent() / 2.0 - mark / 2.0 + labelMetrics.getDescent() / 2.0,
          mark);
    }
    StrengthPaint.text(
        g,
        row.blackText(),
        valueFont(leader < 0),
        valueFont(false).getSize() - 3,
        leader > 0 ? style.secondaryText : style.text,
        x,
        baseline,
        valueWidth,
        StrengthPaint.LEFT);
    StrengthPaint.text(
        g,
        row.whiteText(),
        valueFont(leader > 0),
        valueFont(false).getSize() - 3,
        leader < 0 ? style.secondaryText : style.text,
        x + width,
        baseline,
        valueWidth,
        StrengthPaint.RIGHT);

    double barY = blockTop + valueMetrics.getHeight() + style.px(6);
    double black = Math.max(0, row.blackValue());
    double white = Math.max(0, row.whiteValue());
    double total = black + white;
    if (!Double.isFinite(total) || total <= 0) {
      g.setColor(style.sunken);
      g.fill(new RoundRectangle2D.Double(x, barY, width, barHeight, barHeight, barHeight));
      return;
    }
    double gap = style.px(3);
    double blackWidth = Math.max(barHeight, (width - gap) * black / total);
    double whiteWidth = Math.max(barHeight, width - gap - blackWidth);
    blackWidth = width - gap - whiteWidth;
    g.setColor(style.blackShare);
    g.fill(new RoundRectangle2D.Double(x, barY, blackWidth, barHeight, barHeight, barHeight));
    g.setColor(style.whiteShare);
    g.fill(
        new RoundRectangle2D.Double(
            x + blackWidth + gap, barY, whiteWidth, barHeight, barHeight, barHeight));
  }
}
