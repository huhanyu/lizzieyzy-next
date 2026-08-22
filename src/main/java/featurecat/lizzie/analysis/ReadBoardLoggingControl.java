package featurecat.lizzie.analysis;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

public final class ReadBoardLoggingControl {
  public enum Status {
    STARTING,
    CAPABILITY_READY,
    APPLYING,
    OBSERVED,
    UNKNOWN,
    LEGACY_UNCONFIRMED
  }

  public enum Presentation {
    OFF,
    ON,
    ON_STORAGE_DEGRADED,
    ON_STORAGE_UNAVAILABLE,
    NOT_APPLIED,
    UNKNOWN,
    LEGACY_UNCONFIRMED
  }

  public static final class Desired {
    public final boolean diagnostics;
    public final boolean capture;
    public final boolean trace;

    public Desired(boolean diagnostics, boolean capture, boolean trace) {
      this.diagnostics = diagnostics;
      this.capture = capture;
      this.trace = trace;
    }

    public static Desired launchDefaults(boolean diagnosticsPolicy) {
      return new Desired(diagnosticsPolicy, false, false);
    }
  }

  private final boolean contractLaunch;
  private final SecureRandom random = new SecureRandom();
  private ReadBoardLoggingProtocol.RequestGate gate = new ReadBoardLoggingProtocol.RequestGate();
  private Desired desired;
  private ReadBoardLoggingProtocol.Observed observed;
  private String processSessionId;
  private Status status;

  public ReadBoardLoggingControl(Desired desired, boolean contractLaunch) {
    this.desired = desired == null ? Desired.launchDefaults(false) : desired;
    this.contractLaunch = contractLaunch;
    this.status = contractLaunch ? Status.STARTING : Status.LEGACY_UNCONFIRMED;
  }

  public static String readBoardLogDirectory(Path logsDirectory) {
    if (logsDirectory == null) {
      return "";
    }
    return logsDirectory.resolve("readboard").toAbsolutePath().toString();
  }

  public static List<String> appendNamedLoggingArguments(
      List<String> positional, String logDir, String hostSessionId, boolean diagnostics) {
    if (positional == null || positional.size() < 7) {
      return positional;
    }
    List<String> head = new ArrayList<String>(positional.subList(0, 7));
    if (!ReadBoardLoggingProtocol.isAbsoluteLogDirectory(logDir)
        || !ReadBoardLoggingProtocol.isOpaqueId(hostSessionId)) {
      return Collections.unmodifiableList(head);
    }
    return ReadBoardLoggingProtocol.appendLaunchArguments(
        head, logDir, hostSessionId, diagnostics, false);
  }

  public boolean isContractLaunch() {
    return contractLaunch;
  }

  public Status status() {
    return status;
  }

  public Desired desired() {
    return desired;
  }

  public ReadBoardLoggingProtocol.Observed observed() {
    return observed;
  }

  public String processSessionId() {
    return processSessionId;
  }

  public boolean awaitsCapability() {
    return contractLaunch && status == Status.STARTING;
  }

  public void onReady() {
    if (!contractLaunch) {
      status = Status.LEGACY_UNCONFIRMED;
      clearObservedSuccess();
      return;
    }
    if (status == Status.CAPABILITY_READY
        || status == Status.OBSERVED
        || status == Status.APPLYING) {
      return;
    }
    status = Status.STARTING;
  }

  public void onCapability(ReadBoardLoggingProtocol.Capability capability) {
    if (capability == null) {
      return;
    }
    processSessionId = capability.processSessionId;
    observed = observedFromCapability(capability);
    status = Status.CAPABILITY_READY;
  }

  public void onObserved(ReadBoardLoggingProtocol.Observed incoming) {
    if (incoming == null || !gate.acceptObserved(incoming)) {
      return;
    }
    if (processSessionId != null && !processSessionId.equals(incoming.processSessionId)) {
      return;
    }
    processSessionId = incoming.processSessionId;
    observed = incoming;
    status = Status.OBSERVED;
  }

  public void onCapabilityTimeout() {
    if (!contractLaunch) {
      status = Status.LEGACY_UNCONFIRMED;
      clearObservedSuccess();
      return;
    }
    if (status == Status.STARTING) {
      status = Status.UNKNOWN;
      clearObservedSuccess();
      return;
    }
    if (status == Status.APPLYING) {
      status = Status.UNKNOWN;
    }
  }

  public void onDisconnect() {
    status = contractLaunch ? Status.UNKNOWN : Status.LEGACY_UNCONFIRMED;
    desired = Desired.launchDefaults(desired.diagnostics);
    clearObservedSuccess();
  }

  public void resetForNewProcess() {
    processSessionId = null;
    observed = null;
    gate = new ReadBoardLoggingProtocol.RequestGate();
    desired = Desired.launchDefaults(desired.diagnostics);
    status = contractLaunch ? Status.STARTING : Status.LEGACY_UNCONFIRMED;
  }

  public ReadBoardLoggingProtocol.SetRequest beginSet(
      boolean diagnostics, boolean capture, boolean trace) {
    desired = new Desired(diagnostics, capture, trace);
    String requestId = newRequestId();
    gate.noteRequest(requestId);
    status = Status.APPLYING;
    return new ReadBoardLoggingProtocol.SetRequest(
        requestId, toggle(diagnostics), toggle(capture), toggle(trace));
  }

  public ReadBoardLoggingSnapshot snapshot() {
    return ReadBoardLoggingSnapshot.from(this);
  }

  public Presentation presentation(
      boolean desiredOn,
      ReadBoardLoggingProtocol.Toggle observedToggle,
      ReadBoardLoggingProtocol.Persistence persistence) {
    if (status == Status.LEGACY_UNCONFIRMED) {
      return Presentation.LEGACY_UNCONFIRMED;
    }
    if (status == Status.UNKNOWN
        || status == Status.STARTING
        || observedToggle == null
        || observedToggle == ReadBoardLoggingProtocol.Toggle.UNKNOWN) {
      return Presentation.UNKNOWN;
    }
    if (desiredOn && observedToggle == ReadBoardLoggingProtocol.Toggle.OFF) {
      return Presentation.NOT_APPLIED;
    }
    if (observedToggle == ReadBoardLoggingProtocol.Toggle.ON) {
      if (persistence == ReadBoardLoggingProtocol.Persistence.DEGRADED) {
        return Presentation.ON_STORAGE_DEGRADED;
      }
      if (persistence == ReadBoardLoggingProtocol.Persistence.UNAVAILABLE) {
        return Presentation.ON_STORAGE_UNAVAILABLE;
      }
      return Presentation.ON;
    }
    return Presentation.OFF;
  }

  private void clearObservedSuccess() {
    processSessionId = null;
    observed = null;
  }

  private String newRequestId() {
    byte[] bytes = new byte[12];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static ReadBoardLoggingProtocol.Toggle toggle(boolean on) {
    return on ? ReadBoardLoggingProtocol.Toggle.ON : ReadBoardLoggingProtocol.Toggle.OFF;
  }

  private static ReadBoardLoggingProtocol.Observed observedFromCapability(
      ReadBoardLoggingProtocol.Capability capability) {
    return new ReadBoardLoggingProtocol.Observed(
        "",
        capability.processSessionId,
        toObservedToggle(capability.diagnostics),
        toObservedToggle(capability.capture),
        toObservedToggle(capability.trace),
        capability.persistence,
        capability.dropCount,
        ReadBoardLoggingProtocol.Reason.APPLIED);
  }

  private static ReadBoardLoggingProtocol.Toggle toObservedToggle(
      ReadBoardLoggingProtocol.Toggle toggle) {
    return toggle == null ? ReadBoardLoggingProtocol.Toggle.UNKNOWN : toggle;
  }
}
