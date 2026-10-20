package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.AppLocale;
import featurecat.lizzie.analysis.PlayerStrengthEstimator;
import featurecat.lizzie.rules.Stone;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;

class StrengthSegmentChartTest {
  private static final StrengthStyle STYLE = new StrengthStyle(false, false, "Dialog", 12);

  private static StrengthSegmentChart chart(ResourceBundle bundle) throws Exception {
    PlayerStrengthEstimator.Report report =
        StrengthFixtures.report(
            StrengthFixtures.side(
                StrengthFixtures.moves(
                    Stone.BLACK, 1, 40, PlayerStrengthEstimator.MoveCategory.EXCELLENT),
                1.0,
                1.0),
            StrengthFixtures.side(
                StrengthFixtures.moves(
                    Stone.WHITE, 2, 40, PlayerStrengthEstimator.MoveCategory.BLUNDER),
                0.0,
                0.0),
            PlayerStrengthEstimator.StrengthModel.XGBOOST20TUN);
    StrengthSegmentChart chart =
        new StrengthSegmentChart(
            report,
            new String[] {
              bundle.getString("SidebarHeader.black"), bundle.getString("SidebarHeader.white")
            },
            bundle,
            STYLE);
    chart.setSize(chart.getPreferredSize());
    chart.paint(new BufferedImage(1000, 600, BufferedImage.TYPE_INT_ARGB).createGraphics());
    return chart;
  }

  @Test
  void strongStretchesAreLabelledWithTheirLocalizedRank() throws Exception {
    ResourceBundle bundle = AppLocale.SIMPLIFIED_CHINESE.loadBundle();
    List<StrengthSegmentChart.Stretch> stretches = chart(bundle).stretches();
    assertFalse(stretches.isEmpty(), "perfect play is labelled");
    for (StrengthSegmentChart.Stretch stretch : stretches) {
      assertTrue(stretch.black(), "the blundering side has no strong stretch");
      assertEquals(
          StrengthText.band(stretch.segment().report().strengthBand, bundle), stretch.label());
      assertFalse(stretch.label().contains("Fox"));
    }
  }

  @Test
  void hoveringAStretchTellsWhichMovesItCovers() throws Exception {
    ResourceBundle bundle = AppLocale.ENGLISH.loadBundle();
    StrengthSegmentChart chart = chart(bundle);
    StrengthSegmentChart.Stretch stretch = chart.stretches().get(0);
    Rectangle bounds = stretch.bounds();
    String tooltip =
        chart.getToolTipText(
            new MouseEvent(
                chart,
                MouseEvent.MOUSE_MOVED,
                0L,
                0,
                bounds.x + bounds.width / 2,
                bounds.y + bounds.height / 2,
                0,
                false));
    assertEquals(
        String.format(
            bundle.getString("PlayerStrengthEstimate.match.rankTooltip"),
            "Black",
            stretch.segment().firstMove(),
            stretch.segment().lastMove(),
            stretch.label()),
        tooltip);
    assertNull(
        chart.getToolTipText(new MouseEvent(chart, MouseEvent.MOUSE_MOVED, 0L, 0, 2, 2, 0, false)));
  }
}
