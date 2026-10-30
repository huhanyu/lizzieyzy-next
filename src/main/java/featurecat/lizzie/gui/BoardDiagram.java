package featurecat.lizzie.gui;

import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.Stone;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Map;

/** Immutable teaching position, rendered independently of screen size and live renderer caches. */
final class BoardDiagram {
  static final int PIXELS = 3600;
  private final int width, height;
  private final int lastMove;
  private final Stone[] stones;
  private final String[] numbers;
  private final Map<String, String> marks;

  BoardDiagram(int width, int height, Stone[] stones, String[] numbers, Map<String, String> marks) {
    this(width, height, stones, numbers, marks, -1);
  }

  BoardDiagram(
      int width,
      int height,
      Stone[] stones,
      String[] numbers,
      Map<String, String> marks,
      int lastMove) {
    this.lastMove = lastMove;
    if (width < 2
        || height < 2
        || stones.length != width * height
        || numbers.length != stones.length)
      throw new IllegalArgumentException("Invalid board dimensions");
    this.width = width;
    this.height = height;
    this.stones = stones.clone();
    this.numbers = numbers.clone();
    this.marks = Map.copyOf(marks);
  }

  static BoardDiagram capture(
      int width,
      int height,
      BoardData data,
      boolean trying,
      int numberLimit,
      boolean fromOne,
      boolean allInBranch) {
    int last = Arrays.stream(data.moveNumberList).max().orElse(0);
    String[] numbers = new String[data.stones.length];
    Arrays.fill(numbers, "");
    if (!data.isSnapshotNode()) {
      for (int i = 0; i < numbers.length; i++) {
        int n = data.moveNumberList[i];
        if (trying) {
          if (n < 0) numbers[i] = Integer.toString(-n);
        } else if (n > 0 && (allInBranch || numberLimit < 0 || last - n < numberLimit)) {
          numbers[i] =
              Integer.toString(
                  fromOne && !allInBranch && numberLimit > 0
                      ? n - Math.max(0, last - numberLimit)
                      : n);
        }
      }
    }
    int lastMove =
        data.isSnapshotNode()
            ? -1
            : data.lastMove
                .filter(
                    p -> p.length >= 2 && p[0] >= 0 && p[0] < width && p[1] >= 0 && p[1] < height)
                .map(p -> p[0] * height + p[1])
                .orElse(-1);
    return new BoardDiagram(width, height, data.stones, numbers, data.getProperties(), lastMove);
  }

  BufferedImage render(int pixels) {
    return render(pixels, java.util.List.of(), "");
  }

  BufferedImage render(
      int pixels, java.util.List<BoardDiagramCandidates.Candidate> candidates, String legend) {
    if (pixels < 200 || pixels > 7200) throw new IllegalArgumentException("Invalid export size");
    BufferedImage image = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, pixels, pixels);
      double step = pixels / (Math.max(width, height) + 0.3);
      double left = (pixels - (width - 1) * step) / 2;
      double top = (pixels - (height - 1) * step) / 2;
      double radius = step * 0.46;
      g.setStroke(new BasicStroke((float) (step * 0.012)));
      g.setColor(Color.BLACK);
      for (int x = 0; x < width; x++)
        g.draw(new Line2D.Double(left + x * step, top, left + x * step, top + (height - 1) * step));
      for (int y = 0; y < height; y++)
        g.draw(new Line2D.Double(left, top + y * step, left + (width - 1) * step, top + y * step));
      if (width == height) {
        int[] stars =
            width == 19
                ? new int[] {3, 9, 15}
                : width == 13 ? new int[] {3, 6, 9} : width == 9 ? new int[] {2, 4, 6} : new int[0];
        for (int x : stars)
          for (int y : stars) {
            if (width != 19 && (x == width / 2) != (y == height / 2)) continue;
            double r = step * 0.055;
            g.fill(new Ellipse2D.Double(left + x * step - r, top + y * step - r, 2 * r, 2 * r));
          }
      }
      for (int x = 0; x < width; x++)
        for (int y = 0; y < height; y++) {
          int i = x * height + y;
          if (stones[i].isEmpty()) continue;
          double cx = left + x * step, cy = top + y * step;
          Shape stone = new Ellipse2D.Double(cx - radius, cy - radius, 2 * radius, 2 * radius);
          g.setColor(stones[i].isBlack() ? Color.BLACK : Color.WHITE);
          g.fill(stone);
          g.setColor(Color.BLACK);
          g.setStroke(new BasicStroke((float) (step * 0.018)));
          g.draw(stone);
          g.setColor(stones[i].isBlack() ? Color.WHITE : Color.BLACK);
          text(g, numbers[i], cx, cy, step * 0.55, radius * 1.7);
          if (i == lastMove) {
            boolean numbered = !numbers[i].isEmpty();
            double r = step * (numbered ? 0.065 : 0.18);
            double markerY = cy - (numbered ? step * 0.34 : 0);
            Path2D triangle = new Path2D.Double();
            triangle.moveTo(cx, markerY - r);
            triangle.lineTo(cx + r, markerY + r);
            triangle.lineTo(cx - r, markerY + r);
            triangle.closePath();
            g.setStroke(new BasicStroke((float) (step * 0.025)));
            if (numbered) g.fill(triangle);
            else g.draw(triangle);
          }
        }
      for (String key : new String[] {"LB", "TR", "SQ", "CR", "MA"}) {
        String entries = marks.getOrDefault(key, "");
        for (String entry : entries.split(",")) {
          String[] parts = entry.split(":", 2);
          if (parts[0].length() != 2) continue;
          int x = coordinate(parts[0].charAt(0)), y = coordinate(parts[0].charAt(1));
          if (x < 0 || y < 0 || x >= width || y >= height) continue;
          double cx = left + x * step, cy = top + y * step, r = step * 0.27;
          Stone stone = stones[x * height + y];
          // Clear the grid/number underneath a teaching mark so it remains legible in print.
          g.setColor(stone.isBlack() ? Color.BLACK : Color.WHITE);
          double clearRadius = stone.isEmpty() ? r * 1.3 : radius * 0.97;
          g.fill(
              new Ellipse2D.Double(
                  cx - clearRadius, cy - clearRadius, clearRadius * 2, clearRadius * 2));
          g.setColor(stone.isBlack() ? Color.WHITE : Color.BLACK);
          g.setStroke(new BasicStroke((float) (step * 0.025)));
          switch (key) {
            case "LB":
              if (parts.length == 2) text(g, parts[1], cx, cy, step * 0.5, step * 0.75);
              break;
            case "CR":
              g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
              break;
            case "SQ":
              g.draw(new Rectangle2D.Double(cx - r, cy - r, 2 * r, 2 * r));
              break;
            case "MA":
              g.draw(new Line2D.Double(cx - r, cy - r, cx + r, cy + r));
              g.draw(new Line2D.Double(cx - r, cy + r, cx + r, cy - r));
              break;
            case "TR":
              Path2D triangle = new Path2D.Double();
              triangle.moveTo(cx, cy - r);
              triangle.lineTo(cx + r, cy + r);
              triangle.lineTo(cx - r, cy + r);
              triangle.closePath();
              g.draw(triangle);
              break;
            default:
              break;
          }
        }
      }
      for (int i = 0; i < candidates.size(); i++) {
        BoardDiagramCandidates.Candidate candidate = candidates.get(i);
        double cx = left + candidate.x() * step, cy = top + candidate.y() * step;
        double r = step * 0.32;
        int rank = candidate.order() + 1;
        Shape circle = new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);
        g.setColor(Color.WHITE);
        g.fill(circle);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke((float) (step * (rank == 1 ? 0.02 : 0.012))));
        g.draw(circle);
        text(
            g,
            String.format(java.util.Locale.ROOT, "%.1f%%", candidate.winrate()),
            cx,
            cy,
            step * 0.18,
            step * 0.57);
        // A small superscript breaks the upper-right rim without resembling a played move.
        double badgeX = cx + step * 0.27, badgeY = cy - step * 0.29;
        g.setColor(Color.WHITE);
        g.fill(
            new Ellipse2D.Double(
                badgeX - step * 0.12, badgeY - step * 0.11, step * 0.24, step * 0.22));
        g.setColor(Color.BLACK);
        text(g, Integer.toString(rank), badgeX, badgeY, step * 0.18, step * 0.21);
      }

    } finally {
      g.dispose();
    }
    return image;
  }

  static int coordinate(char c) {
    return c >= 'a' && c <= 'z' ? c - 'a' : c >= 'A' && c <= 'Z' ? c - 'A' + 26 : -1;
  }

  private static void text(
      Graphics2D g, String text, double x, double y, double size, double maxWidth) {
    if (text == null || text.isEmpty()) return;
    Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 1).deriveFont((float) size);
    g.setFont(font);
    double measured = g.getFontMetrics().stringWidth(text);
    if (measured > maxWidth) g.setFont(font.deriveFont((float) (size * maxWidth / measured)));
    FontMetrics fm = g.getFontMetrics();
    g.drawString(
        text,
        (float) (x - fm.stringWidth(text) / 2.0),
        (float) (y + (fm.getAscent() - fm.getDescent()) / 2.0));
  }
}
