package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.*;

import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.Stone;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Node;

class BoardDiagramTest {
  @TempDir Path temp;

  @Test
  void lastMoveTriangleIsCapturedAndPassDoesNotMarkAnEarlierStone() {
    BoardData data = position();
    var captured = BoardDiagram.capture(9, 9, data, false, 0, false, false);
    data.lastMove.get()[0] = 0;
    data.lastMove.get()[1] = 0;
    var image = captured.render(600);
    double step = 600 / 9.3;
    double center = (600 - 8 * step) / 2 + step;
    int markerX = (int) Math.round(center);
    int markerY = (int) Math.round(center - step * 0.18);
    assertNotEquals(Color.WHITE.getRGB(), image.getRGB(markerX, markerY));
    data.lastMove = java.util.Optional.empty();
    var unmarked = BoardDiagram.capture(9, 9, data, false, 0, false, false).render(600);
    assertEquals(Color.WHITE.getRGB(), unmarked.getRGB(markerX, markerY));
  }

  @Test
  void coordinateInkIsRemovedWithoutErasingForegroundStones() {
    BufferedImage plain = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
    var g = plain.createGraphics();
    g.setColor(Color.YELLOW);
    g.fillRect(0, 0, 20, 20);
    g.dispose();
    BufferedImage labelled = BoardScreenshotAction.copy(new Rectangle(0, 0, 20, 20), plain);
    labelled.setRGB(1, 1, Color.BLACK.getRGB());
    labelled.setRGB(2, 2, Color.BLACK.getRGB());
    BufferedImage foreground = BoardScreenshotAction.copy(new Rectangle(0, 0, 20, 20), labelled);
    foreground.setRGB(2, 2, Color.BLACK.getRGB());
    BufferedImage stones = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
    stones.setRGB(2, 2, Color.BLACK.getRGB());
    var result =
        BoardScreenshotAction.copyWithoutCoordinates(
            new Rectangle(0, 0, 20, 20),
            new Rectangle(0, 0, 20, 20),
            labelled,
            plain,
            new BufferedImage[] {stones},
            foreground);
    assertEquals(Color.YELLOW.getRGB(), result.getRGB(1, 1));
    assertEquals(Color.BLACK.getRGB(), result.getRGB(2, 2));
    assertEquals(Color.BLACK.getRGB(), labelled.getRGB(1, 1));
  }

  @Test
  void trialNumbersAndSnapshotAreIndependentOfFurtherMoves() {
    BoardData data = position();
    data.moveNumberList[0] = 35;
    data.moveNumberList[10] = -1;
    data.getProperties().put("TR", "cc");
    BoardDiagram captured = BoardDiagram.capture(9, 9, data, true, 0, false, false);
    String[] expectedNumbers = new String[81];
    Arrays.fill(expectedNumbers, "");
    expectedNumbers[10] = "1";
    BoardDiagram expected =
        new BoardDiagram(9, 9, data.stones, expectedNumbers, Map.of("TR", "cc"), 10);
    data.stones[0] = Stone.EMPTY;
    data.moveNumberList[10] = -8;
    data.getProperties().clear();
    assertPixelsEqual(expected.render(600), captured.render(600));
  }

  @Test
  void lastNMovesAndRenumberingMatchTheBoardSetting() {
    BoardData data = position();
    data.moveNumberList[0] = 28;
    data.moveNumberList[10] = 30;
    BoardDiagram diagram = BoardDiagram.capture(9, 9, data, false, 2, true, false);
    String[] numbers = new String[81];
    Arrays.fill(numbers, "");
    numbers[10] = "2";
    assertPixelsEqual(
        new BoardDiagram(9, 9, data.stones, numbers, Map.of(), 10).render(600),
        diagram.render(600));
  }

  @Test
  void snapshotNodesDoNotInventMoveNumbers() {
    BoardData data = BoardData.empty(9, 9);
    data.stones[0] = Stone.BLACK;
    data.moveNumberList[0] = 42;
    String[] numbers = new String[81];
    Arrays.fill(numbers, "");
    assertPixelsEqual(
        new BoardDiagram(9, 9, data.stones, numbers, Map.of()).render(600),
        BoardDiagram.capture(9, 9, data, false, -1, false, false).render(600));
  }

  @Test
  void printImageHasNativeResolutionAndPhysicalDensity() throws Exception {
    BoardData data = position();
    data.getProperties().put("LB", "cc:A,dd:测试");
    data.getProperties().put("TR", "ee");
    data.getProperties().put("CR", "ff");
    data.getProperties().put("SQ", "gg");
    data.getProperties().put("MA", "hh");
    BufferedImage image =
        BoardDiagram.capture(9, 9, data, false, -1, false, false).render(BoardDiagram.PIXELS);
    Path file = temp.resolve("teaching.png");
    BoardScreenshotAction.writePng(file, image, 300);
    try (var input = ImageIO.createImageInputStream(file.toFile())) {
      var reader = ImageIO.getImageReaders(input).next();
      try {
        reader.setInput(input);
        assertEquals(3600, reader.getWidth(0));
        assertEquals(3600, reader.getHeight(0));
        Node root = reader.getImageMetadata(0).getAsTree("javax_imageio_png_1.0");
        Node phys = null;
        for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling())
          if (n.getNodeName().equals("pHYs")) phys = n;
        assertNotNull(phys);
        assertEquals(
            "11811", phys.getAttributes().getNamedItem("pixelsPerUnitXAxis").getNodeValue());
        assertEquals(
            "11811", phys.getAttributes().getNamedItem("pixelsPerUnitYAxis").getNodeValue());
        assertEquals("meter", phys.getAttributes().getNamedItem("unitSpecifier").getNodeValue());
      } finally {
        reader.dispose();
      }
    }
    // Save a stable visual-review artifact without involving the application configuration.
    Path preview = Path.of("target/board-capture-preview.png");
    ImageIO.write(image, "png", preview.toFile());
  }

  @Test
  void screenCaptureCropsAndDetachesBothLayers() {
    BufferedImage background = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
    var g = background.createGraphics();
    g.setColor(Color.YELLOW);
    g.fillRect(0, 0, 20, 20);
    g.dispose();
    BufferedImage overlay = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
    overlay.setRGB(7, 8, Color.BLACK.getRGB());
    BufferedImage captured =
        BoardScreenshotAction.copy(new Rectangle(5, 6, 10, 10), background, overlay);
    overlay.setRGB(7, 8, Color.RED.getRGB());
    assertEquals(Color.BLACK.getRGB(), captured.getRGB(2, 2));
    assertEquals(Color.YELLOW.getRGB(), captured.getRGB(0, 0));
    assertEquals(10, captured.getWidth());
    assertEquals(10, captured.getHeight());
  }

  @Test
  void suffixAndFailedWritesPreserveExistingFiles() throws Exception {
    assertEquals(Path.of("diagram.PNG"), BoardScreenshotAction.pngPath(Path.of("diagram.PNG")));
    assertEquals(Path.of("diagram.png"), BoardScreenshotAction.pngPath(Path.of("diagram")));
    Path destination = temp.resolve("existing.png");
    Files.writeString(destination, "existing");
    assertThrows(
        java.io.IOException.class, () -> BoardScreenshotAction.writePng(destination, null, 300));
    assertEquals("existing", Files.readString(destination));
    Path directory = Files.createDirectory(temp.resolve("directory.png"));
    assertThrows(
        java.io.IOException.class,
        () -> BoardScreenshotAction.writePng(directory, new BufferedImage(2, 2, 1), 300));
    try (var files = Files.list(temp)) {
      assertFalse(files.anyMatch(p -> p.getFileName().toString().startsWith(".board-")));
    }
  }

  private static BoardData position() {
    Stone[] stones = new Stone[81];
    Arrays.fill(stones, Stone.EMPTY);
    stones[0] = Stone.BLACK;
    stones[10] = Stone.WHITE;
    return BoardData.move(
        stones, new int[] {1, 1}, Stone.WHITE, true, null, 30, new int[81], 0, 0, 50, 0);
  }

  private static void assertPixelsEqual(BufferedImage expected, BufferedImage actual) {
    assertArrayEquals(
        expected.getRGB(
            0, 0, expected.getWidth(), expected.getHeight(), null, 0, expected.getWidth()),
        actual.getRGB(0, 0, actual.getWidth(), actual.getHeight(), null, 0, actual.getWidth()));
  }
}
