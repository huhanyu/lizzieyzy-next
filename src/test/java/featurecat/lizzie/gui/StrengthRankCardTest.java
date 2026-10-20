package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.AppLocale;
import featurecat.lizzie.analysis.PlayerStrengthEstimator;
import featurecat.lizzie.rules.Stone;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;

class StrengthRankCardTest {
  private static final ResourceBundle BUNDLE = AppLocale.SIMPLIFIED_CHINESE.loadBundle();
  private static final StrengthStyle STYLE = new StrengthStyle(false, false, "Dialog", 12);

  private static PlayerStrengthEstimator.SideReport ninthDan() throws Exception {
    return StrengthFixtures.side(
        StrengthFixtures.moves(Stone.BLACK, 1, 20, PlayerStrengthEstimator.MoveCategory.GREAT),
        PlayerStrengthEstimator.StrengthModel.XGBOOST20TUN,
        9.04,
        0.64,
        0.88,
        "9d",
        PlayerStrengthEstimator.Confidence.HIGH);
  }

  @Test
  void rankNumberIsDrawnWithKerningSoMacOsKeepsItsDigits() throws Exception {
    StrengthRankCard card = new StrengthRankCard("Black", true, ninthDan(), BUNDLE, STYLE);
    assertEquals(
        TextAttribute.KERNING_ON, card.numberFont().getAttributes().get(TextAttribute.KERNING));
  }

  @Test
  void tooltipAndScreenReaderGetTheRankAndTheWholeScale() throws Exception {
    StrengthRankCard card = new StrengthRankCard("\u9ed1\u68cb", true, ninthDan(), BUNDLE, STYLE);
    String rank = "\u4e1a\u4f599.0\u6bb5";
    assertTrue(card.getToolTipText().contains(rank));
    assertTrue(card.getToolTipText().contains("&lt;10"));
    assertFalse(card.getToolTipText().contains("..."));
    assertEquals("\u9ed1\u68cb " + rank, card.getAccessibleContext().getAccessibleName());
    String description = card.getAccessibleContext().getAccessibleDescription();
    for (String tier : new String[] {"12+", "11+", "10+", "<10"}) {
      assertTrue(description.contains(tier), description);
    }
  }

  @Test
  void cardWithoutMovesStillPaints() throws Exception {
    StrengthRankCard card =
        new StrengthRankCard(
            "\u767d\u68cb", false, StrengthFixtures.emptyReport().white, BUNDLE, STYLE);
    card.setSize(card.getPreferredSize());
    card.paint(new BufferedImage(500, 300, BufferedImage.TYPE_INT_ARGB).createGraphics());
    assertEquals("\u767d\u68cb -", card.getAccessibleContext().getAccessibleName());
  }

  @Test
  void largeRankFitsANarrowCard() throws Exception {
    PlayerStrengthEstimator.SideReport ai =
        StrengthFixtures.side(
            StrengthFixtures.moves(
                Stone.WHITE, 2, 20, PlayerStrengthEstimator.MoveCategory.EXCELLENT),
            PlayerStrengthEstimator.StrengthModel.XGBOOST20TUN,
            12.6,
            0.9,
            0.99,
            "12d AI",
            PlayerStrengthEstimator.Confidence.HIGH);
    StrengthRankCard card =
        new StrengthRankCard("White", false, ai, AppLocale.THAI.loadBundle(), STYLE);
    card.setSize(card.getMinimumSize());
    card.paint(new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB).createGraphics());
    assertTrue(card.getMinimumSize().height <= card.getPreferredSize().height);
  }
}
