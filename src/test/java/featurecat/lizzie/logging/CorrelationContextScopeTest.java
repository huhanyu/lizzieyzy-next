package featurecat.lizzie.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.MDC;

class CorrelationContextScopeTest {
  @TempDir Path tempDir;

  @AfterEach
  void clearMdc() {
    LoggingRuntime.resetForTests();
    MDC.clear();
  }

  @Test
  void nestedScopeRestoresTheImmediatelyEnclosingValues() {
    MDC.put(CorrelationContext.ENGINE_ID, "eng-caller");
    MDC.put(CorrelationContext.COMMAND_ID, "cmd-caller");

    EngineObservation.inContext(
        "eng-outer",
        "cmd-outer",
        () -> {
          assertEquals("eng-outer", MDC.get(CorrelationContext.ENGINE_ID));
          assertEquals("cmd-outer", MDC.get(CorrelationContext.COMMAND_ID));
          EngineObservation.inContext(
              "eng-inner",
              null,
              () -> {
                assertEquals("eng-inner", MDC.get(CorrelationContext.ENGINE_ID));
                assertNull(MDC.get(CorrelationContext.COMMAND_ID));
              });
          assertEquals("eng-outer", MDC.get(CorrelationContext.ENGINE_ID));
          assertEquals("cmd-outer", MDC.get(CorrelationContext.COMMAND_ID));
        });

    assertEquals("eng-caller", MDC.get(CorrelationContext.ENGINE_ID));
    assertEquals("cmd-caller", MDC.get(CorrelationContext.COMMAND_ID));
  }

  @Test
  void observationScopesRestoreAllCallerValuesAfterNestedFailure() {
    MDC.put(CorrelationContext.APP_SESSION, "app-caller");
    MDC.put(CorrelationContext.ENGINE_ID, "eng-caller");
    MDC.put(CorrelationContext.COMMAND_ID, "cmd-caller");
    MDC.put(CorrelationContext.REQUEST_ID, "req-caller");
    MDC.put(CorrelationContext.TRACE_SESSION, "trace-caller");
    MDC.put(CorrelationContext.GMA_ID, "gma-caller");
    MDC.put(CorrelationContext.SYNC_SESSION, "yike-caller");

    assertThrows(
        MarkerFailure.class,
        () ->
            EngineObservation.inContext(
                "eng-engine",
                "cmd-engine",
                () ->
                    NetworkObservation.inContext(
                        "req-network",
                        () ->
                            ReadBoardObservation.inContext(
                                "eng-readboard",
                                "gma-readboard",
                                "yike-readboard",
                                () -> {
                                  assertEquals(
                                      "eng-readboard", MDC.get(CorrelationContext.ENGINE_ID));
                                  assertEquals(
                                      "cmd-engine", MDC.get(CorrelationContext.COMMAND_ID));
                                  assertEquals(
                                      "req-network", MDC.get(CorrelationContext.REQUEST_ID));
                                  assertEquals(
                                      "trace-caller", MDC.get(CorrelationContext.TRACE_SESSION));
                                  assertEquals(
                                      "gma-readboard", MDC.get(CorrelationContext.GMA_ID));
                                  assertEquals(
                                      "yike-readboard",
                                      MDC.get(CorrelationContext.SYNC_SESSION));
                                  throw new MarkerFailure();
                                }))));

    assertEquals("app-caller", MDC.get(CorrelationContext.APP_SESSION));
    assertEquals("eng-caller", MDC.get(CorrelationContext.ENGINE_ID));
    assertEquals("cmd-caller", MDC.get(CorrelationContext.COMMAND_ID));
    assertEquals("req-caller", MDC.get(CorrelationContext.REQUEST_ID));
    assertEquals("trace-caller", MDC.get(CorrelationContext.TRACE_SESSION));
    assertEquals("gma-caller", MDC.get(CorrelationContext.GMA_ID));
    assertEquals("yike-caller", MDC.get(CorrelationContext.SYNC_SESSION));
  }

  @Test
  void activeTraceSessionTemporarilyOverridesAndThenRestoresCallerTrace() {
    LoggingRuntime runtime =
        LoggingRuntime.initialize(
            new WorkDirectoryResolution(tempDir, List.of()),
            new LoggingLimits(64, 32, 32, 32, 7, 1_000_000, 256_000));
    runtime.startFullTrace(EnumSet.of(TraceScope.ENGINE_GTP));
    String runtimeTrace = runtime.currentTraceSessionId();
    MDC.put(CorrelationContext.TRACE_SESSION, "trace-caller");

    EngineObservation.inContext(
        "eng-runtime",
        null,
        () -> assertEquals(runtimeTrace, MDC.get(CorrelationContext.TRACE_SESSION)));

    assertEquals("trace-caller", MDC.get(CorrelationContext.TRACE_SESSION));
  }

  private static final class MarkerFailure extends RuntimeException {}
}
