package featurecat.lizzie.gui;

import java.awt.Dimension;
import javax.swing.JPanel;

/**
 * The line above the figures while they rest on part of the game: how far the running analysis got,
 * or how much of the game is analyzed, with the button that analyzes the rest.
 */
final class StrengthBanner extends JPanel {
  private static final long serialVersionUID = 1L;
  private final transient StrengthStyle style;
  private final StrengthNote note;
  private final StrengthButton action;

  StrengthBanner(String actionText, StrengthStyle style) {
    super(null);
    this.style = style;
    setOpaque(false);
    note = new StrengthNote("", false, style);
    action = new StrengthButton(actionText, StrengthButton.Glyph.NONE, style);
    action.setName("strength-analyze-rest");
    add(note);
    add(action);
    setVisible(false);
  }

  /**
   * Shows {@code text}, with the button when {@code offerAnalysis}, or hides the line when there is
   * no text.
   */
  void showText(String text, boolean offerAnalysis) {
    boolean shown = text != null && !text.isEmpty();
    note.setText(shown ? text : "");
    action.setVisible(shown && offerAnalysis);
    setVisible(shown);
    revalidate();
    repaint();
  }

  String text() {
    return note.text();
  }

  StrengthButton actionButton() {
    return action;
  }

  private int actionWidth() {
    return action.isVisible() ? action.getPreferredSize().width + style.px(16) : 0;
  }

  @Override
  public Dimension getPreferredSize() {
    int width = getWidth() > 0 ? getWidth() : style.px(900);
    int actionHeight = action.isVisible() ? action.getPreferredSize().height : 0;
    return new Dimension(width, Math.max(note.heightFor(width - actionWidth()), actionHeight));
  }

  @Override
  public void doLayout() {
    int noteWidth = getWidth() - actionWidth();
    int noteHeight = note.heightFor(noteWidth);
    note.setBounds(0, (getHeight() - noteHeight) / 2, noteWidth, noteHeight);
    if (action.isVisible()) {
      Dimension size = action.getPreferredSize();
      action.setBounds(
          getWidth() - size.width, (getHeight() - size.height) / 2, size.width, size.height);
    }
  }
}
