package featurecat.lizzie.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * What the AI Coach marks on the main board: the area of a first hint, and a point, such as the
 * move of a second hint or the AI's choice after a 恶手. The coach changes them on the event thread,
 * and the board draws them over the stones.
 */
final class CoachBoardMarks {
  private static final Color AREA_FILL = new Color(61, 190, 170, 46);
  private static final Color AREA_EDGE = new Color(61, 190, 170, 210);
  private static final Color POINT = new Color(0x3d, 0xbe, 0xaa);
  private static final Color POINT_GLOW = new Color(61, 190, 170, 70);

  private int[] area;
  private int[] point;
  private int version;

  /** Marks the board area of {@link #areaOf}: inclusive {x0, y0, x1, y1}. */
  void showArea(int[] bounds) {
    area = bounds.clone();
    version++;
  }

  void showPoint(int x, int y) {
    point = new int[] {x, y};
    version++;
  }

  void clear() {
    if (area != null || point != null) {
      area = null;
      point = null;
      version++;
    }
  }

  boolean isEmpty() {
    return area == null && point == null;
  }

  int[] area() {
    return area == null ? null : area.clone();
  }

  int[] point() {
    return point == null ? null : point.clone();
  }

  /** Changes on every new mark, so the panel knows when to repaint the board. */
  int version() {
    return version;
  }

  /**
   * One of nine areas of the board: the corners, the sides and the centre, numbered row by row from
   * the top left, 0 to 8.
   */
  static int areaOf(int x, int y, int width, int height) {
    return third(y, height) * 3 + third(x, width);
  }

  /** The inclusive {x0, y0, x1, y1} of area {@code index}. */
  static int[] areaBounds(int index, int width, int height) {
    int column = index % 3;
    int row = index / 3;
    return new int[] {
      edge(column, width), edge(row, height), edge(column + 1, width) - 1, edge(row + 1, height) - 1
    };
  }

  private static int third(int value, int size) {
    return value < edge(1, size) ? 0 : value < edge(2, size) ? 1 : 2;
  }

  /** Where third {@code index} of a side starts: 0, 6, 13 and 19 on a 19×19 board. */
  private static int edge(int index, int size) {
    return (int) Math.round(size * index / 3.0);
  }

  /**
   * @param left the screen x of the board's first line
   * @param top the screen y of the board's first line
   */
  void paint(Graphics2D graphics, int left, int top, int squareWidth, int squareHeight) {
    if (isEmpty() || squareWidth <= 0 || squareHeight <= 0) {
      return;
    }
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      if (area != null) {
        int x0 = left + area[0] * squareWidth - squareWidth / 2;
        int y0 = top + area[1] * squareHeight - squareHeight / 2;
        int width = (area[2] - area[0] + 1) * squareWidth;
        int height = (area[3] - area[1] + 1) * squareHeight;
        int arc = squareWidth / 2;
        g.setColor(AREA_FILL);
        g.fillRoundRect(x0, y0, width, height, arc, arc);
        g.setColor(AREA_EDGE);
        g.setStroke(new BasicStroke(Math.max(2f, squareWidth / 14f)));
        g.drawRoundRect(x0, y0, width, height, arc, arc);
      }
      if (point != null) {
        int cx = left + point[0] * squareWidth;
        int cy = top + point[1] * squareHeight;
        int radius = squareWidth * 46 / 100;
        g.setColor(POINT_GLOW);
        g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
        g.setColor(POINT);
        g.setStroke(new BasicStroke(Math.max(2.5f, squareWidth / 9f)));
        g.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
      }
    } finally {
      g.dispose();
    }
  }
}
