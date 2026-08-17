package featurecat.lizzie.training;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HumanSlTrainingConfigTest {

  @Test
  void defaultTrainingUsesThreeDanHumanProfile() {
    HumanSlTrainingConfig config = HumanSlTrainingConfig.builder().build();

    assertEquals("rank_3d", config.humanSlProfile());
    assertEquals(64, config.analysisVisits());
    assertEquals(1, config.rootSymmetries());
  }

  @Test
  void rankProfilesStayWithinOfficialTwentyKyuToNineDanRange() {
    HumanSlTrainingConfig weakest =
        HumanSlTrainingConfig.builder().rank(99, false).build();
    HumanSlTrainingConfig strongest =
        HumanSlTrainingConfig.builder().rank(99, true).build();

    assertEquals("rank_20k", weakest.humanSlProfile());
    assertEquals("rank_9d", strongest.humanSlProfile());
  }

  @Test
  void modernProPresetUsesProYearAndHigherQualityBudget() {
    HumanSlTrainingConfig config =
        HumanSlTrainingConfig.builder().opponentPreset(OpponentPreset.MODERN_PRO).build();

    assertEquals("proyear_2023", config.humanSlProfile());
    assertEquals(128, config.analysisVisits());
    assertEquals(2, config.rootSymmetries());
  }

  @Test
  void onlineNineDanPresetMapsToOfficialRankProfile() {
    HumanSlTrainingConfig config =
        HumanSlTrainingConfig.builder().opponentPreset(OpponentPreset.ONLINE_9D).build();

    assertEquals("rank_9d", config.humanSlProfile());
    assertEquals(64, config.analysisVisits());
  }
}
