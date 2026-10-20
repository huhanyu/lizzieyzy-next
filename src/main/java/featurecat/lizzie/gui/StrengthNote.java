package featurecat.lizzie.gui;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * A remark under a view, after an information mark or, when the figures above it are not ready yet,
 * a caution mark. It wraps onto a second line when the dialog is narrow.
 */
final class StrengthNote extends JPanel {
  private static final long serialVersionUID = 1L;
  private static final int MAX_LINES = 2;
  private final transient StrengthStyle style;
  private String text;
  private final boolean caution;

  StrengthNote(String text, boolean caution, StrengthStyle style) {
    this.style = style;
    this.text = text == null ? "" : text;
    this.caution = caution;
    setOpaque(false);
    setFont(style.font(Font.PLAIN, 0));
    getAccessibleContext().setAccessibleName(this.text);
  }

  String text() {
    return text;
  }

  void setText(String value) {
    String next = value == null ? "" : value;
    if (next.equals(text)) {
      return;
    }
    text = next;
    getAccessibleContext().setAccessibleName(next);
    revalidate();
    repaint();
  }

  boolean isCaution() {
    return caution;
  }

  private int markSize() {
    return style.px(15);
  }

  private int textX() {
    return markSize() + style.px(8);
  }

  private List<String> lines(int width) {
    FontMetrics metrics = getFontMetrics(getFont());
    return StrengthPaint.wrap(text, metrics, Math.max(40, width - textX()), MAX_LINES);
  }

  int heightFor(int width) {
    FontMetrics metrics = getFontMetrics(getFont());
    return Math.max(markSize(), lines(width).size() * metrics.getHeight());
  }

  @Override
  public Dimension getPreferredSize() {
    int width = getWidth() > 0 ? getWidth() : style.px(900);
    return new Dimension(width, heightFor(width));
  }

  @Override
  public void setBounds(int x, int y, int width, int height) {
    super.setBounds(x, y, width, height);
    if (width > 0 && heightFor(width) != height) {
      SwingUtilities.invokeLater(this::revalidate);
    }
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = StrengthStyle.smooth(graphics);
    try {
      FontMetrics metrics = g.getFontMetrics(getFont());
      double mark = markSize();
      double markY = (metrics.getHeight() - mark) / 2.0;
      if (caution) {
        g.setColor(style.caution);
        g.fill(new Ellipse2D.Double(0, markY, mark, mark));
        g.setColor(style.surface);
      } else {
        g.setColor(style.tertiaryText);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Ellipse2D.Double(0.6, markY + 0.6, mark - 1.2, mark - 1.2));
      }
      paintMarkGlyph(g, mark, markY);

      g.setFont(getFont());
      g.setColor(caution ? style.text : style.secondaryText);
      int baseline = metrics.getAscent();
      for (String line : lines(getWidth())) {
        g.drawString(line, textX(), baseline);
        baseline += metrics.getHeight();
      }
    } finally {
      g.dispose();
    }
  }

  private void paintMarkGlyph(Graphics2D g, double mark, double markY) {
    double centerX = mark / 2.0;
    double stem = mark * 0.36;
    double dot = Math.max(1.6, mark * 0.13);
    g.setStroke(
        new BasicStroke(
            (float) Math.max(1.4, mark * 0.12), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    if (caution) {
      double top = markY + mark * 0.24;
      g.draw(new Line2D.Double(centerX, top, centerX, top + stem));
      g.fill(new Ellipse2D.Double(centerX - dot / 2, markY + mark * 0.72, dot, dot));
    } else {
      double top = markY + mark * 0.44;
      g.fill(new Ellipse2D.Double(centerX - dot / 2, markY + mark * 0.22, dot, dot));
      g.draw(new Line2D.Double(centerX, top, centerX, top + stem * 0.85));
    }
  }
}
