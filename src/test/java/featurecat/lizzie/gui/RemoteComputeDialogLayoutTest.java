package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.atomic.AtomicReference;
import javax.accessibility.AccessibleContext;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

class RemoteComputeDialogLayoutTest {
  @Test
  void localizedButtonIncludesFractionalDpiSafetyPadding() {
    JButton button = new JButton("Open official website");

    assertTrue(
        RemoteComputeDialog.localizedButtonWidth(button, 40)
            >= button.getPreferredSize().width + 12);
  }

  @Test
  void localizedTabsExpandPastLegacyFixedWidth() {
    JButton first = new JButton("Zhizi Cloud");
    JButton second = new JButton("User-provided remote computing service");

    int width = RemoteComputeDialog.localizedButtonGroupWidth(292, 8, 12, first, second);

    assertTrue(width > 292);
    assertTrue(width >= first.getPreferredSize().width + second.getPreferredSize().width + 20);
  }

  @Test
  void localizedTabsKeepEachButtonsPreferredWidth() {
    JButton first = new JButton("Zhizi Cloud");
    JButton second = new JButton("User-provided remote computing service");
    JPanel group = RemoteComputeDialog.createLocalizedTabGroup(first, second);

    group.setSize(group.getPreferredSize());
    group.doLayout();

    assertTrue(first.getWidth() >= first.getPreferredSize().width);
    assertTrue(second.getWidth() >= second.getPreferredSize().width);
  }

  @Test
  void zhiziWebsiteCardKeepsItsActionCompactAndContentVisible() {
    JButton button = new JButton("Open official website");
    JPanel card =
        RemoteComputeDialog.createZhiziWebsiteCard(
            button, "Zhizi official website", "www.zhizigo.cn");

    assertTrue(card.getPreferredSize().height <= 96);
    assertEquals(card.getPreferredSize().height, card.getMaximumSize().height);

    card.setSize(520, card.getPreferredSize().height);
    layoutTree(card);

    Component title = findByName(card, "zhiziWebsiteTitle");
    Component url = findByName(card, "zhiziWebsiteUrl");
    Component action = findByName(card, "zhiziWebsiteAction");
    assertNotNull(title);
    assertNotNull(url);
    assertNotNull(action);
    assertTrue(button.getHeight() >= 42);
    assertTrue(title.isVisible());
    assertTrue(url.isVisible());
    assertWithinParent(title);
    assertWithinParent(url);
    assertWithinParent(action);
  }

  @Test
  void zhiziWebsiteCardAdaptsToLargeWindowsFontsWithoutClipping() {
    JButton button = new JButton("Open official website");
    button.setFont(button.getFont().deriveFont(22F));
    JPanel card =
        RemoteComputeDialog.createZhiziWebsiteCard(
            button, "Zhizi official website", "www.zhizigo.cn");

    card.setSize(560, card.getPreferredSize().height);
    layoutTree(card);

    assertTrue(card.getPreferredSize().height >= button.getPreferredSize().height + 26);
    assertWithinParent(findByName(card, "zhiziWebsiteTitle"));
    assertWithinParent(findByName(card, "zhiziWebsiteUrl"));
    assertWithinParent(findByName(card, "zhiziWebsiteAction"));
  }

  @Test
  void statusChangesRemainAvailableAfterTheLiveAnnouncement() {
    JLabel status = new JLabel("Custom compute enabled");
    JPanel indicator = new JPanel();
    AtomicReference<Object> announcedValue = new AtomicReference<>();
    status
        .getAccessibleContext()
        .addPropertyChangeListener(
            event -> {
              if (AccessibleContext.ACCESSIBLE_VISIBLE_DATA_PROPERTY.equals(
                  event.getPropertyName())) {
                announcedValue.set(event.getNewValue());
              }
            });

    RemoteComputeDialog.updateStatusAccessibility(
        status,
        indicator,
        "Connection status",
        "Connection indicator",
        "Connecting to custom compute");

    assertEquals("Connection status", status.getAccessibleContext().getAccessibleName());
    assertEquals(
        "Custom compute enabled", status.getAccessibleContext().getAccessibleDescription());
    assertEquals(
        "Custom compute enabled", indicator.getAccessibleContext().getAccessibleDescription());
    assertEquals("Custom compute enabled", announcedValue.get());
  }

  private static void layoutTree(Container container) {
    container.doLayout();
    for (Component child : container.getComponents()) {
      if (child instanceof Container) {
        layoutTree((Container) child);
      }
    }
  }

  private static Component findByName(Container container, String name) {
    for (Component child : container.getComponents()) {
      if (name.equals(child.getName())) {
        return child;
      }
      if (child instanceof Container) {
        Component match = findByName((Container) child, name);
        if (match != null) {
          return match;
        }
      }
    }
    return null;
  }

  private static void assertWithinParent(Component component) {
    assertNotNull(component);
    assertTrue(component.getX() >= 0);
    assertTrue(component.getY() >= 0);
    assertTrue(component.getX() + component.getWidth() <= component.getParent().getWidth());
    assertTrue(component.getY() + component.getHeight() <= component.getParent().getHeight());
  }
}
