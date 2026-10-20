package featurecat.lizzie.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * A short text that breaks into as many lines as its width needs, left aligned or centred. Its
 * preferred height follows the width it is given.
 */
final class StrengthParagraph extends JPanel {
  private static final long serialVersionUID = 1L;
  private final transient StrengthStyle style;
  private final int align;
  private final int maxLines;
  private String text;
  private Color color;
  private transient int linesWidth = -1;
  private transient List<String> lines;

  StrengthParagraph(
      String text, Font font, Color color, int align, int maxLines, StrengthStyle style) {
    this.style = style;
    this.align = align;
    this.maxLines = maxLines;
    this.text = text == null ? "" : text;
    this.color = color;
    setFont(font);
    setOpaque(false);
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
    lines = null;
    getAccessibleContext().setAccessibleName(next);
    revalidate();
    repaint();
  }

  @Override
  public void setFont(Font font) {
    super.setFont(font);
    lines = null;
  }

  void setColor(Color value) {
    color = value;
    repaint();
  }

  /** The lines of the text at {@code width}, kept for the last width: measuring text is slow. */
  private List<String> lines(int width) {
    if (lines != null && linesWidth == width) {
      return lines;
    }
    lines = wrap(width);
    linesWidth = width;
    return lines;
  }

  /**
   * The text broken into lines no wider than {@code width}. Centred text is balanced: it uses the
   * narrowest width that needs no more lines, so the last line is not left with a word or two.
   */
  private List<String> wrap(int width) {
    FontMetrics metrics = getFontMetrics(getFont());
    int full = Math.max(40, width);
    List<String> wrapped = StrengthPaint.wrap(text, metrics, full, maxLines);
    int count = wrapped.size();
    if (align != StrengthPaint.CENTER || count < 2 || count >= maxLines) {
      return wrapped;
    }
    int low = full / 2;
    int high = full;
    while (low < high) {
      int middle = (low + high) / 2;
      if (StrengthPaint.wrap(text, metrics, middle, count + 1).size() <= count) {
        high = middle;
      } else {
        low = middle + 1;
      }
    }
    return StrengthPaint.wrap(text, metrics, high, maxLines);
  }

  int heightFor(int width) {
    return text.isEmpty() ? 0 : lines(width).size() * getFontMetrics(getFont()).getHeight();
  }

  @Override
  public Dimension getPreferredSize() {
    int width = getWidth() > 0 ? getWidth() : style.px(480);
    return new Dimension(width, heightFor(width));
  }

  @Override
  public Dimension getMaximumSize() {
    return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
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
      g.setFont(getFont());
      g.setColor(color);
      int baseline = metrics.getAscent();
      for (String line : lines(getWidth())) {
        int width = metrics.stringWidth(line);
        float x =
            align == StrengthPaint.CENTER
                ? (getWidth() - width) / 2f
                : align == StrengthPaint.RIGHT ? getWidth() - width : 0;
        g.drawString(line, x, baseline);
        baseline += metrics.getHeight();
      }
    } finally {
      g.dispose();
    }
  }
}
