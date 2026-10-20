package featurecat.lizzie.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.text.MessageFormat;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;

/**
 * The Black/White switch above the problem list. Each half shows a stone, the side's name and how
 * many problem moves that side has. The halves sit side by side, or one above the other when the
 * sidebar is too narrow for both.
 */
final class ProblemSideToggle extends JPanel {
  private static final int TRACK = 3;
  private final Half black = new Half(ProblemListSideFilter.BLACK);
  private final Half white = new Half(ProblemListSideFilter.WHITE);
  private final Consumer<ProblemListSideFilter> choose;
  private boolean stacked;

  ProblemSideToggle(Consumer<ProblemListSideFilter> choose) {
    super(null);
    this.choose = choose;
    setOpaque(false);
    ButtonGroup group = new ButtonGroup();
    group.add(black);
    group.add(white);
    add(black);
    add(white);
    black.setSelected(true);
    linkArrowKeys(black, white);
    linkArrowKeys(white, black);
  }

  /** Shows {@code side} as selected and the number of problem moves of each side. */
  void update(ProblemListSideFilter side, int blackCount, int whiteCount) {
    half(side == ProblemListSideFilter.WHITE ? side : ProblemListSideFilter.BLACK)
        .setSelected(true);
    black.setCount(blackCount);
    white.setCount(whiteCount);
    revalidate();
    repaint();
  }

  ProblemListSideFilter selectedSide() {
    return white.isSelected() ? ProblemListSideFilter.WHITE : ProblemListSideFilter.BLACK;
  }

  JToggleButton half(ProblemListSideFilter side) {
    return side == ProblemListSideFilter.WHITE ? white : black;
  }

  /** The size of the switch in {@code width}: side by side when that fits, otherwise stacked. */
  Dimension sizeFor(int width) {
    Dimension first = black.getPreferredSize();
    Dimension second = white.getPreferredSize();
    int half = Math.max(first.width, second.width);
    int row = Math.max(first.height, second.height);
    if (half * 2 + TRACK * 2 <= width) return new Dimension(half * 2 + TRACK * 2, row + TRACK * 2);
    return new Dimension(Math.min(width, half + TRACK * 2), row * 2 + TRACK * 2);
  }

  @Override
  public Dimension getPreferredSize() {
    return sizeFor(Integer.MAX_VALUE);
  }

  @Override
  public void setFont(Font font) {
    super.setFont(font);
    // The constructor sets the default font before the halves exist.
    if (black != null) {
      black.setFont(font);
      white.setFont(font);
    }
  }

  @Override
  public void doLayout() {
    Dimension row = sizeFor(Integer.MAX_VALUE);
    stacked = row.width > getWidth();
    int width = Math.max(1, getWidth() - TRACK * 2);
    if (stacked) {
      int height = Math.max(1, (getHeight() - TRACK * 2) / 2);
      black.setBounds(TRACK, TRACK, width, height);
      white.setBounds(TRACK, TRACK + height, width, height);
    } else {
      int half = width / 2;
      int height = Math.max(1, getHeight() - TRACK * 2);
      black.setBounds(TRACK, TRACK, half, height);
      white.setBounds(TRACK + half, TRACK, width - half, height);
    }
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int arc = arc(getHeight());
      g.setColor(SidebarPanel.rowHighlightColor(false));
      g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
      g.setColor(SidebarPanel.rowSeparatorColor());
      g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
    } finally {
      g.dispose();
    }
  }

  private int arc(int height) {
    return stacked ? 14 : height;
  }

  private void linkArrowKeys(Half from, Half to) {
    for (int key :
        new int[] {KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_DOWN}) {
      from.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, 0), "other-side");
    }
    from.getActionMap()
        .put(
            "other-side",
            new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent event) {
                to.requestFocusInWindow();
                if (!to.isSelected()) to.doClick(0);
              }
            });
  }

  private final class Half extends JToggleButton {
    private static final int PAD_X = 10;
    private static final int PAD_Y = 5;
    private static final int GAP = 6;
    private final ProblemListSideFilter side;
    private int count;

    Half(ProblemListSideFilter side) {
      this.side = side;
      setContentAreaFilled(false);
      setBorderPainted(false);
      setFocusPainted(false);
      setOpaque(false);
      setRolloverEnabled(true);
      addActionListener(event -> choose.accept(side));
      refreshText();
    }

    void setCount(int count) {
      if (this.count == count) return;
      this.count = count;
      refreshText();
    }

    private String label() {
      return BlunderListPanel.text(
          side == ProblemListSideFilter.WHITE ? "SidebarHeader.white" : "SidebarHeader.black");
    }

    private void refreshText() {
      setText(label());
      String moves =
          new MessageFormat(
                  BlunderListPanel.text("BlunderListPanel.count"),
                  BlunderListPanel.bundle().getLocale())
              .format(new Object[] {count});
      getAccessibleContext().setAccessibleName(label());
      getAccessibleContext().setAccessibleDescription(moves);
      setToolTipText(moves);
    }

    private Font labelFont() {
      return FittedFont.withKerning(getFont().deriveFont(Font.BOLD));
    }

    private Font countFont() {
      return FittedFont.withKerning(getFont());
    }

    private int stoneSize(FontMetrics metrics) {
      return Math.max(10, metrics.getAscent() - 2);
    }

    @Override
    public Dimension getPreferredSize() {
      FontMetrics labelMetrics = getFontMetrics(labelFont());
      FontMetrics countMetrics = getFontMetrics(countFont());
      int stone = stoneSize(labelMetrics);
      int width =
          PAD_X
              + stone
              + GAP
              + labelMetrics.stringWidth(label())
              + GAP
              + countMetrics.stringWidth(Integer.toString(count))
              + PAD_X;
      int height = Math.max(labelMetrics.getHeight(), stone) + PAD_Y * 2;
      return new Dimension(width, height);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      Graphics2D g = (Graphics2D) graphics.create();
      try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int arc = stacked ? 10 : getHeight();
        if (isSelected()) {
          g.setColor(SidebarPanel.rowHighlightColor(true));
          g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
          g.setColor(SidebarPanel.accentTextColor());
          g.setStroke(new BasicStroke(1.5f));
          g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc - 2, arc - 2);
        } else if (getModel().isRollover()) {
          g.setColor(SidebarPanel.rowHighlightColor(false));
          g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
        }
        Font labelFont = labelFont();
        FontMetrics labelMetrics = g.getFontMetrics(labelFont);
        int stone = stoneSize(labelMetrics);
        String number = Integer.toString(count);
        Font countFont = countFont();
        FontMetrics countMetrics = g.getFontMetrics(countFont);
        int content =
            stone
                + GAP
                + labelMetrics.stringWidth(label())
                + GAP
                + countMetrics.stringWidth(number);
        int x = Math.max(PAD_X, (getWidth() - content) / 2);
        int middle = getHeight() / 2;
        StoneGlyph.paint(g, x, middle - stone / 2.0, stone, side == ProblemListSideFilter.BLACK);
        x += stone + GAP;
        int baseline = middle + (labelMetrics.getAscent() - labelMetrics.getDescent()) / 2;
        g.setFont(labelFont);
        g.setColor(
            isSelected() ? SidebarPanel.primaryTextColor() : SidebarPanel.secondaryTextColor());
        g.drawString(label(), x, baseline);
        x += labelMetrics.stringWidth(label()) + GAP;
        g.setFont(countFont);
        g.setColor(SidebarPanel.secondaryTextColor());
        g.drawString(number, x, baseline);
        if (isFocusOwner()) {
          Color accent = SidebarPanel.accentTextColor();
          g.setColor(accent);
          g.setStroke(new BasicStroke(2f));
          g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc - 2, arc - 2);
        }
      } finally {
        g.dispose();
      }
    }
  }
}
