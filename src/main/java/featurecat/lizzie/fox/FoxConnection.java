package featurecat.lizzie.fox;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;

/** Explicit loopback listener with generation-bound, one-shot guarded writes. */
public final class FoxConnection implements AutoCloseable {
  public interface Listener {
    void connected(long connection);

    void received(long connection, String body);

    void disconnected(long connection);

    void problem(String key);
  }

  private final ServerSocket server;
  private final Listener listener;
  private Socket client;
  private static final AtomicLong CONNECTION_SEQUENCE = new AtomicLong();
  private boolean closed;
  private long clientToken;
  private final Object outputLock = new Object();

  /**
   * The nonblocking claim is the write-start linearization point; a started write is not
   * retractable.
   */
  public boolean send(long token, String body, java.util.function.BooleanSupplier claim)
      throws IOException {
    byte[] frame = FoxProtocol.encode(body);
    synchronized (outputLock) {
      Socket target;
      synchronized (this) {
        if (closed || client == null || clientToken != token) return false;
        target = client;
        if (!claim.getAsBoolean()) return false;
      }
      target.getOutputStream().write(frame);
      target.getOutputStream().flush();
      return true;
    }
  }

  public FoxConnection(int port, Listener listener) throws IOException {
    if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid local port");
    this.listener = listener;
    server = new ServerSocket();
    try {
      server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port));
    } catch (IOException failure) {
      server.close();
      throw failure;
    }
    Thread acceptor = new Thread(this::accept, "fox-ai-listener");
    acceptor.setDaemon(true);
    acceptor.start();
  }

  private void accept() {
    while (!server.isClosed()) {
      try {
        Socket incoming = server.accept();
        long token;
        synchronized (this) {
          if (closed || client != null) {
            incoming.close();
            if (!closed) listener.problem("Fox.duplicateClient");
            continue;
          }
          incoming.setTcpNoDelay(true);
          client = incoming;
          token = CONNECTION_SEQUENCE.incrementAndGet();
          clientToken = token;
        }
        listener.connected(token);
        Thread reader = new Thread(() -> read(incoming, token), "fox-ai-peer");
        reader.setDaemon(true);
        reader.start();
      } catch (IOException failure) {
        if (!server.isClosed()) listener.problem("Fox.connectionError");
        return;
      }
    }
  }

  private void read(Socket socket, long token) {
    FoxProtocol protocol = new FoxProtocol();
    byte[] buffer = new byte[4096];
    try {
      int count;
      while ((count = socket.getInputStream().read(buffer)) >= 0) {
        protocol.accept(buffer, count, body -> listener.received(token, body));
      }
    } catch (IllegalArgumentException failure) {
      listener.problem("Fox.protocolError");
    } catch (IOException failure) {
      synchronized (this) {
        if (!closed && client == socket) listener.problem("Fox.connectionError");
      }
    } finally {
      try {
        socket.close();
      } catch (IOException ignored) {
      }
      synchronized (this) {
        // Admission needs this lock and an empty slot: the old disconnect is delivered before any
        // replacement's connected callback, so its checkpoint/unresolved-submission transition
        // runs.
        listener.disconnected(token);
        if (client == socket) client = null;
      }
    }
  }

  @Override
  public synchronized void close() {
    closed = true;
    try {
      server.close();
    } catch (IOException ignored) {
    }
    if (client != null) {
      try {
        client.close();
      } catch (IOException ignored) {
      }
      client = null;
    }
  }
}
