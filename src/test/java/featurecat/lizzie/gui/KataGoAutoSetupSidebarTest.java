package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Point;
import org.junit.jupiter.api.Test;

class KataGoAutoSetupSidebarTest {
  @Test
  void blankSpaceBelowNavigationItemsDoesNotSelectTheLastItem() {
    KataGoAutoSetupDialog.ExactHitList<String> navigation =
        new KataGoAutoSetupDialog.ExactHitList<>();
    navigation.setListData(new String[] {"Overview", "Weights", "Speed", "Acceleration"});
    navigation.setFixedCellHeight(20);
    navigation.setSize(180, 120);

    assertEquals(3, navigation.locationToIndex(new Point(20, 70)));
    assertEquals(-1, navigation.locationToIndex(new Point(20, 100)));
  }
}
