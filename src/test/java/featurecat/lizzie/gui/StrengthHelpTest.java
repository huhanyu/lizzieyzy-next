package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.AppLocale;
import featurecat.lizzie.analysis.PlayerStrengthEstimator;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Every figure of the strength dialog explains itself when the pointer rests on it. */
class StrengthHelpTest {
  private static final ResourceBundle BUNDLE = AppLocale.SIMPLIFIED_CHINESE.loadBundle();
  private static final StrengthStyle STYLE = new StrengthStyle(false, false, "Dialog", 12);

  /** A dashboard that is only looked at. */
  private static final StrengthDashboard.Actions NO_ACTIONS =
      new StrengthDashboard.Actions() {
        @Override
        public void chooseModel(PlayerStrengthEstimator.StrengthModel model) {}

        @Override
        public void importModel() {}

        @Override
        public void showDetails(int view) {}

        @Override
        public boolean setBackgroundEnabled(boolean enabled) {
          return enabled;
        }

        @Override
        public void prepareNow() {}

        @Override
        public void analyzeGame() {}

        @Override
        public void savePhaseBounds(int[] bounds) {}
      };

  private static String help(String key) {
    return BUNDLE.getString("PlayerStrengthEstimate.help." + key);
  }

  /** The text of a tooltip without its markup and line breaks. */
  private static String plain(String tooltip) {
    return tooltip == null ? null : squeezed(tooltip.replaceAll("<[^>]+>", ""));
  }

  private static String squeezed(String text) {
    return text.replaceAll("\\s", "");
  }

  private static MouseEvent at(JComponent component, int x, int y) {
    return new MouseEvent(component, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false);
  }

  private static void paint(JComponent component) {
    BufferedImage image =
        new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    try {
      component.paint(g);
    } finally {
      g.dispose();
    }
  }

  @Test
  void compareRowsExplainTheirFigure() throws Exception {
    PlayerStrengthEstimator.Report report = StrengthFixtures.readyReport();
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDashboard dashboard =
              new StrengthDashboard(
                  report,
                  new PlayerStrengthEstimator.StrengthModel[] {report.model},
                  false,
                  StrengthPhases.DEFAULT_BOUNDS,
                  new StrengthDashboard.Context(true, false, 6, 6, true),
                  STYLE,
                  BUNDLE,
                  NO_ACTIONS);
          StrengthCompareCard card = new StrengthCompareCard(List.of(), STYLE);
          for (java.awt.Component part : dashboard.currentView().getComponents()) {
            if (part instanceof StrengthCompareCard found) {
              card = found;
            }
          }
          card.setSize(STYLE.px(1000), STYLE.px(420));
          List<StrengthCompareCard.Row> rows = card.rows();
          assertEquals(5, rows.size());
          for (int i = 0; i < rows.size(); i++) {
            assertEquals(
                squeezed(rows.get(i).help()),
                plain(card.getToolTipText(at(card, card.getWidth() / 2, card.rowCenter(i)))),
                rows.get(i).label());
          }
          assertNull(card.getToolTipText(at(card, 10, 1)), "nothing above the first row");
        });
  }

  @Test
  void accuracyRingAndFiguresExplainThemselves() throws Exception {
    PlayerStrengthEstimator.Report report = StrengthFixtures.readyReport();
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthAccuracyCard card =
              new StrengthAccuracyCard("Black", true, report.black, BUNDLE, STYLE);
          card.setSize(STYLE.px(520), card.getPreferredSize().height);
          assertEquals(help("overallMatch"), card.helpAt(STYLE.px(40)));
          Set<String> figures = new HashSet<>();
          for (int x = 0; x < card.getWidth(); x++) {
            String text = card.helpAt(x);
            if (text != null) {
              figures.add(text);
            }
          }
          assertTrue(figures.contains(help("firstChoice")), figures.toString());
          assertTrue(figures.contains(help("goodMoveRate")), figures.toString());
          assertTrue(figures.contains(help("mistakeRate")), figures.toString());
          assertTrue(figures.contains(help("moves")), figures.toString());
        });
  }

  @Test
  void rankCardExplainsTheRankAndTheConfidenceChip() throws Exception {
    PlayerStrengthEstimator.Report report = StrengthFixtures.readyReport();
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthRankCard card = new StrengthRankCard("Black", true, report.black, BUNDLE, STYLE);
          card.setSize(STYLE.px(520), card.getPreferredSize().height);
          paint(card);
          Rectangle chip = card.chipBounds();
          assertFalse(chip.isEmpty(), "the chip is drawn");
          assertEquals(
              squeezed(help("confidence")),
              plain(
                  card.getToolTipText(at(card, (int) chip.getCenterX(), (int) chip.getCenterY()))));
          String rank = plain(card.getToolTipText(at(card, 2, card.getHeight() - 2)));
          assertTrue(rank.startsWith(squeezed(help("rank"))), rank);
        });
  }

  @Test
  void detailRowsExplainEveryFigureThatHasAMeaning() throws Exception {
    PlayerStrengthEstimator.Report report = StrengthFixtures.readyReport();
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDetailTable table =
              (StrengthDetailTable)
                  StrengthDialog.detailPanel(report, false, STYLE, BUNDLE).getComponent(0);
          table.setSize(STYLE.px(900), table.getPreferredSize().height);
          Set<String> expected = new HashSet<>();
          for (String[] row : table.rows()) {
            if (row[3] != null) {
              expected.add(squeezed(row[3]));
            }
          }
          assertTrue(expected.contains(squeezed(help("band"))));
          assertTrue(expected.contains(squeezed(help("rank"))));
          Set<String> shown = new HashSet<>();
          for (int y = 0; y < table.getHeight(); y++) {
            String text = plain(table.getToolTipText(at(table, STYLE.px(40), y)));
            if (text != null) {
              shown.add(text);
            }
          }
          assertEquals(expected, shown);
        });
  }

  @Test
  void primaryButtonIsFilledWithTheAccent() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthButton button = StrengthButton.primary("Go", STYLE);
          assertTrue(button.isPrimary());
          button.setSize(button.getPreferredSize());
          BufferedImage image =
              new BufferedImage(button.getWidth(), button.getHeight(), BufferedImage.TYPE_INT_ARGB);
          Graphics2D g = image.createGraphics();
          try {
            button.paint(g);
          } finally {
            g.dispose();
          }
          Color fill = new Color(image.getRGB(STYLE.px(8), button.getHeight() / 2), true);
          assertEquals(STYLE.accent.getRGB(), fill.getRGB());
          assertEquals(STYLE.onAccent, button.getForeground());
          assertFalse(
              new StrengthButton("Go", StrengthButton.Glyph.NONE, STYLE).isPrimary(),
              "buttons are plain unless asked");
        });
  }
}
