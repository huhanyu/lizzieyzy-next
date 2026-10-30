package featurecat.lizzie.gui;

import featurecat.lizzie.Config;
import featurecat.lizzie.rules.Board;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.imageio.*;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Captures on the EDT, then renders and writes a detached image off the EDT. */
final class BoardScreenshotAction {
  private static final Logger LOG = LoggerFactory.getLogger(BoardScreenshotAction.class);
  private final Component owner;
  private final Config config;
  private final Supplier<Board> board;
  private final BooleanSupplier trying;
  private final Supplier<BufferedImage> screen;
  private final ResourceBundle strings;
  private JButton button;

  BoardScreenshotAction(
      Component owner,
      Config config,
      Supplier<Board> board,
      BooleanSupplier trying,
      Supplier<BufferedImage> screen,
      ResourceBundle strings) {
    this.owner = owner;
    this.config = config;
    this.board = board;
    this.trying = trying;
    this.screen = screen;
    this.strings = strings;
  }

  void install(JButton button) {
    this.button = button;
    button.setName("boardScreenshot");
    button.setFocusable(false);
    button.setToolTipText(strings.getString("BoardScreenshot.tip"));
    button.addActionListener(e -> save());
  }

  private void save() {
    if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("EDT required");
    button.setEnabled(false);
    try {
      Board current = board.get();
      BoardDiagram diagram =
          BoardDiagram.capture(
              Board.boardWidth,
              Board.boardHeight,
              current.getData(),
              trying.getAsBoolean(),
              config.allowMoveNumber,
              config.showMoveNumberFromOne,
              config.showMoveAllInBranch
                  && !current.getHistory().getCurrentHistoryNode().isMainTrunk());
      var candidates =
          BoardDiagramCandidates.capture(
              Board.boardWidth,
              Board.boardHeight,
              current.getData(),
              config.winrateAlwaysBlack,
              10);
      String legend =
          strings.getString(
              config.winrateAlwaysBlack || current.getData().blackToPlay
                  ? "BoardScreenshot.blackWinrate"
                  : "BoardScreenshot.whiteWinrate");
      BufferedImage original = screen.get();
      JSONObject filesystem = config.persisted.getJSONObject("filesystem");
      JFileChooser chooser =
          new JFileChooser(
              filesystem.optString("last-image-folder", filesystem.optString("last-folder", ".")));
      chooser.setDialogTitle(strings.getString("BoardScreenshot.title"));
      chooser.setAcceptAllFileFilterUsed(false);
      chooser.setFileFilter(new FileNameExtensionFilter("PNG (*.png)", "png"));
      chooser.setSelectedFile(
          new java.io.File(
              "board-"
                  + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"))
                  + ".png"));
      JComboBox<String> mode =
          new JComboBox<>(
              new String[] {
                strings.getString("BoardScreenshot.print"),
                strings.getString("BoardScreenshot.candidates"),
                strings.getString("BoardScreenshot.screen")
              });
      JPanel accessory = new JPanel(new java.awt.BorderLayout(0, 8));
      accessory.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
      JPanel options = new JPanel(new java.awt.BorderLayout(0, 4));
      options.add(
          new JLabel(strings.getString("BoardScreenshot.mode")), java.awt.BorderLayout.NORTH);
      JPanel choices = new JPanel();
      choices.setLayout(new BoxLayout(choices, BoxLayout.Y_AXIS));
      mode.setMaximumSize(mode.getPreferredSize());
      choices.add(mode);
      JSpinner count =
          new JSpinner(
              new SpinnerNumberModel(
                  Math.max(
                      5, Math.min(10, filesystem.optInt("board-screenshot-candidate-count", 5))),
                  5,
                  10,
                  1));
      count.setName("boardScreenshotCandidateCount");
      count.setEnabled(false);
      mode.addActionListener(e -> count.setEnabled(mode.getSelectedIndex() == 1));
      JPanel countRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 6));
      countRow.add(new JLabel(strings.getString("BoardScreenshot.candidateCount")));
      countRow.add(count);
      choices.add(countRow);
      options.add(choices, java.awt.BorderLayout.SOUTH);
      JTextArea note = new JTextArea(strings.getString("BoardScreenshot.note"));
      note.setEditable(false);
      note.setLineWrap(true);
      note.setWrapStyleWord(true);
      note.setOpaque(false);
      note.setFont(UIManager.getFont("Label.font"));
      accessory.add(options, java.awt.BorderLayout.NORTH);
      accessory.add(note, java.awt.BorderLayout.CENTER);
      accessory.setPreferredSize(
          new java.awt.Dimension(Math.max(300, mode.getPreferredSize().width + 12), 180));
      chooser.setAccessory(accessory);
      if (chooser.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) {
        button.setEnabled(true);
        return;
      }
      Path destination = pngPath(chooser.getSelectedFile().toPath()).toAbsolutePath();
      if (Files.exists(destination)
          && JOptionPane.showConfirmDialog(
                  owner,
                  strings.getString("LizzieFrame.fileExists"),
                  strings.getString("LizzieFrame.warning"),
                  JOptionPane.OK_CANCEL_OPTION)
              != JOptionPane.OK_OPTION) {
        button.setEnabled(true);
        return;
      }
      boolean withCandidates = mode.getSelectedIndex() == 1;
      if (withCandidates && candidates.isEmpty()) {
        button.setEnabled(true);
        JOptionPane.showMessageDialog(
            owner,
            strings.getString("BoardScreenshot.noCandidates"),
            strings.getString("BoardScreenshot.title"),
            JOptionPane.INFORMATION_MESSAGE);
        return;
      }
      try {
        count.commitEdit();
      } catch (java.text.ParseException ex) {
        button.setEnabled(true);
        JOptionPane.showMessageDialog(owner, strings.getString("BoardScreenshot.candidateCount"));
        return;
      }
      int candidateCount = (Integer) count.getValue();
      var selectedCandidates = candidates.stream().limit(candidateCount).toList();
      boolean print = mode.getSelectedIndex() != 2;
      new SwingWorker<Void, Void>() {
        @Override
        protected Void doInBackground() throws Exception {
          writePng(
              destination,
              withCandidates
                  ? diagram.render(BoardDiagram.PIXELS, selectedCandidates, legend)
                  : print ? diagram.render(BoardDiagram.PIXELS) : original,
              print ? 300 : 0);
          return null;
        }

        @Override
        protected void done() {
          button.setEnabled(true);
          try {
            get();
            filesystem.put("last-image-folder", destination.getParent().toString());
            if (withCandidates) filesystem.put("board-screenshot-candidate-count", candidateCount);
            try {
              config.persist();
            } catch (IOException ex) {
              LOG.warn("Board image saved, but export directory was not persisted", ex);
            }
            JOptionPane.showMessageDialog(
                owner,
                strings.getString("BoardScreenshot.saved") + "\n" + destination,
                strings.getString("BoardScreenshot.title"),
                JOptionPane.INFORMATION_MESSAGE);
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            failure(ex);
          } catch (ExecutionException ex) {
            failure(ex);
          }
        }
      }.execute();
    } catch (RuntimeException ex) {
      button.setEnabled(true);
      failure(ex);
    }
  }

  private void failure(Exception ex) {
    LOG.warn("Board image export failed", ex);
    JOptionPane.showMessageDialog(
        owner,
        strings.getString("BoardScreenshot.failed"),
        strings.getString("BoardScreenshot.title"),
        JOptionPane.ERROR_MESSAGE);
  }

  static Path pngPath(Path path) {
    return path.toString().toLowerCase(Locale.ROOT).endsWith(".png")
        ? path
        : path.resolveSibling(path.getFileName() + ".png");
  }

  static BufferedImage copy(Rectangle bounds, BufferedImage... layers) {
    if (bounds.width <= 0 || bounds.height <= 0)
      throw new IllegalArgumentException("Board is not visible");
    BufferedImage image =
        new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    try {
      g.setColor(java.awt.Color.WHITE);
      g.fillRect(0, 0, bounds.width, bounds.height);
      for (BufferedImage layer : layers)
        if (layer != null) g.drawImage(layer, -bounds.x, -bounds.y, null);
    } finally {
      g.dispose();
    }
    return image;
  }

  static BufferedImage copyWithoutCoordinates(
      Rectangle crop,
      Rectangle board,
      BufferedImage labelled,
      BufferedImage plain,
      BufferedImage[] protectedLayers,
      BufferedImage... layers) {
    BufferedImage result = copy(crop, layers);
    if (labelled == null || plain == null) return result;
    for (int y = 0; y < result.getHeight(); y++)
      for (int x = 0; x < result.getWidth(); x++) {
        int bx = crop.x - board.x + x, by = crop.y - board.y + y;
        if (bx < 0
            || by < 0
            || bx >= labelled.getWidth()
            || by >= labelled.getHeight()
            || bx >= plain.getWidth()
            || by >= plain.getHeight()) continue;
        int before = labelled.getRGB(bx, by), after = plain.getRGB(bx, by);
        // Remove only coordinate ink that is still visible, never a stone or annotation over it.
        if (before != after
            && !hasForeground(bx, by, protectedLayers)
            && (result.getRGB(x, y) & 0xffffff) == (before & 0xffffff)) result.setRGB(x, y, after);
      }
    return result;
  }

  private static boolean hasForeground(int x, int y, BufferedImage[] layers) {
    for (BufferedImage layer : layers) {
      if (layer != null
          && x < layer.getWidth()
          && y < layer.getHeight()
          && (layer.getRGB(x, y) >>> 24) != 0) return true;
    }
    return false;
  }

  static void writePng(Path destination, BufferedImage image, int dpi) throws IOException {
    if (image == null) throw new IOException("No board image available");
    Path temp = Files.createTempFile(destination.toAbsolutePath().getParent(), ".board-", ".png");
    ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
    try {
      try (ImageOutputStream output = ImageIO.createImageOutputStream(temp.toFile())) {
        writer.setOutput(output);
        var metadata =
            writer.getDefaultImageMetadata(
                ImageTypeSpecifier.createFromRenderedImage(image), writer.getDefaultWriteParam());
        if (dpi > 0) {
          IIOMetadataNode root = new IIOMetadataNode("javax_imageio_png_1.0");
          IIOMetadataNode phys = new IIOMetadataNode("pHYs");
          String pixelsPerMeter = Integer.toString((int) Math.round(dpi / 0.0254));
          phys.setAttribute("pixelsPerUnitXAxis", pixelsPerMeter);
          phys.setAttribute("pixelsPerUnitYAxis", pixelsPerMeter);
          phys.setAttribute("unitSpecifier", "meter");
          root.appendChild(phys);
          metadata.mergeTree("javax_imageio_png_1.0", root);
        }
        writer.write(null, new IIOImage(image, null, metadata), writer.getDefaultWriteParam());
      }
      try {
        Files.move(
            temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ex) {
        Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      writer.dispose();
      Files.deleteIfExists(temp);
    }
  }
}
