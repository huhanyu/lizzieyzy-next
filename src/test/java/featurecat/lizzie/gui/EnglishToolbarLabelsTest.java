package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class EnglishToolbarLabelsTest {
  @Test
  void bottomToolbarLabelsAreWordsRatherThanIdentifiers() throws Exception {
    for (String bundle :
        List.of("/l10n/DisplayStrings.properties", "/l10n/DisplayStrings_en_US.properties")) {
      Properties strings = load(bundle);
      for (String key : strings.stringPropertyNames()) {
        if (!key.matches("BottomToolbar\\.[A-Za-z0-9]+")) continue;
        String value = strings.getProperty(key);
        assertFalse(
            value.replace("KataGo", "").matches(".*[a-z][A-Z].*"),
            () -> bundle + ": " + key + "=" + value + " reads like a code identifier");
      }
      assertEquals("Analyze", strings.getProperty("BottomToolbar.analyse"));
      assertEquals("Go to", strings.getProperty("BottomToolbar.gotomove"));
      assertEquals("New game", strings.getProperty("Menu.newGameBtn"));
      assertEquals("Breadth", strings.getProperty("Menu.lblWRN"));
      assertEquals("Breadth", strings.getProperty("Menu.separateLblWrn"));
    }
  }

  @Test
  void thaiNamesBreadthInsteadOfTheEngineParameter() throws Exception {
    Properties thai = load("/l10n/DisplayStrings_th_TH.properties");

    assertNotEquals("WRN", thai.getProperty("Menu.lblWRN"));
    assertNotEquals("WRN", thai.getProperty("Menu.separateLblWrn"));
  }

  private static Properties load(String resource) throws Exception {
    Properties properties = new Properties();
    try (InputStream in = EnglishToolbarLabelsTest.class.getResourceAsStream(resource)) {
      properties.load(in);
    }
    return properties;
  }
}
