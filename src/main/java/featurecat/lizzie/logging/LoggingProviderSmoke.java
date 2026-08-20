package featurecat.lizzie.logging;

import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.SLF4JServiceProvider;

public final class LoggingProviderSmoke {
  public static void main(String[] args) {
    int providers = 0;
    for (SLF4JServiceProvider ignored : ServiceLoader.load(SLF4JServiceProvider.class)) {
      providers++;
    }
    if (providers != 1) {
      System.err.println("expected one SLF4J provider, found " + providers);
      System.exit(2);
    }
    Path workDirectory = Path.of(args[0]);
    LoggingRuntime.resetForTests();
    LoggingRuntime runtime =
        LoggingRuntime.initialize(new WorkDirectoryResolution(workDirectory, List.of()));
    LoggerFactory.getLogger(LogCategories.APP).info("provider-smoke");
    runtime.awaitIdle();
    runtime.shutdown();
  }
}
