package featurecat.lizzie.gui;

import featurecat.lizzie.analysis.PlayerStrengthEstimator;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javax.swing.JPanel;
import javax.swing.JToolTip;

/**
 * One phase of the game: its moves, each side's rank in it with the side's best and weakest phase
 * marked, both ranks on one ruler, and which side played the phase better.
 */
final class StrengthPhaseCard extends JPanel {
  private static final long serialVersionUID = 1L;
  private final transient StrengthStyle style;
  private final transient ResourceBundle bundle;
  private final transient StrengthPhases.Phase phase;
  private final transient StrengthScale scale;
  private final boolean humanStyle;
  private final String blackName;
  private final String whiteName;
  private final String[] badges = new String[2];
  private Rectangle ruler = new Rectangle();
  private final List<Rectangle> confidences = new ArrayList<>();

  StrengthPhaseCard(
      List<StrengthPhases.Phase> phases,
      int index,
      boolean humanStyle,
      String blackName,
      String whiteName,
      ResourceBundle bundle,
      StrengthStyle style) {
    this.style = style;
    this.bundle = bundle;
    this.phase = phases.get(index);
    this.humanStyle = humanStyle;
    this.blackName = blackName;
    this.whiteName = whiteName;
    scale = new StrengthScale(bundle, style);
    for (int side = 0; side < 2; side++) {
      boolean isBlack = side == 0;
      if (StrengthPhases.extreme(phases, isBlack, true) == index) {
        badges[side] = bundle.getString("PlayerStrengthEstimate.phase.best");
      } else if (StrengthPhases.extreme(phases, isBlack, false) == index) {
        badges[side] = bundle.getString("PlayerStrengthEstimate.phase.weakest");
      }
    }
    setOpaque(false);
    setToolTipText("");
    getAccessibleContext().setAccessibleName(name());
    getAccessibleContext().setAccessibleDescription(description());
  }

  StrengthPhases.Phase phase() {
    return phase;
  }

  /** The badge of a side in this phase: its best or weakest phase, or null. */
  String badge(boolean isBlack) {
    return badges[isBlack ? 0 : 1];
  }

  private String name() {
    return bundle.getString("PlayerStrengthEstimate.phase." + phase.name());
  }

  private String range() {
    return phase.played()
        ? MessageFormat.format(
            bundle.getString("PlayerStrengthEstimate.phase.range"), phase.first(), phase.last())
        : bundle.getString("PlayerStrengthEstimate.phase.unplayed");
  }

  /** What the line under the ruler says: who played this phase better, or why there is no rank. */
  String verdict() {
    if (!phase.played()) {
      return bundle.getString("PlayerStrengthEstimate.phase.notReached");
    }
    if (humanStyle && phase.first() > StrengthPhases.HUMAN_STYLE_MOVES) {
      return MessageFormat.format(
          bundle.getString("PlayerStrengthEstimate.phase.humanStyleLimit"),
          StrengthPhases.HUMAN_STYLE_MOVES);
    }
    double lead = phase.lead();
    if (Double.isNaN(lead)) {
      return bundle.getString("PlayerStrengthEstimate.phase.tooFew");
    }
    if (Math.abs(lead) < StrengthPhases.EVEN) {
      return bundle.getString("PlayerStrengthEstimate.phase.even");
    }
    return MessageFormat.format(
        bundle.getString(
            Math.abs(lead) < StrengthPhases.CLEAR
                ? "PlayerStrengthEstimate.phase.slightly"
                : "PlayerStrengthEstimate.phase.clearly"),
        lead > 0 ? blackName : whiteName);
  }

  private String rankText(boolean isBlack) {
    return phase.ranked(isBlack) ? StrengthText.rank(phase.side(isBlack), bundle) : "\u2014";
  }

  private String description() {
    return range()
        + "; "
        + blackName
        + " "
        + rankText(true)
        + "; "
        + whiteName
        + " "
        + rankText(false)
        + "; "
        + verdict();
  }

  private int padding() {
    return style.px(20);
  }

  private int titleHeight() {
    return Math.max(style.px(22), getFontMetrics(style.font(Font.BOLD, 3)).getHeight());
  }

  private int rowHeight() {
    return Math.max(style.px(28), getFontMetrics(style.font(Font.BOLD, 2)).getHeight());
  }

  private int verdictHeight() {
    return getFontMetrics(style.font(Font.PLAIN, 0)).getHeight();
  }

  @Override
  public Dimension getPreferredSize() {
    int height =
        padding()
            + titleHeight()
            + style.px(14)
            + rowHeight() * 2
            + style.px(6)
            + style.px(12)
            + scale.height(this)
            + style.px(14)
            + 1
            + style.px(10)
            + verdictHeight()
            + padding();
    return new Dimension(style.px(300), height);
  }

  @Override
  public Dimension getMinimumSize() {
    return new Dimension(style.px(240), getPreferredSize().height);
  }

  @Override
  public String getToolTipText(MouseEvent event) {
    String help = null;
    if (ruler.contains(event.getPoint())) {
      help = bundle.getString("PlayerStrengthEstimate.help.rank");
    } else {
      for (Rectangle confidence : confidences) {
        if (confidence.contains(event.getPoint())) {
          help = bundle.getString("PlayerStrengthEstimate.help.confidence");
        }
      }
    }
    return help == null
        ? null
        : StrengthText.wrappedTooltip(
            help, getFontMetrics(style.font(Font.PLAIN, 0)), style.px(300));
  }

  @Override
  public JToolTip createToolTip() {
    JToolTip tip = super.createToolTip();
    tip.setFont(style.font(Font.PLAIN, 0));
    return tip;
  }

  /** The tint of phase {@code index} on the timeline, so that a card matches its band. */
  static Color tint(StrengthStyle style, int index) {
    return StrengthStyle.alpha(style.accent, new int[] {90, 160, 235}[index]);
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = StrengthStyle.smooth(graphics);
    try {
      int width = getWidth();
      style.paintCard(g, width, getHeight());
      int pad = padding();
      int content = width - pad * 2;
      confidences.clear();

      int y = pad;
      Font title = style.font(Font.BOLD, 3);
      FontMetrics titleMetrics = g.getFontMetrics(title);
      int titleBaseline =
          y
              + (titleHeight() - titleMetrics.getAscent() - titleMetrics.getDescent()) / 2
              + titleMetrics.getAscent();
      double dot = style.px(9);
      StrengthPaint.dot(g, tint(style, phase.index()), pad + dot / 2, y + titleHeight() / 2.0, dot);
      Font rangeFont = style.font(Font.PLAIN, 0);
      int rangeWidth =
          Math.min(content / 2, g.getFontMetrics(rangeFont).stringWidth(range()) + style.px(2));
      int nameX = pad + (int) dot + style.px(8);
      StrengthPaint.text(
          g,
          name(),
          title,
          title.getSize() - 3,
          style.text,
          nameX,
          titleBaseline,
          width - pad - rangeWidth - style.px(10) - nameX,
          StrengthPaint.LEFT);
      StrengthPaint.text(
          g,
          range(),
          rangeFont,
          rangeFont.getSize() - 2,
          style.secondaryText,
          width - pad,
          titleBaseline,
          rangeWidth,
          StrengthPaint.RIGHT);
      y += titleHeight() + style.px(14);

      Graphics2D body = (Graphics2D) g.create();
      if (!phase.played()) {
        body.setComposite(
            java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.45f));
      }
      paintBody(body, pad, y, width, content);
      body.dispose();
    } finally {
      g.dispose();
    }
  }

  /** The side rows, the ruler and the verdict, from {@code top} down. */
  private void paintBody(Graphics2D g, int pad, int top, int width, int content) {
    int y = top;
    int form = confidenceForm(g, pad, content);
    for (int side = 0; side < 2; side++) {
      paintSide(g, side == 0, pad, y, content, form);
      y += rowHeight() + (side == 0 ? style.px(6) : 0);
    }
    y += style.px(12);

    List<StrengthScale.Marker> markers = new ArrayList<>();
    markers.add(
        new StrengthScale.Marker(
            phase.ranked(true) ? phase.black().rankValue : Double.NaN, Boolean.TRUE));
    markers.add(
        new StrengthScale.Marker(
            phase.ranked(false) ? phase.white().rankValue : Double.NaN, Boolean.FALSE));
    scale.paint(g, pad, y, content, markers, false);
    ruler = new Rectangle(pad, y, content, scale.height(this));
    y += scale.height(this) + style.px(14);

    g.setColor(style.divider);
    g.draw(new Line2D.Double(pad, y + 0.5, width - pad, y + 0.5));
    y += 1 + style.px(10);

    Font verdictFont = style.font(Font.PLAIN, 0);
    FontMetrics verdictMetrics = g.getFontMetrics(verdictFont);
    double lead = phase.lead();
    boolean decided = !Double.isNaN(lead) && Math.abs(lead) >= StrengthPhases.EVEN;
    int textX = pad;
    if (decided) {
      double stone = style.px(14);
      StoneGlyph.paint(g, pad, y + (verdictMetrics.getHeight() - stone) / 2.0, stone, lead > 0);
      textX += (int) stone + style.px(7);
    }
    StrengthPaint.text(
        g,
        verdict(),
        verdictFont,
        verdictFont.getSize() - 3,
        decided ? style.text : style.secondaryText,
        textX,
        y + verdictMetrics.getAscent(),
        content - (textX - pad),
        StrengthPaint.LEFT);
  }

  private int nameColumn(Graphics2D g) {
    FontMetrics names = g.getFontMetrics(style.font(Font.PLAIN, 0));
    return Math.max(names.stringWidth(blackName), names.stringWidth(whiteName));
  }

  /**
   * How both rows show the confidence, so that they match: 0 with its label, 1 the value alone, 2
   * only the coloured dot.
   */
  private int confidenceForm(Graphics2D g, int x, int width) {
    FontMetrics ranks = g.getFontMetrics(style.font(Font.BOLD, 2));
    int rankX = x + style.px(18) + style.px(8) + nameColumn(g) + style.px(12);
    int form = 0;
    for (boolean isBlack : new boolean[] {true, false}) {
      if (!phase.ranked(isBlack)) {
        continue;
      }
      String badge = badge(isBlack);
      int used =
          rankX
              + ranks.stringWidth(rankText(isBlack))
              + (badge == null ? 0 : style.px(8) + badgeWidth(g, badge))
              + style.px(10);
      PlayerStrengthEstimator.SideReport side = phase.side(isBlack);
      while (form < 2 && confidenceWidth(g, confidenceText(side, form)) > x + width - used) {
        form++;
      }
    }
    return form;
  }

  private void paintSide(Graphics2D g, boolean isBlack, int x, int top, int width, int form) {
    int height = rowHeight();
    double stone = style.px(18);
    StoneGlyph.paint(g, x, top + (height - stone) / 2.0, stone, isBlack);
    Font nameFont = style.font(Font.PLAIN, 0);
    Font rankFont = style.font(Font.BOLD, 2);
    FontMetrics rankMetrics = g.getFontMetrics(rankFont);
    int baseline =
        top
            + (height - rankMetrics.getAscent() - rankMetrics.getDescent()) / 2
            + rankMetrics.getAscent();
    int nameColumn = nameColumn(g);
    int cursor = x + (int) stone + style.px(8);
    StrengthPaint.text(
        g,
        isBlack ? blackName : whiteName,
        nameFont,
        nameFont.getSize() - 2,
        style.secondaryText,
        cursor,
        baseline,
        nameColumn,
        StrengthPaint.LEFT);
    cursor += nameColumn + style.px(12);

    PlayerStrengthEstimator.SideReport side = phase.side(isBlack);
    boolean ranked = phase.ranked(isBlack);
    String rank = rankText(isBlack);
    String badge = badge(isBlack);
    int badgeWidth = badge == null ? 0 : badgeWidth(g, badge) + style.px(8);
    int right = x + width;
    String confidence = ranked ? confidenceText(side, form) : null;
    int confidenceWidth = confidence == null ? 0 : confidenceWidth(g, confidence) + style.px(10);
    int drawn =
        StrengthPaint.text(
            g,
            rank,
            rankFont,
            rankFont.getSize() - 4,
            ranked ? style.text : style.tertiaryText,
            cursor,
            baseline,
            right - cursor - confidenceWidth,
            StrengthPaint.LEFT);
    cursor += drawn + style.px(8);
    if (badge != null && cursor + badgeWidth - style.px(8) <= right - confidenceWidth) {
      paintBadge(g, badge, cursor, top, height);
    }
    if (confidence != null) {
      paintConfidence(g, side, confidence, right, top, height);
    }
  }

  private int badgeWidth(Graphics2D g, String text) {
    return g.getFontMetrics(style.font(Font.BOLD, -2)).stringWidth(text) + style.px(14);
  }

  private void paintBadge(Graphics2D g, String text, int x, int top, int height) {
    Font font = style.font(Font.BOLD, -2);
    FontMetrics metrics = g.getFontMetrics(font);
    int badgeHeight = style.px(20);
    int badgeWidth = badgeWidth(g, text);
    boolean best = text.equals(bundle.getString("PlayerStrengthEstimate.phase.best"));
    Color tone = best ? style.accent : style.caution;
    double y = top + (height - badgeHeight) / 2.0;
    g.setColor(StrengthStyle.alpha(tone, style.dark ? 60 : 36));
    g.fill(new RoundRectangle2D.Double(x, y, badgeWidth, badgeHeight, badgeHeight, badgeHeight));
    g.setColor(tone);
    g.setFont(font);
    g.drawString(
        text,
        x + style.px(7),
        (float) (y + (badgeHeight - metrics.getAscent() - metrics.getDescent()) / 2.0)
            + metrics.getAscent());
  }

  /** The confidence of {@code side} in {@code form}: with its label, alone, or nothing. */
  private String confidenceText(PlayerStrengthEstimator.SideReport side, int form) {
    String value = StrengthText.confidence(side.confidence, bundle);
    return form == 0
        ? bundle.getString("PlayerStrengthEstimate.confidence") + " " + value
        : form == 1 ? value : "";
  }

  /** The width of the confidence dot and {@code text}; an empty text leaves the dot alone. */
  private int confidenceWidth(Graphics2D g, String text) {
    FontMetrics metrics = g.getFontMetrics(style.font(Font.PLAIN, -1));
    return style.px(6) + (text.isEmpty() ? 0 : style.px(5) + metrics.stringWidth(text));
  }

  private void paintConfidence(
      Graphics2D g,
      PlayerStrengthEstimator.SideReport side,
      String text,
      int right,
      int top,
      int height) {
    Font font = style.font(Font.PLAIN, -1);
    FontMetrics metrics = g.getFontMetrics(font);
    int width = confidenceWidth(g, text);
    int left = right - width;
    Color tone =
        side.confidence == PlayerStrengthEstimator.Confidence.HIGH
            ? style.accent
            : side.confidence == PlayerStrengthEstimator.Confidence.MEDIUM
                ? style.caution
                : style.negative;
    double dot = style.px(6);
    StrengthPaint.dot(g, tone, left + dot / 2, top + height / 2.0, dot);
    g.setFont(font);
    g.setColor(style.tertiaryText);
    int baseline =
        top + (height - metrics.getAscent() - metrics.getDescent()) / 2 + metrics.getAscent();
    g.drawString(text, left + (int) dot + style.px(5), baseline);
    confidences.add(new Rectangle(left - style.px(4), top, width + style.px(4), height));
  }
}
