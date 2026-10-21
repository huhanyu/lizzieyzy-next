package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Component;
import java.lang.reflect.Field;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

class BottomToolbarNavigationTest {
  @Test
  void backOneAndForwardOneSitTogetherAndGoToFollowsTheTransport() throws Exception {
    BottomToolbar toolbar = toolbarWithNavigation();

    assertEquals(
        List.of(
            toolbar.firstButton,
            toolbar.backward10,
            toolbar.backward1,
            toolbar.forward1,
            toolbar.forward10,
            toolbar.lastButton,
            toolbar.txtMoveNumber,
            toolbar.gotomove,
            toolbar.autoPlay),
        BottomToolbarNavigation.visibleComponents(toolbar));
  }

  @Test
  void hiddenControlsAreLeftOut() throws Exception {
    BottomToolbar toolbar = toolbarWithNavigation();
    toolbar.backward10.setVisible(false);
    toolbar.forward10.setVisible(false);
    toolbar.autoPlay = null;

    List<Component> visible = BottomToolbarNavigation.visibleComponents(toolbar);

    assertEquals(
        List.of(
            toolbar.firstButton,
            toolbar.backward1,
            toolbar.forward1,
            toolbar.lastButton,
            toolbar.txtMoveNumber,
            toolbar.gotomove),
        visible);
  }

  private static BottomToolbar toolbarWithNavigation() throws Exception {
    BottomToolbar toolbar = allocate(BottomToolbar.class);
    toolbar.firstButton = new JButton("|<");
    toolbar.backward10 = new JButton("<<");
    toolbar.backward1 = new JButton("<");
    toolbar.forward1 = new JButton(">");
    toolbar.forward10 = new JButton(">>");
    toolbar.lastButton = new JButton(">|");
    toolbar.txtMoveNumber = new JTextField("12");
    toolbar.gotomove = new JButton("Go to");
    toolbar.autoPlay = new JButton("Auto play");
    return toolbar;
  }

  @SuppressWarnings("unchecked")
  private static <T> T allocate(Class<T> type) throws Exception {
    Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    return (T) ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
  }
}
