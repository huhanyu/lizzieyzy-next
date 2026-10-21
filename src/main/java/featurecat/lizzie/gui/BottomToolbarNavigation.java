package featurecat.lizzie.gui;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

/** The move navigation group in the middle of the bottom toolbar. */
final class BottomToolbarNavigation {
  private BottomToolbarNavigation() {}

  /**
   * The visible navigation controls, left to right. Back one and forward one sit together, so the
   * pair used most is one short mouse move apart; the move field and Go to follow the transport.
   */
  static List<Component> visibleComponents(BottomToolbar toolbar) {
    List<Component> visible = new ArrayList<>();
    for (Component component :
        new Component[] {
          toolbar.firstButton,
          toolbar.backward10,
          toolbar.backward1,
          toolbar.forward1,
          toolbar.forward10,
          toolbar.lastButton,
          toolbar.txtMoveNumber,
          toolbar.gotomove,
          toolbar.autoPlay
        }) {
      if (component != null && component.isVisible()) visible.add(component);
    }
    return visible;
  }
}
