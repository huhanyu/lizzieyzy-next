package featurecat.lizzie.gui;

import featurecat.lizzie.Lizzie;
import java.awt.*;
import java.awt.event.*;
import java.text.MessageFormat;
import javax.swing.*;

/**
 * The top of the sidebar: the Comments and Problem moves tabs, and on the right the evaluation
 * status of the problem list. The Black/White filter and the sort order sit in the problem list's
 * own toolbar ({@link BlunderListPanel}); B and W still switch sides from here.
 */
public class SidebarHeaderPanel extends JPanel {
  private final SidebarPanel parentPanel;
  private ProblemListSnapshot currentSnapshot;

  private static final int NO_SEGMENT = -1;
  private static final int FIRST_SEGMENT = 0;
  private static final int SECOND_SEGMENT = 1;
  private static final int CONTROL_X = 10;
  private static final int APPLE_PRIMARY_Y = 14;
  private static final int APPLE_PRIMARY_WIDTH = 132;
  private static final int APPLE_PRIMARY_HEIGHT = 30;
  private static final int CLASSIC_PRIMARY_Y = 12;
  private static final int CLASSIC_ROW_HEIGHT = 32;
  private static final int CLASSIC_PRIMARY_BASELINE = 25;
  private static final int CLASSIC_PRIMARY_LEGACY_WIDTH = 98;
  private static final int CLASSIC_SECOND_LABEL_OFFSET = 45;
  private static final int CLASSIC_HIT_PADDING_X = 8;
  private static final int CLASSIC_HEIGHT = 48;
  private static final int APPLE_HEIGHT = 56;
  private static final int APPLE_LABEL_INSET = 8;
  private static final int CLASSIC_LABEL_GAP = 16;
  private static final int STATUS_DOT = 7;
  private static final int STATUS_GAP = 6;
  private static final int STATUS_MARGIN = 10;

  public SidebarHeaderPanel(SidebarPanel parentPanel) {
    this.parentPanel = parentPanel;
    setOpaque(false);
    setFocusable(true);
    setPreferredSize(new Dimension(200, preferredHeight(Lizzie.config.isAppleStyle)));
    AccessibilitySupport.named(
        this,
        text("SidebarHeader.accessibleName", "Comments and problem moves"),
        text(
            "SidebarHeader.accessibleDescription",
            "Switch between comments and problem moves, then filter by Black or White."));
    installKeyboardActions();
    addFocusListener(
        new FocusAdapter() {
          @Override
          public void focusGained(FocusEvent event) {
            repaint();
          }

          @Override
          public void focusLost(FocusEvent event) {
            repaint();
          }
        });

    addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            int segment =
                primarySegmentIndexAt(
                    e.getPoint(),
                    Lizzie.config.isAppleStyle,
                    getFontMetrics(headerFont()),
                    layoutWidth(),
                    progressLabelFor(currentSnapshot));
            if (segment == FIRST_SEGMENT) {
              parentPanel.switchTo("COMMENTS");
            } else if (segment == SECOND_SEGMENT) {
              parentPanel.switchTo("BLUNDERS");
            }
          }
        });
    addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent event) {
            if (Lizzie.config != null) {
              applyPreferredHeight();
            }
          }
        });
  }

  public void setShowingBlunders(boolean showingBlunders) {
    applyPreferredHeight();
    repaint();
  }

  private void applyPreferredHeight() {
    int height = preferredHeight(Lizzie.config.isAppleStyle);
    Dimension preferredSize = getPreferredSize();
    if (preferredSize == null || preferredSize.height != height) {
      setPreferredSize(new Dimension(200, height));
      revalidate();
    }
    repaint();
  }

  private int layoutWidth() {
    return getWidth() > 0 ? getWidth() : Integer.MAX_VALUE;
  }

  public void updateSnapshot(ProblemListSnapshot snapshot) {
    String previous = progressLabelFor(this.currentSnapshot);
    this.currentSnapshot = snapshot;
    setToolTipText(progressTooltipFor(snapshot));
    AccessibilitySupport.announce(this, previous, progressLabelFor(snapshot));
    applyPreferredHeight();
  }

  static int preferredHeight(boolean appleStyle) {
    return appleStyle ? APPLE_HEIGHT : CLASSIC_HEIGHT;
  }

  static String progressLabelFor(ProblemListSnapshot snapshot) {
    if (snapshot == null || snapshot.totalMoves <= 0) {
      return "";
    }
    int analyzedMoves = Math.max(0, Math.min(snapshot.analyzedMoves, snapshot.totalMoves));
    if (snapshot.analysisRunning) {
      return format(
          "SidebarHeader.progress.running",
          "Evaluating {0}/{1}",
          analyzedMoves,
          snapshot.totalMoves);
    }
    if (analyzedMoves >= snapshot.totalMoves) {
      return text("SidebarHeader.progress.complete", "Evaluation complete");
    }
    return format(
        "SidebarHeader.progress.partial", "Evaluated {0}/{1}", analyzedMoves, snapshot.totalMoves);
  }

  static String progressTooltipFor(ProblemListSnapshot snapshot) {
    String label = progressLabelFor(snapshot);
    if (label.isEmpty()) {
      return null;
    }
    return format("SidebarHeader.progress.tooltip", "Problem move progress: {0}.", label);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    boolean showBlunders = Lizzie.config.isShowingBlunderTabel;
    g2.setFont(headerFont());
    FontMetrics fm = g2.getFontMetrics();
    String status = progressLabelFor(currentSnapshot);
    HeaderLayout layout = headerLayout(Lizzie.config.isAppleStyle, fm, layoutWidth(), status);
    String commentsText = text("SidebarHeader.comments", "Comments");
    String problemsText = text("SidebarHeader.problems", "Problems");
    Color accent = glassAccentColor();

    if (Lizzie.config.isAppleStyle) {
      int x = layout.comments.x;
      int y = layout.comments.y;
      int halfW = layout.comments.width;
      int segW = halfW + layout.problems.width;
      int segH = layout.comments.height;
      int arc = 15;
      g2.setColor(new Color(255, 255, 255, 24));
      g2.fillRoundRect(x, y, segW, segH, arc, arc);
      g2.setColor(new Color(255, 255, 255, 18));
      g2.drawRoundRect(x, y, segW - 1, segH - 1, arc, arc);
      g2.setColor(showBlunders ? withAlpha(accent, 132) : new Color(255, 255, 255, 58));
      if (!showBlunders) {
        g2.fillRoundRect(x + 2, y + 2, halfW - 2, segH - 4, arc - 2, arc - 2);
      } else {
        g2.fillRoundRect(x + halfW, y + 2, halfW - 2, segH - 4, arc - 2, arc - 2);
      }
    } else {
      int underlineY = layout.primaryBaseline + 7;
      g2.setColor(withAlpha(accent, 220));
      if (showBlunders) {
        g2.fillRoundRect(layout.problemsTextX, underlineY, fm.stringWidth(problemsText), 3, 3, 3);
      } else {
        g2.fillRoundRect(layout.commentsTextX, underlineY, fm.stringWidth(commentsText), 3, 3, 3);
      }
    }
    g2.setColor(showBlunders ? SidebarPanel.secondaryTextColor() : SidebarPanel.primaryTextColor());
    g2.drawString(commentsText, layout.commentsTextX, layout.primaryBaseline);
    g2.setColor(showBlunders ? SidebarPanel.primaryTextColor() : SidebarPanel.secondaryTextColor());
    g2.drawString(problemsText, layout.problemsTextX, layout.primaryBaseline);
    paintStatus(g2, layout, status);
    paintKeyboardFocus(g2);
    g2.dispose();
  }

  /** A status line, not a button: a coloured dot and the evaluation progress. */
  private void paintStatus(Graphics2D g2, HeaderLayout layout, String status) {
    if (layout.progress.isEmpty()) {
      return;
    }
    boolean running = currentSnapshot != null && currentSnapshot.analysisRunning;
    boolean complete =
        currentSnapshot != null
            && !running
            && currentSnapshot.analyzedMoves >= currentSnapshot.totalMoves;
    int dotY = layout.primaryBaseline - g2.getFontMetrics().getAscent() / 2 - STATUS_DOT / 2 + 1;
    g2.setColor(
        running
            ? SidebarPanel.severityTextColor(3)
            : complete ? SidebarPanel.accentTextColor() : SidebarPanel.secondaryTextColor());
    g2.fillOval(layout.progress.x, dotY, STATUS_DOT, STATUS_DOT);
    g2.setFont(FittedFont.withKerning(g2.getFont().deriveFont(Font.PLAIN)));
    g2.setColor(running ? SidebarPanel.primaryTextColor() : SidebarPanel.secondaryTextColor());
    g2.drawString(status, layout.progress.x + STATUS_DOT + STATUS_GAP, layout.primaryBaseline);
  }

  static int primarySegmentIndexAt(Point point, boolean appleStyle, FontMetrics metrics) {
    return primarySegmentIndexAt(point, appleStyle, metrics, Integer.MAX_VALUE);
  }

  static int primarySegmentIndexAt(
      Point point, boolean appleStyle, FontMetrics metrics, int availableWidth) {
    return primarySegmentIndexAt(point, appleStyle, metrics, availableWidth, "");
  }

  static int primarySegmentIndexAt(
      Point point,
      boolean appleStyle,
      FontMetrics metrics,
      int availableWidth,
      String progressText) {
    HeaderLayout layout = headerLayout(appleStyle, metrics, availableWidth, progressText);
    if (layout.comments.contains(point)) {
      return FIRST_SEGMENT;
    }
    if (layout.problems.contains(point)) {
      return SECOND_SEGMENT;
    }
    return NO_SEGMENT;
  }

  static HeaderLayout headerLayout(
      boolean appleStyle, FontMetrics metrics, int availableWidth, String progressText) {
    String progress = progressText == null ? "" : progressText;
    return appleStyle
        ? appleLayout(metrics, availableWidth, progress)
        : classicLayout(metrics, availableWidth, progress);
  }

  static final class HeaderLayout {
    final Rectangle comments;
    final Rectangle problems;
    final Rectangle progress;
    final int commentsTextX;
    final int problemsTextX;
    final int primaryBaseline;
    final int height;

    HeaderLayout(
        Rectangle comments,
        Rectangle problems,
        Rectangle progress,
        int commentsTextX,
        int problemsTextX,
        int primaryBaseline,
        int height) {
      this.comments = comments;
      this.problems = problems;
      this.progress = progress;
      this.commentsTextX = commentsTextX;
      this.problemsTextX = problemsTextX;
      this.primaryBaseline = primaryBaseline;
      this.height = height;
    }
  }

  private static HeaderLayout classicLayout(
      FontMetrics metrics, int availableWidth, String progressText) {
    String commentsText = text("SidebarHeader.comments", "Comments");
    String problemsText = text("SidebarHeader.problems", "Problems");
    int commentsTextX = CONTROL_X;
    int problemsTextX = commentsTextX + classicSecondLabelOffset(metrics, commentsText);
    Rectangle comments = classicTextBounds(metrics, commentsText, commentsTextX, CLASSIC_PRIMARY_Y);
    Rectangle problems = classicTextBounds(metrics, problemsText, problemsTextX, CLASSIC_PRIMARY_Y);
    // Keep the generous legacy target to the right of the second tab.
    problems.width =
        Math.max(problems.x + problems.width, commentsTextX + CLASSIC_PRIMARY_LEGACY_WIDTH)
            - problems.x;
    Rectangle progress =
        statusBounds(
            metrics,
            progressText,
            availableWidth,
            problems.x + problems.width,
            CLASSIC_PRIMARY_BASELINE);
    return new HeaderLayout(
        comments,
        problems,
        progress,
        commentsTextX,
        problemsTextX,
        CLASSIC_PRIMARY_BASELINE,
        CLASSIC_HEIGHT);
  }

  private static HeaderLayout appleLayout(
      FontMetrics metrics, int availableWidth, String progressText) {
    String commentsText = text("SidebarHeader.comments", "Comments");
    String problemsText = text("SidebarHeader.problems", "Problems");
    int halfW =
        Math.max(
            APPLE_PRIMARY_WIDTH / 2,
            Math.max(metrics.stringWidth(commentsText), metrics.stringWidth(problemsText))
                + APPLE_LABEL_INSET);
    int primaryX = CONTROL_X;
    int primaryY = APPLE_PRIMARY_Y;
    Rectangle comments = new Rectangle(primaryX, primaryY, halfW, APPLE_PRIMARY_HEIGHT);
    Rectangle problems = new Rectangle(primaryX + halfW, primaryY, halfW, APPLE_PRIMARY_HEIGHT);
    int primaryBaseline = primaryY + APPLE_PRIMARY_HEIGHT / 2 + metrics.getAscent() / 2 - 1;
    Rectangle progress =
        statusBounds(metrics, progressText, availableWidth, primaryX + halfW * 2, primaryBaseline);
    return new HeaderLayout(
        comments,
        problems,
        progress,
        primaryX + (halfW - metrics.stringWidth(commentsText)) / 2,
        primaryX + halfW + (halfW - metrics.stringWidth(problemsText)) / 2,
        primaryBaseline,
        APPLE_HEIGHT);
  }

  /** Right-aligned status (dot and text); empty when it would touch the tabs. */
  private static Rectangle statusBounds(
      FontMetrics metrics,
      String progressText,
      int availableWidth,
      int occupiedRight,
      int baseline) {
    if (progressText.isEmpty() || availableWidth <= 0) {
      return new Rectangle();
    }
    int width = STATUS_DOT + STATUS_GAP + metrics.stringWidth(progressText);
    int x = availableWidth - STATUS_MARGIN - width;
    if (x <= occupiedRight + 8) {
      return new Rectangle();
    }
    return new Rectangle(x, baseline - metrics.getAscent(), width, metrics.getHeight());
  }

  private static Rectangle classicTextBounds(
      FontMetrics metrics, String text, int textX, int rowY) {
    int textWidth = Math.max(1, metrics.stringWidth(text));
    return new Rectangle(
        textX - CLASSIC_HIT_PADDING_X,
        rowY,
        textWidth + CLASSIC_HIT_PADDING_X * 2,
        CLASSIC_ROW_HEIGHT);
  }

  private static int classicSecondLabelOffset(FontMetrics metrics, String firstText) {
    return Math.max(
        CLASSIC_SECOND_LABEL_OFFSET, metrics.stringWidth(firstText) + CLASSIC_LABEL_GAP);
  }

  private Font headerFont() {
    String fontName =
        Lizzie.config != null && Lizzie.config.uiFontName != null
            ? Lizzie.config.uiFontName
            : getFont().getName();
    return FittedFont.withKerning(new Font(fontName, Font.BOLD, 12));
  }

  private void installKeyboardActions() {
    getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "comments");
    getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "problems");
    getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_B, 0), "black");
    getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0), "white");
    getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "toggle");
    getActionMap()
        .put(
            "comments",
            new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent event) {
                parentPanel.switchTo("COMMENTS");
                repaint();
              }
            });
    getActionMap()
        .put(
            "problems",
            new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent event) {
                parentPanel.switchTo("BLUNDERS");
                repaint();
              }
            });
    getActionMap()
        .put(
            "black",
            new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent event) {
                Lizzie.frame.setProblemListSideFilter(ProblemListSideFilter.BLACK);
              }
            });
    getActionMap()
        .put(
            "white",
            new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent event) {
                Lizzie.frame.setProblemListSideFilter(ProblemListSideFilter.WHITE);
              }
            });
    getActionMap()
        .put(
            "toggle",
            new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent event) {
                parentPanel.switchTo(Lizzie.config.isShowingBlunderTabel ? "COMMENTS" : "BLUNDERS");
                repaint();
              }
            });
  }

  private void paintKeyboardFocus(Graphics2D graphics) {
    if (!isFocusOwner()) {
      return;
    }
    graphics.setColor(withAlpha(glassAccentColor(), 230));
    graphics.setStroke(new BasicStroke(2F));
    graphics.drawRoundRect(2, 2, Math.max(0, getWidth() - 5), Math.max(0, getHeight() - 5), 10, 10);
  }

  private static String text(String key, String fallback) {
    try {
      if (Lizzie.resourceBundle != null && Lizzie.resourceBundle.containsKey(key)) {
        return Lizzie.resourceBundle.getString(key);
      }
    } catch (Exception error) {
    }
    return fallback;
  }

  private static String format(String key, String fallback, Object... arguments) {
    return MessageFormat.format(text(key, fallback), arguments);
  }

  private Color glassAccentColor() {
    return Lizzie.config != null && Lizzie.config.theme != null
        ? Lizzie.config.theme.glassAccentColor()
        : new Color(96, 165, 250);
  }

  private Color withAlpha(Color color, int alpha) {
    return new Color(
        color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
  }
}
