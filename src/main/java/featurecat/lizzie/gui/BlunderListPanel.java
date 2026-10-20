package featurecat.lizzie.gui;

import featurecat.lizzie.AppLocale;
import featurecat.lizzie.Config;
import featurecat.lizzie.Lizzie;
import java.awt.*;
import java.awt.event.*;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.*;

/** A single sorted view is shared by painting, navigation and accessibility. */
public class BlunderListPanel extends JPanel {
  private static final int TOOLBAR_GAP = 8;
  private final EntryModel model = new EntryModel();
  private final ReviewList list = new ReviewList();
  private final ProblemSideToggle sideToggle;
  private final ProblemSortButton sortButton;
  private final JPanel toolbar = new JPanel(null);
  private final JScrollPane scrollPane = new JScrollPane(list);
  private final JTextArea emptyText = wrappingText();
  private final JScrollPane emptyScroll = new JScrollPane(emptyText);
  private final JPanel body = new JPanel(new CardLayout());
  private final Consumer<ProblemMoveEntry> navigate;
  private final Supplier<ProblemListSideFilter> sideFilter;
  private ProblemListSnapshot snapshot;
  private Object gameIdentity;
  private EntryKey pressedEntry;
  private Object pressedGame;
  private int hoveredIndex = -1;
  private double largestLoss;
  // Every stone in the list is as wide as the longest move number, so the points line up.
  private String widestMoveNumber = "888";
  private boolean styled;
  private boolean appleStyle;

  public BlunderListPanel() {
    this(
        entry -> {
          if (Lizzie.frame != null) Lizzie.frame.jumpToProblemMove(entry);
        },
        () ->
            Lizzie.frame == null
                ? ProblemListSideFilter.BLACK
                : Lizzie.frame.getProblemListSideFilter(),
        side -> {
          if (Lizzie.frame != null) Lizzie.frame.setProblemListSideFilter(side);
        });
  }

  BlunderListPanel(
      Consumer<ProblemMoveEntry> navigate, Supplier<ProblemListSideFilter> sideFilter) {
    this(navigate, sideFilter, side -> {});
  }

  BlunderListPanel(
      Consumer<ProblemMoveEntry> navigate,
      Supplier<ProblemListSideFilter> sideFilter,
      Consumer<ProblemListSideFilter> chooseSide) {
    this.navigate = navigate;
    this.sideFilter = sideFilter;
    setOpaque(false);
    setLayout(new BorderLayout(0, 8));
    toolbar.setOpaque(false);
    sideToggle =
        new ProblemSideToggle(
            side -> {
              chooseSide.accept(side);
              rebuildRows(false);
            });
    String saved =
        Lizzie.config == null
            ? null
            : Lizzie.config.uiConfig.optString("problem-list-sort", "loss-desc");
    sortButton =
        new ProblemSortButton(
            ProblemListSort.fromConfigValue(saved),
            order -> {
              if (Lizzie.config != null) {
                Lizzie.config.uiConfig.put("problem-list-sort", order.configValue);
              }
              rebuildRows(false);
              if (list.getSelectedIndex() < 0) {
                scrollPane.getViewport().setViewPosition(new Point());
              } else {
                list.ensureIndexIsVisible(list.getSelectedIndex());
              }
            });
    toolbar.add(sideToggle);
    toolbar.add(sortButton);
    add(toolbar, BorderLayout.NORTH);
    list.setModel(model);
    list.setCellRenderer(new ProblemRow());
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setOpaque(false);
    list.setVisibleRowCount(-1);
    list.setToolTipText("");
    list.getAccessibleContext().setAccessibleName(text("SidebarHeader.problems"));
    for (JScrollPane pane : new JScrollPane[] {scrollPane, emptyScroll}) {
      pane.setBorder(BorderFactory.createEmptyBorder());
      pane.setOpaque(false);
      pane.getViewport().setOpaque(false);
      pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
      pane.getVerticalScrollBar().setUI(new SlimScrollBarUI());
      pane.getVerticalScrollBar().setOpaque(false);
    }
    emptyText.setBorder(BorderFactory.createEmptyBorder(16, 8, 16, 8));
    body.setOpaque(false);
    body.add(scrollPane, "rows");
    body.add(emptyScroll, "empty");
    add(body, BorderLayout.CENTER);
    installNavigation();
    scrollPane
        .getViewport()
        .addComponentListener(
            new ComponentAdapter() {
              @Override
              public void componentResized(ComponentEvent event) {
                list.setFixedCellWidth(Math.max(1, scrollPane.getViewport().getWidth()));
              }
            });
    refreshStyle();
    rebuildRows(false);
  }

  private void installNavigation() {
    list.addFocusListener(
        new FocusAdapter() {
          @Override
          public void focusGained(FocusEvent event) {
            if (list.getSelectedIndex() < 0 && model.getSize() > 0) {
              list.setSelectedIndex(Math.max(0, list.getFirstVisibleIndex()));
            }
          }
        });
    list.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent event) {
            pressedEntry = key(entryAt(event.getPoint()));
            pressedGame = gameIdentity;
          }

          @Override
          public void mouseReleased(MouseEvent event) {
            ProblemMoveEntry entry = entryAt(event.getPoint());
            if (SwingUtilities.isLeftMouseButton(event)
                && event.getClickCount() == 1
                && pressedEntry != null
                && pressedGame == gameIdentity
                && pressedEntry.equals(key(entry))) {
              navigate.accept(entry);
            }
            pressedEntry = null;
          }

          @Override
          public void mouseExited(MouseEvent event) {
            hoveredIndex = -1;
            list.repaint();
          }
        });
    list.addMouseMotionListener(
        new MouseAdapter() {
          @Override
          public void mouseMoved(MouseEvent event) {
            int next = indexAt(event.getPoint());
            if (hoveredIndex != next) {
              hoveredIndex = next;
              list.repaint();
            }
          }
        });
    list.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "open-problem");
    list.getInputMap().put(KeyStroke.getKeyStroke("SPACE"), "open-problem");
    list.getActionMap()
        .put(
            "open-problem",
            new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent event) {
                ProblemMoveEntry selected = list.getSelectedValue();
                if (selected != null) navigate.accept(selected);
              }
            });
  }

  public void updateSnapshot(ProblemListSnapshot snapshot) {
    updateSnapshot(snapshot, Lizzie.board == null ? null : Lizzie.board.getHistory().getStart());
  }

  void updateSnapshot(ProblemListSnapshot snapshot, Object currentGame) {
    boolean changedGame = gameIdentity != currentGame;
    gameIdentity = currentGame;
    this.snapshot = snapshot;
    refreshStyle();
    rebuildRows(changedGame);
  }

  private void rebuildRows(boolean changedGame) {
    EntryKey selected = changedGame ? null : key(list.getSelectedValue());
    int first = list.getFirstVisibleIndex();
    EntryKey anchor = changedGame || first < 0 ? null : key(model.getElementAt(first));
    Rectangle bounds = first < 0 ? null : list.getCellBounds(first, first);
    int offset = bounds == null ? 0 : scrollPane.getViewport().getViewPosition().y - bounds.y;
    List<ProblemMoveEntry> entries = new ArrayList<>();
    ProblemListSideFilter side = sideFilter.get();
    if (side == null || side == ProblemListSideFilter.ALL) side = ProblemListSideFilter.BLACK;
    if (snapshot != null) {
      entries.addAll(
          side == ProblemListSideFilter.BLACK ? snapshot.blackEntries : snapshot.whiteEntries);
    }
    sideToggle.update(
        side,
        snapshot == null ? 0 : snapshot.blackEntries.size(),
        snapshot == null ? 0 : snapshot.whiteEntries.size());
    entries.sort(sortOrder().comparator());
    largestLoss = 0;
    int digits = 3;
    for (ProblemMoveEntry entry : entries) {
      largestLoss = Math.max(largestLoss, entry.winrateLossAbs);
      digits = Math.max(digits, Integer.toString(entry.moveNumber).length());
    }
    widestMoveNumber = "8".repeat(digits);
    if (!model.sameEntries(entries)) {
      hoveredIndex = -1;
      model.replace(entries);
      list.setSelectedIndex(indexOf(selected));
      if (changedGame) {
        pressedEntry = null;
        scrollPane.getViewport().setViewPosition(new Point());
      } else {
        int anchorIndex = indexOf(anchor);
        Rectangle next = anchorIndex < 0 ? null : list.getCellBounds(anchorIndex, anchorIndex);
        if (next != null) {
          int maxY =
              Math.max(0, list.getPreferredSize().height - scrollPane.getViewport().getHeight());
          scrollPane
              .getViewport()
              .setViewPosition(new Point(0, Math.min(maxY, Math.max(0, next.y + offset))));
        }
      }
    } else if (changedGame) {
      list.clearSelection();
      pressedEntry = null;
      scrollPane.getViewport().setViewPosition(new Point());
    }
    String empty = emptyStateText(snapshot, bundle());
    if (!empty.equals(emptyText.getText())) {
      emptyText.setText(empty);
      emptyText.setCaretPosition(0);
      emptyText.getAccessibleContext().setAccessibleName(empty);
    }
    ((CardLayout) body.getLayout()).show(body, entries.isEmpty() ? "empty" : "rows");
    revalidate();
    repaint();
  }

  private int indexOf(EntryKey target) {
    if (target == null) return -1;
    for (int i = 0; i < model.getSize(); i++) {
      if (target.equals(key(model.getElementAt(i)))) return i;
    }
    return -1;
  }

  int indexAt(Point point) {
    int index = list.locationToIndex(point);
    Rectangle bounds = index < 0 ? null : list.getCellBounds(index, index);
    return bounds != null && bounds.contains(point) && point.y < bounds.y + bounds.height - 1
        ? index
        : -1;
  }

  private ProblemMoveEntry entryAt(Point point) {
    int index = indexAt(point);
    return index < 0 ? null : model.getElementAt(index);
  }

  JList<ProblemMoveEntry> reviewList() {
    return list;
  }

  ProblemSortButton sortControl() {
    return sortButton;
  }

  ProblemSideToggle sideControl() {
    return sideToggle;
  }

  ProblemListSort sortOrder() {
    return sortButton.order();
  }

  private void refreshStyle() {
    // Kerning makes Swing lay the Chinese-and-digits text out with TextLayout (see FittedFont).
    Font font =
        FittedFont.withKerning(AppleStyleSupport.workspaceFont(Font.PLAIN, Config.frameFontSize));
    boolean apple = Lizzie.config != null && Lizzie.config.isAppleStyle;
    if (styled
        && appleStyle == apple
        && font.equals(list.getFont())
        && SidebarPanel.secondaryTextColor().equals(emptyText.getForeground())) return;
    styled = true;
    appleStyle = apple;
    setFont(font);
    list.setFont(font);
    sideToggle.setFont(font);
    sortButton.setFont(font);
    sortButton.refreshText();
    emptyText.setFont(font);
    emptyText.setForeground(SidebarPanel.secondaryTextColor());
  }

  @Override
  public void doLayout() {
    int width = Math.max(1, getWidth());
    Dimension side = sideToggle.sizeFor(width);
    Dimension sort = sortButton.sizeFor(width);
    boolean wrap = side.width + TOOLBAR_GAP + sort.width > width;
    int rowHeight = Math.max(side.height, sort.height);
    toolbar.setPreferredSize(
        new Dimension(1, wrap ? side.height + TOOLBAR_GAP + sort.height : rowHeight));
    super.doLayout();
    if (wrap) {
      sideToggle.setBounds(0, 0, side.width, side.height);
      sortButton.setBounds(0, side.height + TOOLBAR_GAP, sort.width, sort.height);
    } else {
      sideToggle.setBounds(0, (rowHeight - side.height) / 2, side.width, side.height);
      sortButton.setBounds(
          width - sort.width, (rowHeight - sort.height) / 2, sort.width, sort.height);
    }
    body.doLayout();
  }

  static ResourceBundle bundle() {
    return Lizzie.resourceBundle != null ? Lizzie.resourceBundle : AppLocale.ENGLISH.loadBundle();
  }

  static String text(String key) {
    return bundle().getString(key);
  }

  private static String format(String key, Object... args) {
    return new MessageFormat(text(key), bundle().getLocale()).format(args);
  }

  static String emptyStatePrimary(boolean analysisRunning, ResourceBundle bundle) {
    return bundle.getString(
        analysisRunning ? "BlunderListPanel.organizing" : "BlunderListPanel.empty");
  }

  static String emptyStateHint(ResourceBundle bundle) {
    return bundle.getString("BlunderListPanel.emptyHint");
  }

  static String emptyStateText(ProblemListSnapshot snapshot, ResourceBundle bundle) {
    if (snapshot != null && snapshot.analysisRunning) return emptyStatePrimary(true, bundle);
    if (snapshot == null || snapshot.analyzedMoves == 0)
      return bundle.getString("BlunderListPanel.unanalyzed");
    return bundle.getString("BlunderListPanel.filteredEmpty");
  }

  private static String decimal(double value) {
    NumberFormat format = NumberFormat.getNumberInstance(bundle().getLocale());
    format.setMinimumFractionDigits(1);
    format.setMaximumFractionDigits(1);
    format.setGroupingUsed(false);
    return format.format(value);
  }

  static String moveLabel(ProblemMoveEntry entry) {
    return format(
        "BlunderListPanel.move",
        entry.moveNumber,
        text(entry.isBlack ? "SidebarHeader.black" : "SidebarHeader.white"),
        entry.coords);
  }

  static String detailLabel(ProblemMoveEntry entry) {
    String searches = format("BlunderListPanel.searches", entry.playouts);
    return entry.hasScoreLoss
        ? format("BlunderListPanel.score", decimal(entry.scoreLossAbs)) + "  \u00b7  " + searches
        : searches;
  }

  /** The score lost, shown under the point; empty when the engine reported no score. */
  static String visibleDetail(ProblemMoveEntry entry) {
    return entry.hasScoreLoss ? format("BlunderListPanel.score", decimal(entry.scoreLossAbs)) : "";
  }

  static String accessibleRow(ProblemMoveEntry entry, int index) {
    return format(
        "BlunderListPanel.row",
        index + 1,
        moveLabel(entry),
        decimal(entry.winrateLossAbs),
        detailLabel(entry));
  }

  private static JTextArea wrappingText() {
    JTextArea text = new JTextArea();
    text.setEditable(false);
    text.setFocusable(false);
    text.setOpaque(false);
    text.setLineWrap(true);
    text.setWrapStyleWord(true);
    text.setBorder(null);
    text.setMargin(new Insets(0, 0, 0, 0));
    return text;
  }

  private static EntryKey key(ProblemMoveEntry entry) {
    return entry == null ? null : new EntryKey(entry);
  }

  private static final class EntryKey {
    private final int move;
    private final boolean black;
    private final String coords;

    EntryKey(ProblemMoveEntry entry) {
      move = entry.moveNumber;
      black = entry.isBlack;
      coords = entry.coords;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof EntryKey)) return false;
      EntryKey key = (EntryKey) other;
      return move == key.move && black == key.black && Objects.equals(coords, key.coords);
    }

    @Override
    public int hashCode() {
      return Objects.hash(move, black, coords);
    }
  }

  private static final class EntryModel extends AbstractListModel<ProblemMoveEntry> {
    private List<ProblemMoveEntry> entries = new ArrayList<>();

    @Override
    public int getSize() {
      return entries.size();
    }

    @Override
    public ProblemMoveEntry getElementAt(int index) {
      return entries.get(index);
    }

    boolean sameEntries(List<ProblemMoveEntry> next) {
      if (entries.size() != next.size()) return false;
      for (int i = 0; i < next.size(); i++) {
        ProblemMoveEntry a = entries.get(i);
        ProblemMoveEntry b = next.get(i);
        if (!key(a).equals(key(b))
            || a.winrateLossAbs != b.winrateLossAbs
            || a.scoreLossAbs != b.scoreLossAbs
            || a.hasScoreLoss != b.hasScoreLoss
            || a.playouts != b.playouts
            || a.isCurrent != b.isCurrent
            || a.severityTier != b.severityTier) return false;
      }
      return true;
    }

    void replace(List<ProblemMoveEntry> next) {
      int old = entries.size();
      entries = next;
      int common = Math.min(old, next.size());
      if (common > 0) fireContentsChanged(this, 0, common - 1);
      if (next.size() > old) fireIntervalAdded(this, old, next.size() - 1);
      if (old > next.size()) fireIntervalRemoved(this, next.size(), old - 1);
    }
  }

  private final class ReviewList extends JList<ProblemMoveEntry> {
    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    @Override
    public String getToolTipText(MouseEvent event) {
      int index = indexAt(event.getPoint());
      return index < 0 ? null : accessibleRow(model.getElementAt(index), index);
    }

    @Override
    protected void processMouseEvent(MouseEvent event) {
      // BasicListUI otherwise selects the nearest row when the empty tail is clicked.
      if (event.getID() == MouseEvent.MOUSE_PRESSED && indexAt(event.getPoint()) < 0) {
        pressedEntry = null;
        return;
      }
      super.processMouseEvent(event);
    }
  }

  /**
   * One problem move: a stone with the move number, the point, the score lost and, on the right,
   * the winrate lost with a bar scaled to the largest loss in the list. Colour and position are
   * left to the stone and the filter; the full sentence stays in the tooltip and accessible name.
   */
  private final class ProblemRow extends JPanel implements ListCellRenderer<ProblemMoveEntry> {
    private static final int TOP = 9;
    private static final int BOTTOM = 9;
    private static final int LEFT = 12;
    private static final int RIGHT = 10;
    private static final int GAP = 10;
    private static final int BAR = 4;
    private final JTextArea title = wrappingText();
    private final JTextArea detail = wrappingText();
    private final JLabel loss = new JLabel("", SwingConstants.RIGHT);
    private final JTextArea lossLine = wrappingText();
    private ProblemMoveEntry entry;
    private Font badgeFont;
    private boolean selected;
    private boolean current;
    private boolean focused;
    private boolean hovered;
    private int rowWidth;
    private final Rectangle badge = new Rectangle();
    private final Rectangle bar = new Rectangle();

    ProblemRow() {
      super(null);
      setOpaque(false);
      title.setName("problem-point");
      detail.setName("problem-detail");
      loss.setName("problem-loss");
      lossLine.setName("problem-loss-line");
      add(title);
      add(detail);
      add(loss);
      add(lossLine);
    }

    @Override
    public Component getListCellRendererComponent(
        JList<? extends ProblemMoveEntry> owner,
        ProblemMoveEntry entry,
        int index,
        boolean selected,
        boolean focused) {
      this.entry = entry;
      this.selected = selected;
      this.current = entry.isCurrent;
      this.focused = focused;
      this.hovered = index == hoveredIndex;
      rowWidth =
          Math.max(1, owner.getFixedCellWidth() > 0 ? owner.getFixedCellWidth() : owner.getWidth());
      Font primary = owner.getFont();
      Font secondary = primary.deriveFont(Math.max(12f, primary.getSize2D() - 1f));
      badgeFont = secondary.deriveFont(Font.BOLD);
      title.setFont(primary.deriveFont(Font.BOLD));
      detail.setFont(secondary);
      loss.setFont(primary.deriveFont(Font.BOLD));
      lossLine.setFont(secondary.deriveFont(Font.BOLD));
      Color severity = SidebarPanel.severityTextColor(entry.severityTier);
      title.setForeground(SidebarPanel.primaryTextColor());
      detail.setForeground(SidebarPanel.secondaryTextColor());
      loss.setForeground(severity);
      lossLine.setForeground(severity);
      title.setText(entry.coords);
      detail.setText(visibleDetail(entry));
      loss.setText(decimal(entry.winrateLossAbs) + "%");
      loss.setToolTipText(text("BlunderListPanel.loss"));
      lossLine.setText(format("BlunderListPanel.lossValue", loss.getText()));
      String accessible = accessibleRow(entry, index);
      getAccessibleContext().setAccessibleName(accessible);
      getAccessibleContext().setAccessibleDescription(accessible);
      // CellRendererPane can reuse equal-sized cells without calling doLayout again.
      arrange();
      return this;
    }

    private int wrappedHeight(JTextArea text, int width) {
      text.setSize(Math.max(1, width), Short.MAX_VALUE);
      return text.getPreferredSize().height;
    }

    private int arrange() {
      int width = Math.max(1, rowWidth);
      FontMetrics badgeMetrics = getFontMetrics(badgeFont);
      int stone = StoneGlyph.numberedSize(badgeMetrics, widestMoveNumber);
      int start = LEFT + stone + GAP;
      int content = Math.max(1, width - start - RIGHT);
      int lossWidth = loss.getPreferredSize().width;
      int barWidth = Math.max(lossWidth, getFontMetrics(loss.getFont()).charWidth('0') * 5);
      // Keep the loss beside the point while the point still gets a few characters.
      boolean stacked =
          content < barWidth + GAP + getFontMetrics(title.getFont()).charWidth('M') * 4;
      int mainWidth = stacked ? content : Math.max(1, content - barWidth - GAP);
      int titleHeight = wrappedHeight(title, mainWidth);
      int y = TOP + titleHeight;
      title.setBounds(start, TOP, mainWidth, titleHeight);
      if (detail.getText().isEmpty()) {
        detail.setBounds(0, 0, 0, 0);
      } else {
        int detailHeight = wrappedHeight(detail, mainWidth);
        detail.setBounds(start, y + 2, mainWidth, detailHeight);
        y += 2 + detailHeight;
      }
      if (stacked) {
        loss.setBounds(0, 0, 0, 0);
        int lineHeight = wrappedHeight(lossLine, content);
        lossLine.setBounds(start, y + 4, content, lineHeight);
        y += 4 + lineHeight;
        bar.setBounds(start, y + 6, Math.min(content, barWidth * 2), BAR);
        y += 6 + BAR;
      } else {
        lossLine.setBounds(0, 0, 0, 0);
        int lossHeight = loss.getPreferredSize().height;
        loss.setBounds(width - RIGHT - barWidth, TOP, barWidth, Math.max(lossHeight, titleHeight));
        int barY = Math.max(y - BAR - 3, TOP + Math.max(lossHeight, titleHeight) + 6);
        bar.setBounds(width - RIGHT - barWidth, barY, barWidth, BAR);
        y = Math.max(y, barY + BAR);
      }
      int height = y + BOTTOM;
      badge.setBounds(LEFT, (height - stone) / 2, stone, stone);
      return height;
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(rowWidth, arrange());
    }

    @Override
    public void doLayout() {
      arrange();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      // Highlight, marker, separator and focus ring stay crisp, unantialiased rectangles.
      if (selected || current || hovered) {
        graphics.setColor(SidebarPanel.rowHighlightColor(selected || current));
        graphics.fillRect(0, 0, getWidth(), getHeight() - 1);
      }
      if (current || selected) {
        graphics.setColor(SidebarPanel.accentTextColor());
        graphics.fillRect(0, 5, 3, Math.max(0, getHeight() - 11));
      }
      graphics.setColor(SidebarPanel.rowSeparatorColor());
      graphics.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
      if (focused) {
        graphics.setColor(SidebarPanel.accentTextColor());
        graphics.drawRect(1, 1, Math.max(0, getWidth() - 3), Math.max(0, getHeight() - 4));
      }
      if (entry == null) return;
      Graphics2D g = (Graphics2D) graphics.create();
      try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        StoneGlyph.paintNumbered(
            g,
            badge.x,
            badge.y,
            badge.width,
            entry.isBlack,
            Integer.toString(entry.moveNumber),
            badgeFont);
        paintLossBar(g);
      } finally {
        g.dispose();
      }
    }

    private void paintLossBar(Graphics2D g) {
      if (bar.isEmpty()) return;
      Color track = SidebarPanel.secondaryTextColor();
      g.setColor(new Color(track.getRed(), track.getGreen(), track.getBlue(), 46));
      g.fillRoundRect(bar.x, bar.y, bar.width, bar.height, bar.height, bar.height);
      double share = largestLoss <= 0 ? 0 : Math.min(1, entry.winrateLossAbs / largestLoss);
      int filled = Math.max(bar.height, (int) Math.round(bar.width * share));
      g.setColor(
          entry.severityTier >= 3
              ? SidebarPanel.severityTextColor(entry.severityTier)
              : SidebarPanel.secondaryTextColor());
      // The bar grows from the right, under the right-aligned number.
      g.fillRoundRect(
          bar.x + bar.width - filled, bar.y, filled, bar.height, bar.height, bar.height);
    }
  }
}
