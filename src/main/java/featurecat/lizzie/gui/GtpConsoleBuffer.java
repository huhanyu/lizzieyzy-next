package featurecat.lizzie.gui;

import featurecat.lizzie.logging.ObservationText;
import featurecat.lizzie.util.DocType;
import java.util.ArrayDeque;

final class GtpConsoleBuffer {
  static final int CAPACITY = 4096;
  private final ArrayDeque<DocType> queue = new ArrayDeque<>();

  void offer(DocType doc) {
    if (doc == null) {
      return;
    }
    DocType bounded = boundedCopy(doc);
    synchronized (queue) {
      while (queue.size() >= CAPACITY) {
        queue.removeFirst();
      }
      queue.addLast(bounded);
    }
  }

  DocType poll() {
    synchronized (queue) {
      return queue.pollFirst();
    }
  }

  int size() {
    synchronized (queue) {
      return queue.size();
    }
  }

  private static DocType boundedCopy(DocType source) {
    DocType bounded = new DocType();
    bounded.content = ObservationText.boundedRawEvent(source.content == null ? "" : source.content);
    bounded.contentColor = source.contentColor;
    bounded.isCommand = source.isCommand;
    bounded.fontSize = source.fontSize;
    return bounded;
  }
}
