package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.training.HumanSlTrainingConfig;
import featurecat.lizzie.training.OpponentPreset;
import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;

class HumanSlGameControllerLabelTest {
  @Test
  void rankOpponentUsesLocalizedDanLabel() {
    ResourceBundle previous = Lizzie.resourceBundle;
    try {
      Lizzie.resourceBundle =
          ResourceBundle.getBundle("l10n.DisplayStrings", Locale.SIMPLIFIED_CHINESE);
      HumanSlGameController controller =
          new HumanSlGameController(
              null,
              HumanSlTrainingConfig.builder()
                  .opponentPreset(OpponentPreset.RANK)
                  .rank(3, true)
                  .build(),
              null);

      assertEquals("3段", controller.opponentLabel());
    } finally {
      Lizzie.resourceBundle = previous;
    }
  }

  @Test
  void rankOpponentUsesLocalizedKyuLabel() {
    ResourceBundle previous = Lizzie.resourceBundle;
    try {
      Lizzie.resourceBundle = ResourceBundle.getBundle("l10n.DisplayStrings", Locale.US);
      HumanSlGameController controller =
          new HumanSlGameController(
              null,
              HumanSlTrainingConfig.builder()
                  .opponentPreset(OpponentPreset.RANK)
                  .rank(12, false)
                  .build(),
              null);

      assertEquals("12 kyu", controller.opponentLabel());
    } finally {
      Lizzie.resourceBundle = previous;
    }
  }
}
