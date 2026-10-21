package featurecat.lizzie.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.util.Map;
import javax.swing.ImageIcon;

/**
 * The top toolbar's icons as line drawings on a 24-unit grid: one stroke weight, in the toolbar's
 * text colour, so light and dark toolbars get the same set. Each keeps the shape of the picture it
 * replaces. Colour stays only where it means something on the board: the stones, the blue and red
 * analysis-area marks and the green move-rating dot. A selected tool gets a tinted backing instead
 * of the old glow pictures.
 */
enum ToolbarGlyph {
  NEW_FILE,
  OPEN,
  SAVE,
  FLASH,
  HAWK_EYE,
  RANK_MARK_ON,
  RANK_MARK_OFF,
  SWAP_TURN,
  SET_MAIN,
  BACK_MAIN,
  CONTROL_PANEL,
  MARKUP,
  BLACK_STONE,
  WHITE_STONE,
  ALTERNATE_STONES,
  PASS,
  ALLOW_AREA,
  AVOID_AREA,
  CLEAR_AREAS,
  LABEL_LETTER,
  LABEL_NUMBER,
  MARK_X,
  MARK_SQUARE,
  MARK_TRIANGLE,
  MARK_CIRCLE,
  ERASER,
  PAINT;

  /** A glyph and whether it is drawn as the selected state of its tool. */
  record Variant(ToolbarGlyph glyph, boolean selected) {}

  /** The toolbar pictures these glyphs replace, by resource path. */
  static final Map<String, Variant> BY_ASSET =
      Map.ofEntries(
          Map.entry("/assets/newFile.png", new Variant(NEW_FILE, false)),
          Map.entry("/assets/open.png", new Variant(OPEN, false)),
          Map.entry("/assets/save.png", new Variant(SAVE, false)),
          Map.entry("/assets/flash.png", new Variant(FLASH, false)),
          Map.entry("/assets/hawkeye2.png", new Variant(HAWK_EYE, false)),
          Map.entry("/assets/RankMarkOn.png", new Variant(RANK_MARK_ON, false)),
          Map.entry("/assets/RankMarkOff.png", new Variant(RANK_MARK_OFF, false)),
          Map.entry("/assets/pass.png", new Variant(SWAP_TURN, false)),
          Map.entry("/assets/setmain.png", new Variant(SET_MAIN, false)),
          Map.entry("/assets/backmain.png", new Variant(BACK_MAIN, false)),
          Map.entry("/assets/control.png", new Variant(CONTROL_PANEL, false)),
          Map.entry("/assets/mark1.png", new Variant(MARKUP, false)),
          Map.entry("/assets/mark2.png", new Variant(MARKUP, true)),
          Map.entry("/assets/smallblack1.png", new Variant(BLACK_STONE, false)),
          Map.entry("/assets/smallblack2.png", new Variant(BLACK_STONE, true)),
          Map.entry("/assets/smallwhite.png", new Variant(WHITE_STONE, false)),
          Map.entry("/assets/smallwhite4.png", new Variant(WHITE_STONE, true)),
          Map.entry("/assets/hb.png", new Variant(ALTERNATE_STONES, false)),
          Map.entry("/assets/hb2.png", new Variant(ALTERNATE_STONES, true)),
          Map.entry("/assets/playpass.png", new Variant(PASS, false)),
          Map.entry("/assets/blueallow.png", new Variant(ALLOW_AREA, false)),
          Map.entry("/assets/blueallow2.png", new Variant(ALLOW_AREA, true)),
          Map.entry("/assets/redavoid.png", new Variant(AVOID_AREA, false)),
          Map.entry("/assets/redavoid2.png", new Variant(AVOID_AREA, true)),
          Map.entry("/assets/clear.png", new Variant(CLEAR_AREAS, false)),
          Map.entry("/assets/label.png", new Variant(LABEL_LETTER, false)),
          Map.entry("/assets/label2.png", new Variant(LABEL_LETTER, true)),
          Map.entry("/assets/labelnum1.png", new Variant(LABEL_NUMBER, false)),
          Map.entry("/assets/labelnum2.png", new Variant(LABEL_NUMBER, true)),
          Map.entry("/assets/x.png", new Variant(MARK_X, false)),
          Map.entry("/assets/x2.png", new Variant(MARK_X, true)),
          Map.entry("/assets/square.png", new Variant(MARK_SQUARE, false)),
          Map.entry("/assets/square2.png", new Variant(MARK_SQUARE, true)),
          Map.entry("/assets/sanjiao.png", new Variant(MARK_TRIANGLE, false)),
          Map.entry("/assets/sanjiao2.png", new Variant(MARK_TRIANGLE, true)),
          Map.entry("/assets/circle.png", new Variant(MARK_CIRCLE, false)),
          Map.entry("/assets/circle2.png", new Variant(MARK_CIRCLE, true)),
          Map.entry("/assets/eraser.png", new Variant(ERASER, false)),
          Map.entry("/assets/eraser2.png", new Variant(ERASER, true)),
          Map.entry("/assets/paint.png", new Variant(PAINT, false)));

  static final float STROKE = 2f;
  private static final double[] SCALES = {1, 1.25, 1.5, 1.75, 2, 2.5, 3};
  private static final Color INK = new Color(0x2B, 0x2F, 0x36);
  private static final Color INK_ON_DARK = new Color(0xE8, 0xEB, 0xEF);
  private static final Color STONE_BLACK = new Color(0x1E, 0x20, 0x24);
  private static final Color STONE_WHITE = new Color(0xF7, 0xF7, 0xF4);
  private static final Color STONE_EDGE = new Color(0x55, 0x5A, 0x62);
  private static final Color STONE_BLACK_ON_DARK = new Color(0x4A, 0x50, 0x59);
  static final Color ALLOW = new Color(0x2F, 0x6F, 0xDE);
  static final Color AVOID = new Color(0xD9, 0x43, 0x3B);
  static final Color RATING = new Color(0x2E, 0x9E, 0x4F);
  private static final Color SELECTED = new Color(0x2F, 0x6F, 0xDE, 46);
  private static final Color SELECTED_EDGE = new Color(0x2F, 0x6F, 0xDE, 150);
  private static final Color SELECTED_ON_DARK = new Color(0x7F, 0xAE, 0xFF, 72);
  private static final Color SELECTED_EDGE_ON_DARK = new Color(0x9F, 0xC2, 0xFF, 170);

  /** The glyph as an icon of {@code size} pixels, sharp at every display scale. */
  ImageIcon icon(int size, boolean dark, boolean selected) {
    Image[] variants = new Image[SCALES.length];
    for (int i = 0; i < SCALES.length; i++) {
      int pixels = (int) Math.round(size * SCALES[i]);
      BufferedImage image = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = image.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.scale(pixels / 24.0, pixels / 24.0);
      if (selected) paintSelection(g, dark);
      paint(g, dark);
      g.dispose();
      variants[i] = image;
    }
    return new ImageIcon(new BaseMultiResolutionImage(variants));
  }

  private static void paintSelection(Graphics2D g, boolean dark) {
    RoundRectangle2D backing = new RoundRectangle2D.Double(0.5, 0.5, 23, 23, 7, 7);
    g.setColor(dark ? SELECTED_ON_DARK : SELECTED);
    g.fill(backing);
    g.setColor(dark ? SELECTED_EDGE_ON_DARK : SELECTED_EDGE);
    g.setStroke(new BasicStroke(1.2f));
    g.draw(backing);
  }

  /** Paints the glyph on the 24-unit grid. */
  void paint(Graphics2D g, boolean dark) {
    Color ink = dark ? INK_ON_DARK : INK;
    g.setStroke(new BasicStroke(STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.setColor(ink);
    switch (this) {
      case NEW_FILE -> {
        g.draw(folder());
        g.draw(new Line2D.Double(12, 10.5, 12, 16.5));
        g.draw(new Line2D.Double(9, 13.5, 15, 13.5));
      }
      case OPEN -> {
        Path2D back = new Path2D.Double();
        back.moveTo(3, 17.5);
        back.lineTo(3, 6.5);
        back.quadTo(3, 5, 4.5, 5);
        back.lineTo(9, 5);
        back.lineTo(11, 7);
        back.lineTo(17.5, 7);
        back.quadTo(19, 7, 19, 8.5);
        back.lineTo(19, 10.5);
        g.draw(back);
        Path2D front = new Path2D.Double();
        front.moveTo(3, 19);
        front.lineTo(6.4, 11.6);
        front.quadTo(6.8, 10.5, 8, 10.5);
        front.lineTo(20.6, 10.5);
        front.quadTo(21.8, 10.5, 21.3, 11.7);
        front.lineTo(18.2, 18.2);
        front.quadTo(17.8, 19, 16.9, 19);
        front.closePath();
        g.draw(front);
      }
      case SAVE -> {
        Path2D body = new Path2D.Double();
        body.moveTo(5, 3.5);
        body.lineTo(16, 3.5);
        body.lineTo(20.5, 8);
        body.lineTo(20.5, 19);
        body.quadTo(20.5, 20.5, 19, 20.5);
        body.lineTo(5, 20.5);
        body.quadTo(3.5, 20.5, 3.5, 19);
        body.lineTo(3.5, 5);
        body.quadTo(3.5, 3.5, 5, 3.5);
        body.closePath();
        g.draw(body);
        Path2D shutter = new Path2D.Double();
        shutter.moveTo(7.5, 3.5);
        shutter.lineTo(7.5, 8);
        shutter.lineTo(14.5, 8);
        shutter.lineTo(14.5, 3.5);
        g.draw(shutter);
        Path2D label = new Path2D.Double();
        label.moveTo(7, 20.5);
        label.lineTo(7, 14);
        label.lineTo(17, 14);
        label.lineTo(17, 20.5);
        g.draw(label);
      }
      case FLASH ->
          g.draw(polygon(13.5, 2.5, 5, 13.5, 11.5, 13.5, 10.5, 21.5, 19, 10.5, 12.5, 10.5));
      case HAWK_EYE -> {
        g.draw(new Ellipse2D.Double(2.5, 2.5, 19, 19));
        g.fill(eagleHead());
      }
      case RANK_MARK_ON -> {
        Ellipse2D dot = new Ellipse2D.Double(4.5, 4.5, 15, 15);
        g.setColor(RATING);
        g.fill(dot);
        g.setColor(ink);
        g.draw(dot);
      }
      case RANK_MARK_OFF -> g.draw(new Ellipse2D.Double(4.5, 4.5, 15, 15));
      case SWAP_TURN -> {
        drawArcArrow(g, 30, 135);
        drawArcArrow(g, 210, 135);
      }
      case SET_MAIN -> {
        g.draw(new Line2D.Double(12, 4, 12, 19.5));
        g.draw(polyline(6.5, 14, 12, 19.5, 17.5, 14));
      }
      case BACK_MAIN -> {
        g.draw(polyline(9.5, 5.5, 4, 11, 9.5, 16.5));
        Path2D tail = new Path2D.Double();
        tail.moveTo(4, 11);
        tail.lineTo(14, 11);
        tail.quadTo(20, 11, 20, 17);
        tail.lineTo(20, 19);
        g.draw(tail);
      }
      case CONTROL_PANEL -> {
        g.draw(new RoundRectangle2D.Double(3.5, 3.5, 17, 17, 5, 5));
        double[] knobs = {13.5, 9.5, 14.5};
        for (int i = 0; i < 3; i++) {
          double x = 8.25 + i * 3.75;
          g.draw(new Line2D.Double(x, 7, x, 17));
          g.draw(new Line2D.Double(x - 1.6, knobs[i], x + 1.6, knobs[i]));
        }
      }
      case MARKUP -> {
        g.draw(polygon(15.5, 4, 20, 8.5, 9, 19.5, 4, 20, 4.5, 15));
        g.draw(new Line2D.Double(13.2, 6.3, 17.7, 10.8));
      }
      case BLACK_STONE -> paintStone(g, 12, 12, 9, true, dark);
      case WHITE_STONE -> paintStone(g, 12, 12, 9, false, dark);
      case ALTERNATE_STONES -> {
        paintStone(g, 9, 9, 7, true, dark);
        paintStone(g, 15, 15, 7, false, dark);
      }
      case PASS -> g.draw(hand());
      case ALLOW_AREA, AVOID_AREA -> {
        g.setColor(this == ALLOW_AREA ? ALLOW : AVOID);
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(9, 3.5, 9, 20.5));
        g.draw(new Line2D.Double(3.5, 9, 20.5, 9));
      }
      case CLEAR_AREAS -> {
        g.draw(new Line2D.Double(6.5, 6.5, 17.5, 17.5));
        g.draw(new Line2D.Double(17.5, 6.5, 6.5, 17.5));
      }
      case LABEL_LETTER -> {
        g.draw(polyline(6, 20, 12, 4, 18, 20));
        g.draw(new Line2D.Double(8.3, 14, 15.7, 14));
      }
      case LABEL_NUMBER -> g.draw(polyline(8.5, 7.5, 12.5, 4.5, 12.5, 20));
      case MARK_X -> {
        g.draw(new Line2D.Double(7.5, 8.5, 16.5, 19));
        g.draw(new Line2D.Double(16.5, 8.5, 7.5, 19));
      }
      case MARK_SQUARE -> g.draw(new Rectangle2D.Double(5, 5, 14, 14));
      case MARK_TRIANGLE -> g.draw(polygon(12, 4.5, 20, 19, 4, 19));
      case MARK_CIRCLE -> g.draw(new Ellipse2D.Double(4.5, 4.5, 15, 15));
      case ERASER -> {
        AffineTransform turn = AffineTransform.getRotateInstance(Math.toRadians(-45), 12, 11.5);
        g.draw(turn.createTransformedShape(new RoundRectangle2D.Double(4, 7.5, 16, 8, 3, 3)));
        g.draw(turn.createTransformedShape(new Line2D.Double(9.5, 7.5, 9.5, 15.5)));
        g.draw(new Line2D.Double(11, 20.5, 20, 20.5));
      }
      case PAINT -> {
        g.draw(new Line2D.Double(20, 4, 12.6, 11.4));
        Path2D bristles = new Path2D.Double();
        bristles.moveTo(12.6, 11.4);
        bristles.curveTo(14.6, 13.4, 14, 17.4, 10.6, 18.8);
        bristles.curveTo(8.6, 19.6, 6, 20, 3.8, 20);
        bristles.curveTo(5.6, 18.4, 5.4, 16.6, 6, 15);
        bristles.curveTo(7, 12.4, 10.2, 10.6, 12.6, 11.4);
        bristles.closePath();
        g.draw(bristles);
      }
    }
  }

  private static Shape folder() {
    Path2D path = new Path2D.Double();
    path.moveTo(3, 17.5);
    path.lineTo(3, 6.5);
    path.quadTo(3, 5, 4.5, 5);
    path.lineTo(9, 5);
    path.lineTo(11, 7);
    path.lineTo(19.5, 7);
    path.quadTo(21, 7, 21, 8.5);
    path.lineTo(21, 17.5);
    path.quadTo(21, 19, 19.5, 19);
    path.lineTo(4.5, 19);
    path.quadTo(3, 19, 3, 17.5);
    path.closePath();
    return path;
  }

  /** A solid eagle's head in profile, facing right, with its eye cut out. */
  private static Shape eagleHead() {
    Path2D head = new Path2D.Double();
    head.moveTo(5.2, 16.4);
    head.curveTo(3.9, 13, 4.3, 9.4, 6.4, 7.2);
    head.curveTo(8.4, 5.2, 11, 5.2, 13.2, 6.3);
    head.lineTo(15.6, 8.1);
    head.curveTo(17.6, 8.5, 19.4, 9.9, 19.9, 11.9);
    head.curveTo(20.3, 13.4, 19.8, 14.6, 18.8, 15.4);
    head.lineTo(18, 13);
    head.lineTo(12.8, 13.1);
    head.curveTo(11.2, 14.1, 10.2, 15.8, 9.6, 17.8);
    head.curveTo(8, 18.2, 6.3, 17.7, 5.2, 16.4);
    head.closePath();
    Area area = new Area(head);
    area.subtract(new Area(new Ellipse2D.Double(11.1, 8.4, 2.4, 2.4)));
    return area;
  }

  /** The outline of a raised hand: four fingers, a thumb and the palm, as one shape. */
  private static Shape hand() {
    Area hand = new Area(new RoundRectangle2D.Double(6.6, 10.2, 13.1, 10.6, 6, 6));
    double[][] fingers = {{6.6, 5.6, 9.2}, {9.9, 3.6, 10.6}, {13.2, 4.4, 10}, {16.5, 6.8, 7.8}};
    for (double[] finger : fingers) {
      hand.add(
          new Area(new RoundRectangle2D.Double(finger[0], finger[1], 3.2, finger[2], 3.2, 3.2)));
    }
    AffineTransform tilt = AffineTransform.getRotateInstance(Math.toRadians(-38), 6.4, 17.4);
    hand.add(
        new Area(
            tilt.createTransformedShape(
                new RoundRectangle2D.Double(1.8, 13.2, 3.2, 7.6, 3.2, 3.2))));
    return hand;
  }

  private static void paintStone(
      Graphics2D g, double cx, double cy, double r, boolean black, boolean dark) {
    Ellipse2D stone = new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
    // On a dark toolbar a black stone is lightened just enough to stay a solid disc.
    g.setColor(black ? (dark ? STONE_BLACK_ON_DARK : STONE_BLACK) : STONE_WHITE);
    g.fill(stone);
    if (black || dark) return;
    g.setColor(STONE_EDGE);
    g.setStroke(new BasicStroke(1.2f));
    g.draw(stone);
    g.setStroke(new BasicStroke(STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
  }

  /** An arc of the swap circle, counterclockwise from {@code start}, with its arrowhead. */
  private static void drawArcArrow(Graphics2D g, double start, double extent) {
    double r = 7.5;
    g.draw(new Arc2D.Double(12 - r, 12 - r, 2 * r, 2 * r, start, extent, Arc2D.OPEN));
    double end = Math.toRadians(start + extent);
    double x = 12 + r * Math.cos(end);
    double y = 12 - r * Math.sin(end);
    double tx = -Math.sin(end);
    double ty = -Math.cos(end);
    for (double turn : new double[] {Math.toRadians(150), Math.toRadians(-150)}) {
      double hx = tx * Math.cos(turn) - ty * Math.sin(turn);
      double hy = tx * Math.sin(turn) + ty * Math.cos(turn);
      g.draw(new Line2D.Double(x, y, x + 3.4 * hx, y + 3.4 * hy));
    }
  }

  private static Path2D polyline(double... points) {
    Path2D path = new Path2D.Double();
    path.moveTo(points[0], points[1]);
    for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
    return path;
  }

  private static Path2D polygon(double... points) {
    Path2D path = polyline(points);
    path.closePath();
    return path;
  }
}
