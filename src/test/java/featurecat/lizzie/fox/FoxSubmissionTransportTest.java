package featurecat.lizzie.fox;

import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class FoxSubmissionTransportTest {
  @Test void cancelledQueuedWriteCannotSendAndSuccessorCannotInheritIt() throws Exception {
    queuedWrite(false, false);
  }

  @Test void directDeadlineExpiringBehindOutputLockCannotSubmit() throws Exception {
    queuedWrite(true, false);
  }

  @Test void pdaCancelsOnlyUnwrittenQueuedMoveAndKeepsSuccessorEligible() throws Exception {
    queuedWrite(false, true);
  }

  @Test void physicallyClaimedMoveCannotBeCancelledOrReplacedByPda() {
    FoxGameState game = new FoxGameState();
    game.connected();
    game.receive("FARULE,19,60,30,3,650", 0);
    game.receive("FASTATUS,1,B", 0);
    game.receive("FATIMELEFT,60,30,3", 0);
    game.receive("FAMOVE,1,B,0^0^0^N", 0);
    assertTrue(game.beginClockRefresh(0));
    game.receive("FATIMELEFT,60,30,3", 0);
    var pending = game.prepareSubmission(game.snapshot(), 1, "D4", 1);
    assertTrue(game.claimSubmission(pending, 2));
    assertFalse(game.cancelUnwrittenForPda(3));
    assertSame(pending, game.snapshot().pending());
    assertNull(game.prepareSubmission(game.snapshot(), 2, "Q16", 4));
    game.receive("FAMOVE,0,B,1^3^15^B", 5);
    assertEquals(FoxGameState.SubmissionOutcome.CONFIRMED, game.latestSubmission().outcome());
    assertEquals(1, game.snapshot().actions().size());
  }

  @Test void concurrentClaimAndPdaCancellationAreMutuallyExclusive() throws Exception {
    ExecutorService contenders = Executors.newFixedThreadPool(2);
    try {
      for (int attempt = 0; attempt < 50; attempt++) {
        FoxGameState game = new FoxGameState();
        game.connected();
        game.receive("FARULE,19,60,30,3,650", 0);
        game.receive("FASTATUS,1,B", 0);
        game.receive("FATIMELEFT,60,30,3", 0);
        game.receive("FAMOVE,1,B,0^0^0^N", 0);
        assertTrue(game.beginClockRefresh(0));
        game.receive("FATIMELEFT,60,30,3", 0);
        var pending = game.prepareSubmission(game.snapshot(), 1, "D4", 1);
        CountDownLatch start = new CountDownLatch(1);
        Future<Boolean> claim = contenders.submit(() -> {
          start.await();
          return game.claimSubmission(pending, 2);
        });
        Future<Boolean> cancel = contenders.submit(() -> {
          start.await();
          return game.cancelUnwrittenForPda(2);
        });
        start.countDown();
        boolean claimed = claim.get(2, TimeUnit.SECONDS);
        assertNotEquals(claimed, cancel.get(2, TimeUnit.SECONDS));
        assertEquals(claimed ? FoxGameState.SubmissionOutcome.PENDING
            : FoxGameState.SubmissionOutcome.CANCELLED, game.latestSubmission().outcome());
        assertEquals(claimed ? pending : null, game.snapshot().pending());
      }
    } finally {
      contenders.shutdownNow();
    }
  }

  private void queuedWrite(boolean expireDirectLimit, boolean pda) throws Exception {
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
      FoxGameState.Submission successor = null;
      if (pda) {
        long deadline = game.snapshot().submitDeadlineNanos();
        assertTrue(game.cancelUnwrittenForPda(2));
        successor = game.prepareSubmission(game.snapshot(), 2, "Q16", 3);
        assertNotNull(successor);
        assertEquals(deadline, successor.deadlineNanos());
      } else if (!expireDirectLimit) game.stopAutomation("Fox.stopped", 2);
      releaseOutput.countDown();
      assertTrue(first.get(2, TimeUnit.SECONDS));
      assertFalse(queued.get(2, TimeUnit.SECONDS));
      byte[] query = FoxProtocol.encode("AFSTATUS,B");
      assertArrayEquals(query, peer.getInputStream().readNBytes(query.length));
      peer.setSoTimeout(100);
      assertThrows(SocketTimeoutException.class, () -> peer.getInputStream().read());
      assertFalse(transport.send(original + 1, pending.body(), () -> true));
      assertTrue(game.snapshot().actions().isEmpty());
      assertEquals(pda ? FoxGameState.SubmissionOutcome.CANCELLED : expireDirectLimit ? FoxGameState.SubmissionOutcome.PENDING
          : FoxGameState.SubmissionOutcome.FAILED, game.submissions().get(0).outcome());
      if (pda) {
        assertSame(successor, game.snapshot().pending(), "late cancelled send does not affect successor");
        assertTrue(game.claimSubmission(successor, 4));
        game.receive("FAMOVE,0,B,1^15^3^B", 5);
        game.disconnected();
        assertTrue(game.hasCheckpoint(), "proven never-written cancellation does not poison confirmed history");
      }
    } finally {
      releaseOutput.countDown();
      writers.shutdownNow();
    }
  }
}
