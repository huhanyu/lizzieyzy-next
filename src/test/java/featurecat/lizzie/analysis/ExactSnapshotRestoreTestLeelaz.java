package featurecat.lizzie.analysis;

import java.io.IOException;

public class ExactSnapshotRestoreTestLeelaz extends Leelaz {
  protected ExactSnapshotRestoreTestLeelaz(String engineCommand) throws IOException {
    super(engineCommand);
  }

  @Override
  final boolean sendExactSnapshotRestoreCommand(
      String command, Runnable onResponse, CommandSendFailureHandler onSendFailure) {
    return sendExactSnapshotRestoreCommandForTest(
        command, onResponse, adaptProductionFailureHandler(onSendFailure));
  }

  protected boolean sendExactSnapshotRestoreCommandForTest(
      String command, Runnable onResponse, TestCommandSendFailureHandler onSendFailure) {
    return super.sendExactSnapshotRestoreCommand(
        command, onResponse, adaptTestFailureHandler(onSendFailure));
  }

  private static TestCommandSendFailureHandler adaptProductionFailureHandler(
      CommandSendFailureHandler handler) {
    if (handler == null) {
      return null;
    }
    return new TestCommandSendFailureHandler() {
      @Override
      public void onSendFailure(RuntimeException ex) {
        handler.onSendFailure(ex);
      }

      @Override
      public void onStateResetAfterOutputWrite(RuntimeException ex) {
        handler.onStateResetAfterOutputWrite(ex);
      }
    };
  }

  private static CommandSendFailureHandler adaptTestFailureHandler(
      TestCommandSendFailureHandler handler) {
    if (handler == null) {
      return null;
    }
    return new CommandSendFailureHandler() {
      @Override
      public void onSendFailure(RuntimeException ex) {
        handler.onSendFailure(ex);
      }

      @Override
      public void onStateResetAfterOutputWrite(RuntimeException ex) {
        handler.onStateResetAfterOutputWrite(ex);
      }
    };
  }

  @FunctionalInterface
  protected interface TestCommandSendFailureHandler {
    void onSendFailure(RuntimeException ex);

    default void onStateResetAfterOutputWrite(RuntimeException ex) {
      onSendFailure(ex);
    }
  }
}
