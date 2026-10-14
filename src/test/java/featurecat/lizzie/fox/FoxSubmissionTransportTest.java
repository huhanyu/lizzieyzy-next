package featurecat.lizzie.fox;

import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class FoxSubmissionTransportTest {
  @Test void cancelledQueuedWriteCannotSendAndSuccessorCannotInheritIt() throws Exception {
    queuedWrite(false);
  }

  @Test void directDeadlineExpiringBehindOutputLockCannotSubmit() throws Exception {
    queuedWrite(true);
  }

  private void queuedWrite(boolean expireDirectLimit) throws Exception {
    AtomicLong token = new AtomicLong();
    CountDownLatch connected = new CountDownLatch(1);
    int port;
    try (ServerSocket free = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      port = free.getLocalPort();
    }
    FoxGameState game = new FoxGameState();
    if (expireDirectLimit) game.setDirectMaxTime(2);
    game.connected();
    game.receive("FARULE,19,60,30,3,650", 0);
    game.receive("FASTATUS,1,B", 0);
    game.receive("FATIMELEFT,60,30,3", 0);
    game.receive("FAMOVE,1,B,0^0^0^N", 0);
    assertTrue(game.beginClockRefresh(0));
    game.receive("FATIMELEFT,60,30,3", 0);
    var pending = game.prepareSubmission(game.snapshot(), 1, "D4", 1);
    ExecutorService writers = Executors.newFixedThreadPool(2);
    CountDownLatch outputHeld = new CountDownLatch(1);
    CountDownLatch releaseOutput = new CountDownLatch(1);
    try (FoxConnection transport = new FoxConnection(port, new FoxConnection.Listener() {
      public void connected(long value) { token.set(value); connected.countDown(); }
      public void received(long value, String body) {}
      public void disconnected(long value) {}
      public void problem(String key) {}
    }); Socket peer = new Socket("127.0.0.1", port)) {
      assertTrue(connected.await(2, TimeUnit.SECONDS));
      long original = token.get();
      Future<Boolean> first = writers.submit(() -> transport.send(original, "AFSTATUS,B", () -> {
        outputHeld.countDown();
        try { return releaseOutput.await(2, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
      }));
      assertTrue(outputHeld.await(2, TimeUnit.SECONDS));
      Future<Boolean> queued = writers.submit(() -> transport.send(original, pending.body(),
          () -> game.claimSubmission(pending, expireDirectLimit ? 3_000_000_000L : 3)));
      if (!expireDirectLimit) game.stopAutomation("Fox.stopped", 2);
      releaseOutput.countDown();
      assertTrue(first.get(2, TimeUnit.SECONDS));
      assertFalse(queued.get(2, TimeUnit.SECONDS));
      byte[] query = FoxProtocol.encode("AFSTATUS,B");
      assertArrayEquals(query, peer.getInputStream().readNBytes(query.length));
      peer.setSoTimeout(100);
      assertThrows(SocketTimeoutException.class, () -> peer.getInputStream().read());
      assertFalse(transport.send(original + 1, pending.body(), () -> true));
      assertTrue(game.snapshot().actions().isEmpty());
      assertEquals(expireDirectLimit ? FoxGameState.SubmissionOutcome.PENDING
          : FoxGameState.SubmissionOutcome.FAILED, game.submissions().get(0).outcome());
    } finally {
      releaseOutput.countDown();
      writers.shutdownNow();
    }
  }
}
