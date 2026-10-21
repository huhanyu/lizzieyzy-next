package featurecat.lizzie.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.Icon;

/**
 * The board right-click menus' icons, drawn at paint time on the 24-unit grid of {@link
 * ToolbarGlyph}. Lines take the item's text colour, so they follow the theme and dim with a
 * disabled item. Colour stays only where it means something on the board: blue for analysed points,
 * red for avoided ones, and the stones. Each keeps the shape of the picture it replaces.
 */
enum BoardMenuGlyph {
  ANALYZE_ONLY,
  ANALYZE_ADD,
  AVOID,
  REMOVE,
  CLEAR,
  KEEP_OFF,
  KEEP_ON,
  EVALUATE,
  PRIORITY,
  UNDO,
  REDO,
  FIND,
  JUMP,
  BLACK_STONE,
  WHITE_STONE,
  BRANCH,
  MOVE,
  SWAP_COLOR,
  REVIEW;

  /** Icon size in pixels, as the pictures it replaces. */
  static final int SIZE = 16;

  private static final float STROKE = 2.2f;
  private static final Color STONE_BLACK = new Color(0x1E, 0x20, 0x24);
  private static final Color STONE_BLACK_ON_DARK = new Color(0x4A, 0x50, 0x59);
  private static final Color STONE_WHITE = new Color(0xF7, 0xF7, 0xF4);
  private static final Color STONE_EDGE = new Color(0x55, 0x5A, 0x62);
  private static final Color STONE_EDGE_ON_DARK = new Color(0x8A, 0x90, 0x99);
  private static final Color ALLOW_ON_DARK = new Color(0x5E, 0x93, 0xF0);
  private static final Color AVOID_ON_DARK = new Color(0xF0, 0x66, 0x5C);

  /** The glyph as an icon that paints in the colours of the item showing it. */
  Icon icon() {
    return new GlyphIcon(this);
  }

  private record GlyphIcon(BoardMenuGlyph glyph) implements Icon {
    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      Graphics2D g2 = (Graphics2D) g.create();
      try {
        g2.translate(x, y);
        g2.scale(SIZE / 24.0, SIZE / 24.0);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        glyph.paint(g2, Palette.of(c));
      } finally {
        g2.dispose();
      }
    }

    @Override
    public int getIconWidth() {
      return SIZE;
    }

    @Override
    public int getIconHeight() {
      return SIZE;
    }
  }

  /** Colours for one item: its text colour as ink, and whether it sits on a dark surface. */
  record Palette(Color ink, Color surface, boolean enabled) {
    static Palette of(Component c) {
      Color surface = c == null || c.getBackground() == null ? Color.WHITE : c.getBackground();
      Color text = c == null || c.getForeground() == null ? Color.DARK_GRAY : c.getForeground();
      boolean enabled = c == null || c.isEnabled();
      return new Palette(text, surface, enabled);
    }

    boolean dark() {
      return luminance(surface) < 0.45;
    }

    /** {@code color} as drawn: unchanged on an enabled item, faded into the surface otherwise. */
    Color tone(Color color) {
      if (enabled) return color;
      double keep = 0.42;
      return new Color(
          (int) Math.round(color.getRed() * keep + surface.getRed() * (1 - keep)),
          (int) Math.round(color.getGreen() * keep + surface.getGreen() * (1 - keep)),
          (int) Math.round(color.getBlue() * keep + surface.getBlue() * (1 - keep)));
    }

    private static double luminance(Color color) {
      return (0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue())
          / 255.0;
    }
  }

  /** Paints the glyph on the 24-unit grid. */
  void paint(Graphics2D g, Palette palette) {
    Color ink = palette.tone(palette.ink());
    // The board's blue and red marks, lifted a little on a dark menu to keep their contrast.
    Color allow = palette.tone(palette.dark() ? ALLOW_ON_DARK : ToolbarGlyph.ALLOW);
    Color avoid = palette.tone(palette.dark() ? AVOID_ON_DARK : ToolbarGlyph.AVOID);
    g.setStroke(line(STROKE));
    g.setColor(ink);
    switch (this) {
      case ANALYZE_ONLY -> {
        g.setColor(allow);
        g.setStroke(line(2.6f));
        g.draw(polyline(4.5, 12.5, 9.5, 17.5, 19.5, 6.5));
      }
      case ANALYZE_ADD -> {
        g.setColor(allow);
        g.setStroke(line(2.6f));
        g.draw(polyline(2.5, 14.5, 7, 19, 13.5, 11.5));
        g.draw(new Line2D.Double(18.5, 2.5, 18.5, 10.5));
        g.draw(new Line2D.Double(14.5, 6.5, 22.5, 6.5));
      }
      case AVOID -> {
        g.setColor(avoid);
        g.setStroke(line(2.4f));
        g.draw(new Ellipse2D.Double(4, 4, 16, 16));
        g.draw(new Line2D.Double(6.6, 6.6, 17.4, 17.4));
      }
      case REMOVE -> {
        g.draw(new Line2D.Double(6.5, 6.5, 17.5, 17.5));
        g.draw(new Line2D.Double(17.5, 6.5, 6.5, 17.5));
      }
      case CLEAR -> {
        g.draw(new Line2D.Double(4.5, 6.5, 19.5, 6.5));
        g.draw(polyline(9.5, 6.5, 9.5, 3.8, 14.5, 3.8, 14.5, 6.5));
        Path2D body = new Path2D.Double();
        body.moveTo(6.5, 6.5);
        body.lineTo(7.4, 18.6);
        body.quadTo(7.6, 20.5, 9.4, 20.5);
        body.lineTo(14.6, 20.5);
        body.quadTo(16.4, 20.5, 16.6, 18.6);
        body.lineTo(17.5, 6.5);
        g.draw(body);
        g.setStroke(line(1.6f));
        g.draw(new Line2D.Double(10.4, 10.2, 10.6, 16.8));
        g.draw(new Line2D.Double(13.6, 10.2, 13.4, 16.8));
      }
      case KEEP_OFF -> g.draw(new RoundRectangle2D.Double(4.5, 4.5, 15, 15, 5, 5));
      case KEEP_ON -> {
        RoundRectangle2D box = new RoundRectangle2D.Double(3.5, 3.5, 17, 17, 6, 6);
        g.setColor(allow);
        g.fill(box);
        g.setColor(palette.tone(Color.WHITE));
        g.setStroke(line(2.4f));
        g.draw(polyline(7.5, 12.2, 10.7, 15.4, 16.6, 8.8));
      }
      case EVALUATE -> {
        g.draw(new Ellipse2D.Double(5.5, 5.5, 13, 13));
        g.draw(new Line2D.Double(12, 2, 12, 6));
        g.draw(new Line2D.Double(12, 18, 12, 22));
        g.draw(new Line2D.Double(2, 12, 6, 12));
        g.draw(new Line2D.Double(18, 12, 22, 12));
        g.fill(new Ellipse2D.Double(9.6, 9.6, 4.8, 4.8));
      }
      case PRIORITY -> {
        g.draw(new Line2D.Double(12, 20.5, 12, 4.5));
        g.draw(polyline(6, 10.5, 12, 4.5, 18, 10.5));
      }
      case UNDO, REDO, JUMP -> {
        g.draw(new Ellipse2D.Double(3, 3, 18, 18));
        double turn = this == UNDO ? 0 : this == REDO ? Math.PI : -Math.PI / 2;
        g.rotate(turn, 12, 12);
        g.draw(new Line2D.Double(7.8, 12, 16.5, 12));
        g.draw(polyline(11.3, 8.5, 7.8, 12, 11.3, 15.5));
        g.rotate(-turn, 12, 12);
      }
      case FIND -> {
        g.draw(new Ellipse2D.Double(4, 4, 12.5, 12.5));
        g.setStroke(line(2.8f));
        g.draw(new Line2D.Double(15, 15, 20.5, 20.5));
      }
      case BLACK_STONE -> paintStone(g, palette, true);
      case WHITE_STONE -> paintStone(g, palette, false);
      case BRANCH -> {
        g.draw(new Line2D.Double(7, 3.5, 7, 20.5));
        Path2D fork = new Path2D.Double();
        fork.moveTo(7, 17);
        fork.curveTo(7, 12.5, 17, 13.5, 17, 10);
        g.draw(fork);
        g.draw(new Ellipse2D.Double(14.5, 3.5, 5, 5));
      }
      case MOVE -> {
        g.draw(new Line2D.Double(12, 3, 12, 21));
        g.draw(new Line2D.Double(3, 12, 21, 12));
        for (int quarter = 0; quarter < 4; quarter++) {
          g.rotate(quarter * Math.PI / 2, 12, 12);
          g.draw(polyline(9.2, 5.8, 12, 3, 14.8, 5.8));
          g.rotate(-quarter * Math.PI / 2, 12, 12);
        }
      }
      case SWAP_COLOR -> {
        Ellipse2D stone = new Ellipse2D.Double(3, 3, 18, 18);
        g.setColor(palette.tone(STONE_WHITE));
        g.fill(stone);
        g.setColor(palette.tone(palette.dark() ? STONE_BLACK_ON_DARK : STONE_BLACK));
        g.fill(new Arc2D.Double(3, 3, 18, 18, 90, 180, Arc2D.PIE));
        g.setColor(palette.tone(STONE_EDGE));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(stone);
      }
      case REVIEW -> {
        // A clock face whose rim runs back: looking at the board as it was then.
        g.draw(new Arc2D.Double(3.5, 3.5, 17, 17, 155, -300, Arc2D.OPEN));
        g.draw(polyline(2.4, 6.4, 3.8, 10.4, 7.8, 9));
        g.draw(polyline(12, 7.5, 12, 12, 15.2, 14));
      }
    }
  }

  private static void paintStone(Graphics2D g, Palette palette, boolean black) {
    Ellipse2D stone = new Ellipse2D.Double(3, 3, 18, 18);
    Color fill = black ? (palette.dark() ? STONE_BLACK_ON_DARK : STONE_BLACK) : STONE_WHITE;
    g.setColor(palette.tone(fill));
    g.fill(stone);
    if (black && !palette.dark()) return;
    // On a dark menu both stones get a light rim, so the black one stays a distinct disc.
    g.setColor(palette.tone(palette.dark() ? STONE_EDGE_ON_DARK : STONE_EDGE));
    g.setStroke(new BasicStroke(1.2f));
    g.draw(stone);
  }

  private static BasicStroke line(float width) {
    return new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
  }

  private static Path2D polyline(double... points) {
    Path2D path = new Path2D.Double();
    path.moveTo(points[0], points[1]);
    for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
    return path;
  }
}
