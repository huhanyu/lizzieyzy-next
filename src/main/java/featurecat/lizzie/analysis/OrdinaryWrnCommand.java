package featurecat.lizzie.analysis;

/** Ordinary setter metadata carried by the existing command queue, not an engine-game update. */
record OrdinaryWrnCommand(double value) implements Runnable {
  @Override
  public void run() {
    // A setter ACK is not parameter readback. Metadata is published at the physical write.
  }
}
