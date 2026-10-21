package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

class MenuShortcutHintTest {
  private static void splits(String text, String label, String hint) {
    MenuShortcutHint.Split split = MenuShortcutHint.split(text);
    assertEquals(label, split.label(), text);
    assertEquals(hint, split.hint(), text);
  }

  private static void keeps(String text) {
    splits(text, text, null);
  }

  @Test
  void keyShortcutsLeaveTheLabel() {
    splits("保存(Ctrl+S)", "保存", "Ctrl+S");
    splits("Pass (P)", "Pass", "P");
    splits("打开棋谱(O)", "打开棋谱", "O");
    splits("交换黑白(Ctrl+Shift+Alt+→)", "交换黑白", "Ctrl+Shift+Alt+→");
    splits("删除分支(Shift+Delete)", "删除分支", "Shift+Delete");
    splits("终止人机对局(空格)", "终止人机对局", "空格");
    splits("让AI落子最佳一手(逗號)", "让AI落子最佳一手", "逗號");
    splits("跳转到左分支(←)", "跳转到左分支", "←");
    splits("Previous variation ([)", "Previous variation", "[");
    // Only the last group: an explanation before it stays in the label.
    splits("清除Lizzie缓存(全局)(Shift+A)", "清除Lizzie缓存(全局)", "Shift+A");
    splits("Set engine rules(KataGo) (Shift+D)", "Set engine rules(KataGo)", "Shift+D");
  }

  @Test
  void shortcutsReadInOneNotation() {
    splits("终止引擎对局(ALT+R)", "终止引擎对局", "Alt+R");
    splits("Open (Alt + O)", "Open", "Alt+O");
    splits("open (ctrl+o)", "open", "Ctrl+O");
  }

  @Test
  void explanationsGesturesAndTyposStayInTheLabel() {
    keeps("浮动主棋盘(保留原棋盘)");
    keeps("整盘精析（推荐）");
    keeps("野狐棋谱(输入野狐昵称获取)");
    keeps("框选分析区域(Alt+鼠标圈选)");
    keeps("Analyze region (Alt+Mouse drag)");
    keeps("Save as (Crtl+Shift+S)");
    keeps("Settings (Shif+X)");
    keeps("Stop (分析模式 Alt+N)");
    keeps("(O)");
    keeps("新建");
    keeps("");
  }

  @Test
  void namesUsersChoseAreNeverParsed() {
    keeps("[1] KataGo (B)");
    keeps("2: game (A)");
  }

  @Test
  void acceleratorsUseTheLabelsNotation() {
    assertEquals(
        "Ctrl+Shift+B",
        MenuShortcutHint.format(
            KeyStroke.getKeyStroke(
                KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)));
    assertEquals(
        "Shift+O",
        MenuShortcutHint.format(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.SHIFT_DOWN_MASK)));
    assertNull(MenuShortcutHint.format(null));
  }

  @Test
  void everyBundledMenuLabelKeepsAWholeLabel() throws IOException {
    int split = 0;
    for (String suffix :
        new String[] {"", "_zh_CN", "_zh_TW", "_zh_HK", "_ja_JP", "_ko", "_th_TH", "_en_US"}) {
      Properties strings = new Properties();
      try (InputStream in =
          getClass().getResourceAsStream("/l10n/DisplayStrings" + suffix + ".properties")) {
        if (in == null) continue;
        strings.load(in);
      }
      for (String key : strings.stringPropertyNames()) {
        if (!key.startsWith("Menu.")) continue;
        String text = strings.getProperty(key);
        MenuShortcutHint.Split result = MenuShortcutHint.split(text);
        if (result.hint() == null) continue;
        split++;
        assertFalse(result.label().isBlank(), key + suffix);
        assertTrue(text.startsWith(result.label()), key + suffix);
      }
    }
    assertTrue(split > 100, "bundles should carry many key shortcuts, found " + split);
  }
}
