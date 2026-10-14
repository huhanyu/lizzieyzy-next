package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FoxPdaCompatibilityTest {
  @TempDir Path directory;

  @Test void capturesActualLaunchConfigAndRefusesDynamicPresenceEvenZero() throws Exception {
    Path config = directory.resolve("engine.cfg");
    Files.writeString(config, "numSearchThreads = 2\nplayoutDoublingAdvantage = 0.2\n");
    List<String> command = List.of("katago", "gtp", "-config", "engine.cfg");
    assertEquals("", FoxPdaCompatibility.inspect(command, directory));
    Files.writeString(config, "dynamicPlayoutDoublingAdvantageCapPerOppLead = 0\n");
    assertEquals("Fox.pdaDynamic", FoxPdaCompatibility.inspect(command, directory));
    Files.writeString(config, "# dynamicPlayoutDoublingAdvantageCapPerOppLead = 0.1\n");
    assertEquals("", FoxPdaCompatibility.inspect(command, directory));
    assertEquals("Fox.pdaDynamic", FoxPdaCompatibility.inspect(List.of("katago", "gtp", "-config", "engine.cfg",
        "--override-config", "dynamicPlayoutDoublingAdvantageCapPerOppLead=0.1"), directory));
    Files.writeString(config, "@include hidden.cfg\n");
    assertEquals("Fox.pdaUnknown", FoxPdaCompatibility.inspect(command, directory));
    assertEquals("Fox.pdaUnknown", FoxPdaCompatibility.inspect(List.of("ssh", "host", "katago"), directory));
  }
}
