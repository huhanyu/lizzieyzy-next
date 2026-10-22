package featurecat.lizzie.gui;

import featurecat.lizzie.logging.LogCategories;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds and verifies the HumanSL model off the event thread: the check hashes the 99 MB file, which
 * froze the setup dialog for a moment each time it opened.
 */
final class HumanSlModelCheck {
  private static final Logger LOG = LoggerFactory.getLogger(LogCategories.APP);

  private HumanSlModelCheck() {}

  /**
   * @param resolve finds the verified model, or null when it is missing or damaged
   * @param done receives the result on the event thread
   */
  static void start(Supplier<Path> resolve, Consumer<Path> done) {
    Thread check =
        new Thread(
            () -> {
              Path model = null;
              try {
                model = resolve.get();
              } catch (RuntimeException failure) {
                LOG.warn("Could not check the HumanSL model", failure);
              } finally {
                Path found = model;
                SwingUtilities.invokeLater(() -> done.accept(found));
              }
            },
            "humansl-model-check");
    check.setDaemon(true);
    check.start();
  }
}
