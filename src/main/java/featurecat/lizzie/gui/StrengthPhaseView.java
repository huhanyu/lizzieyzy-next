package featurecat.lizzie.gui;

import featurecat.lizzie.analysis.PlayerStrengthEstimator;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The phases view: the game as a timeline whose two dividers can be dragged, a card for each phase,
 * and the phase each side may want to look at again in review.
 */
final class StrengthPhaseView extends JPanel {
  private static final long serialVersionUID = 1L;
  private final transient StrengthStyle style;
  private final transient ResourceBundle bundle;
  private final transient PlayerStrengthEstimator.Report report;
  private final int lastMove;
  private final String blackName;
  private final String whiteName;
  private final StrengthPhaseTimeline timeline;
  private final StrengthButton reset;
  private final JPanel cards;
  private final StrengthNote review;
  private transient List<StrengthPhases.Phase> phases;

  /** {@code onCommit} hears the boundaries each time the user is done moving a divider. */
  StrengthPhaseView(
      PlayerStrengthEstimator.Report report,
      int[] bounds,
      int lastMove,
      String blackName,
      String whiteName,
      ResourceBundle bundle,
      StrengthStyle style,
      Consumer<int[]> onCommit) {
    super(new BorderLayout());
    this.style = style;
    this.bundle = bundle;
    this.report = report;
    this.lastMove = lastMove;
    this.blackName = blackName;
    this.whiteName = whiteName;
    setOpaque(false);

    reset =
        new StrengthButton(
            bundle.getString("PlayerStrengthEstimate.phase.reset"),
            StrengthButton.Glyph.NONE,
            style);
    reset.setName("strength-phase-reset");
    timeline =
        new StrengthPhaseTimeline(
            bounds,
            lastMove,
            bundle,
            style,
            this::showPhases,
            committed -> {
              reset.setVisible(!StrengthPhases.isDefault(committed));
              onCommit.accept(committed);
            });
    reset.addActionListener(event -> timeline.reset());
    reset.setVisible(!StrengthPhases.isDefault(bounds));

    JLabel title = new JLabel(bundle.getString("PlayerStrengthEstimate.phase.title"));
    title.setFont(style.font(Font.BOLD, 2));
    title.setForeground(style.text);
    JLabel hint = new JLabel(bundle.getString("PlayerStrengthEstimate.phase.hint"));
    hint.setFont(style.font(Font.PLAIN, -1));
    hint.setForeground(style.tertiaryText);
    hint.setMinimumSize(new Dimension(0, hint.getPreferredSize().height));
    JPanel header = new JPanel();
    header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
    header.setOpaque(false);
    header.add(title);
    header.add(Box.createHorizontalStrut(style.px(12)));
    header.add(hint);
    header.add(Box.createHorizontalGlue());
    header.add(reset);
    header.add(Box.createRigidArea(new Dimension(0, reset.getPreferredSize().height)));

    JPanel card = new Card(new BorderLayout(0, style.px(12)));
    card.setBorder(
        BorderFactory.createEmptyBorder(style.px(16), style.px(20), style.px(14), style.px(20)));
    card.add(header, BorderLayout.NORTH);
    card.add(timeline, BorderLayout.CENTER);

    cards = new JPanel(new GridLayout(1, 3, style.px(16), 0));
    cards.setOpaque(false);
    review = new StrengthNote("", false, style);
    StrengthParagraph caveat =
        new StrengthParagraph(
            bundle.getString("PlayerStrengthEstimate.phase.caveat"),
            style.font(Font.PLAIN, -1),
            style.tertiaryText,
            StrengthPaint.LEFT,
            2,
            style);
    JPanel caveatRow = new JPanel(new BorderLayout());
    caveatRow.setOpaque(false);
    caveatRow.setBorder(BorderFactory.createEmptyBorder(0, style.px(23), 0, 0));
    caveatRow.add(caveat, BorderLayout.CENTER);
    JPanel notes = new JPanel(new BorderLayout(0, style.px(6)));
    notes.setOpaque(false);
    notes.add(review, BorderLayout.NORTH);
    notes.add(caveatRow, BorderLayout.CENTER);
    JPanel column = new JPanel(new BorderLayout(0, style.px(14)));
    column.setOpaque(false);
    column.add(card, BorderLayout.NORTH);
    column.add(cards, BorderLayout.CENTER);
    column.add(notes, BorderLayout.SOUTH);
    add(column, BorderLayout.NORTH);
    showPhases(timeline.phaseBounds());
  }

  StrengthPhaseTimeline timeline() {
    return timeline;
  }

  StrengthButton resetButton() {
    return reset;
  }

  List<StrengthPhases.Phase> phases() {
    return phases;
  }

  StrengthPhaseCard card(int index) {
    return (StrengthPhaseCard) cards.getComponent(index);
  }

  StrengthNote reviewNote() {
    return review;
  }

  private void showPhases(int[] bounds) {
    phases = StrengthPhases.split(report, bounds, lastMove);
    boolean humanStyle = report.model != null && report.model.isHumanSl40();
    cards.removeAll();
    for (int i = 0; i < phases.size(); i++) {
      cards.add(new StrengthPhaseCard(phases, i, humanStyle, blackName, whiteName, bundle, style));
    }
    String text = review(phases, blackName, whiteName, bundle);
    review.setText(text == null ? "" : text);
    review.setVisible(text != null);
    revalidate();
    repaint();
  }

  /**
   * The phase each side may want to look at again: its weakest one when it is clearly below its
   * best; that both played evenly when neither is; null when there are too few ranked phases.
   */
  static String review(
      List<StrengthPhases.Phase> phases,
      String blackName,
      String whiteName,
      ResourceBundle bundle) {
    List<String> items = new ArrayList<>();
    List<Integer> weakest = new ArrayList<>();
    boolean comparable = true;
    for (boolean isBlack : new boolean[] {true, false}) {
      int ranked = 0;
      for (StrengthPhases.Phase phase : phases) {
        ranked += phase.ranked(isBlack) ? 1 : 0;
      }
      comparable &= ranked >= 2;
      if (StrengthPhases.worthReviewing(phases, isBlack)) {
        int phase = StrengthPhases.extreme(phases, isBlack, false);
        weakest.add(phase);
        items.add(
            MessageFormat.format(
                bundle.getString("PlayerStrengthEstimate.phase.reviewItem"),
                isBlack ? blackName : whiteName,
                bundle.getString("PlayerStrengthEstimate.phase." + phases.get(phase).name())));
      }
    }
    if (items.isEmpty()) {
      return comparable ? bundle.getString("PlayerStrengthEstimate.phase.steady") : null;
    }
    if (weakest.size() == 2 && weakest.get(0).equals(weakest.get(1))) {
      return MessageFormat.format(
          bundle.getString("PlayerStrengthEstimate.phase.reviewSame"),
          bundle.getString("PlayerStrengthEstimate.phase." + phases.get(weakest.get(0)).name()));
    }
    return items.size() == 1
        ? MessageFormat.format(
            bundle.getString("PlayerStrengthEstimate.phase.review"), items.get(0))
        : MessageFormat.format(
            bundle.getString("PlayerStrengthEstimate.phase.reviewBoth"),
            items.get(0),
            items.get(1));
  }

  /** A card behind the timeline. */
  private final class Card extends JPanel {
    private static final long serialVersionUID = 1L;

    private Card(BorderLayout layout) {
      super(layout);
      setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      Graphics2D g = StrengthStyle.smooth(graphics);
      try {
        style.paintCard(g, getWidth(), getHeight());
      } finally {
        g.dispose();
      }
    }
  }
}
