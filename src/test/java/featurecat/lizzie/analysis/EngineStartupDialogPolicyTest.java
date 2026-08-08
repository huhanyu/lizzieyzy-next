package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.EngineStartupStatus;
import featurecat.lizzie.Lizzie;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EngineStartupDialogPolicyTest {
  @TempDir Path tempDir;

  @Test
  void primaryEngineFailuresStayInTheAccessibleRepairStatus() {
    assertFalse(Leelaz.shouldOpenInteractiveDiagnostic(true, false));
    assertFalse(Leelaz.shouldOpenInteractiveDiagnostic(true, true));
  }

  @Test
  void secondaryEngineDiagnosticsRemainAvailableOutsideFirstLaunch() {
    assertTrue(Leelaz.shouldOpenInteractiveDiagnostic(false, false));
    assertFalse(Leelaz.shouldOpenInteractiveDiagnostic(false, true));
  }

  @Test
  void backgroundAnalysisPreloadDoesNotAnnounceGeneratedConfig() {
    assertFalse(AnalysisEngine.shouldShowGeneratedConfigNotice(true, true));
    assertFalse(AnalysisEngine.shouldShowGeneratedConfigNotice(false, false));
    assertTrue(AnalysisEngine.shouldShowGeneratedConfigNotice(false, true));
  }

  @Test
  void secondaryBundledStartupStageDoesNotPublishPrimaryStatus() throws Exception {
    Leelaz previousPrimary = Lizzie.leelaz;
    Leelaz primary = new Leelaz("");
    Leelaz secondary = new Leelaz("engines/katago/windows-x64-opencl/katago.exe");
    try {
      Lizzie.leelaz = primary;
      Lizzie.engineStartupStatus.ready();

      invokeBundledStartupStage(secondary);

      assertEquals(EngineStartupStatus.State.READY, Lizzie.engineStartupStatus.snapshot().state);
    } finally {
      Lizzie.leelaz = previousPrimary;
      Lizzie.engineStartupStatus.ready();
    }
  }

  @Test
  void primaryBundledStartupStagePublishesCheckingStatus() throws Exception {
    Leelaz previousPrimary = Lizzie.leelaz;
    Leelaz primary = new Leelaz("engines/katago/windows-x64-opencl/katago.exe");
    try {
      Lizzie.leelaz = primary;
      Lizzie.engineStartupStatus.ready();

      invokeBundledStartupStage(primary);

      assertEquals(EngineStartupStatus.State.CHECKING, Lizzie.engineStartupStatus.snapshot().state);
    } finally {
      Lizzie.leelaz = previousPrimary;
      Lizzie.engineStartupStatus.ready();
    }
  }

  @Test
  void secondaryOpenClCompatibilityStageDoesNotPublishPrimaryStatus() throws Exception {
    Leelaz previousPrimary = Lizzie.leelaz;
    Leelaz primary = new Leelaz("");
    Leelaz secondary = new Leelaz("engines/katago/windows-x64-opencl/katago.exe");
    try {
      Lizzie.leelaz = primary;
      Lizzie.engineStartupStatus.ready();

      invokeBundledStartupStatus(
          secondary,
          "BundledEngineStartup.status.openclCompatibility",
          "Using stable NVIDIA OpenCL compatibility mode...");

      assertEquals(EngineStartupStatus.State.READY, Lizzie.engineStartupStatus.snapshot().state);
    } finally {
      Lizzie.leelaz = previousPrimary;
      Lizzie.engineStartupStatus.ready();
    }
  }

  @Test
  void missingExecutableWeightOrConfigUsesNotReadyRepairState() throws Exception {
    Path executable = tempDir.resolve("katago.exe");
    Path model = tempDir.resolve("model.bin.gz");
    Path config = tempDir.resolve("gtp.cfg");

    assertTrue(
        Leelaz.hasMissingLocalStartupAsset(
            List.of(executable.toString(), "gtp", "-model", model.toString()), false, false));

    Files.writeString(executable, "stub");
    Files.writeString(model, "stub");
    assertTrue(
        Leelaz.hasMissingLocalStartupAsset(
            List.of(
                executable.toString(),
                "gtp",
                "-model",
                model.toString(),
                "-config",
                config.toString()),
            false,
            false));

    Files.writeString(config, "stub");
    assertFalse(
        Leelaz.hasMissingLocalStartupAsset(
            List.of(
                executable.toString(),
                "gtp",
                "-model",
                model.toString(),
                "-config",
                config.toString()),
            false,
            false));
    assertFalse(
        Leelaz.hasMissingLocalStartupAsset(
            List.of(executable.toString(), "gtp"), true, false));
  }

  private static void invokeBundledStartupStage(Leelaz engine) throws Exception {
    Method method =
        Leelaz.class.getDeclaredMethod(
            "updateBundledStartupStage",
            Path.class,
            int.class,
            String.class,
            String.class,
            String.class,
            String.class);
    method.setAccessible(true);
    method.invoke(
        engine,
        Path.of("engines/katago/windows-x64-opencl/katago.exe"),
        1,
        "BundledEngineStartup.status.checking",
        "Checking built-in engine files...",
        "BundledEngineStartup.hint",
        "First launch may take a little longer.");
  }

  private static void invokeBundledStartupStatus(
      Leelaz engine, String statusKey, String statusFallback) throws Exception {
    Method method =
        Leelaz.class.getDeclaredMethod(
            "publishBundledStartupStatus", String.class, String.class);
    method.setAccessible(true);
    method.invoke(engine, statusKey, statusFallback);
  }

}
