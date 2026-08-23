package featurecat.lizzie.logging;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;

public final class CorrelationContext {
  public static final String APP_SESSION = "lizzie.appSession";
  public static final String ENGINE_ID = "lizzie.engineId";
  public static final String COMMAND_ID = "lizzie.commandId";
  public static final String REQUEST_ID = "lizzie.requestId";
  public static final String TRACE_SESSION = "lizzie.traceSession";
  public static final String GMA_ID = "lizzie.gmaId";
  public static final String SYNC_SESSION = "lizzie.syncSession";

  private CorrelationContext() {}

  /** Opens a key-local MDC scope that restores every touched value on close. */
  public static Scope openScope() {
    return new Scope();
  }

  public static void installAppSession(String applicationLogSessionId) {
    put(APP_SESSION, applicationLogSessionId);
  }

  public static void installEngine(String engineId) {
    put(ENGINE_ID, engineId);
  }

  public static void installCommand(String commandId) {
    put(COMMAND_ID, commandId);
  }

  public static void installRequest(String requestId) {
    put(REQUEST_ID, requestId);
  }

  public static void installTraceSession(String traceSessionId) {
    put(TRACE_SESSION, traceSessionId);
  }

  public static void installGma(String gmaId) {
    put(GMA_ID, gmaId);
  }

  public static void installSyncSession(String sessionId) {
    put(SYNC_SESSION, sessionId);
  }

  public static void clearEngine() {
    MDC.remove(ENGINE_ID);
  }

  public static void clearCommand() {
    MDC.remove(COMMAND_ID);
  }

  public static void clearRequest() {
    MDC.remove(REQUEST_ID);
  }

  public static void clearTraceSession() {
    MDC.remove(TRACE_SESSION);
  }

  public static void clearGma() {
    MDC.remove(GMA_ID);
  }

  public static void clearSyncSession() {
    MDC.remove(SYNC_SESSION);
  }

  public static void clearAsync() {
    MDC.remove(ENGINE_ID);
    MDC.remove(COMMAND_ID);
    MDC.remove(REQUEST_ID);
    MDC.remove(TRACE_SESSION);
    MDC.remove(GMA_ID);
    MDC.remove(SYNC_SESSION);
  }

  private static void put(String key, String value) {
    if (value == null || value.isEmpty()) {
      MDC.remove(key);
    } else {
      MDC.put(key, value);
    }
  }

  public static final class Scope implements AutoCloseable {
    private final LinkedHashMap<String, String> previousValues = new LinkedHashMap<>();
    private boolean closed;

    private Scope() {}

    public Scope installEngine(String engineId) {
      return install(ENGINE_ID, engineId);
    }

    public Scope installCommand(String commandId) {
      return install(COMMAND_ID, commandId);
    }

    public Scope installRequest(String requestId) {
      return install(REQUEST_ID, requestId);
    }

    public Scope installTraceSession(String traceSessionId) {
      return install(TRACE_SESSION, traceSessionId);
    }

    public Scope installGma(String gmaId) {
      return install(GMA_ID, gmaId);
    }

    public Scope installSyncSession(String sessionId) {
      return install(SYNC_SESSION, sessionId);
    }

    private Scope install(String key, String value) {
      if (closed) {
        throw new IllegalStateException("Correlation context scope is already closed");
      }
      if (!previousValues.containsKey(key)) {
        previousValues.put(key, MDC.get(key));
      }
      put(key, value);
      return this;
    }

    @Override
    public void close() {
      if (closed) {
        return;
      }
      closed = true;
      List<Map.Entry<String, String>> entries =
          new ArrayList<>(previousValues.entrySet());
      for (int index = entries.size() - 1; index >= 0; index--) {
        Map.Entry<String, String> entry = entries.get(index);
        put(entry.getKey(), entry.getValue());
      }
    }
  }
}
