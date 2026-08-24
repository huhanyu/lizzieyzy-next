package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import featurecat.lizzie.analysis.Leelaz;
import org.junit.jupiter.api.Test;

class LizzieFrameLoadingTextTest {
  @Test
  void missingOrFailedEngineUsesTheFailureStatusWithoutDereferencingNull() throws Exception {
    assertEquals("LizzieFrame.display.down", LizzieFrame.loadingTextResourceKey(null));

    Leelaz failed = new Leelaz("");
    failed.isDownWithError = true;
    assertEquals("LizzieFrame.display.down", LizzieFrame.loadingTextResourceKey(failed));
  }

  @Test
  void distinguishesTuningFromOrdinaryLoading() throws Exception {
    Leelaz loading = new Leelaz("");
    assertEquals("LizzieFrame.display.loading", LizzieFrame.loadingTextResourceKey(loading));

    loading.isTuning = true;
    assertEquals("LizzieFrame.display.tuning", LizzieFrame.loadingTextResourceKey(loading));
  }
}
