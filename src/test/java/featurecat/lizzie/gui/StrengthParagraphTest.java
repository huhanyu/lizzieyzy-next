package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class StrengthParagraphTest {
  private static final StrengthStyle STYLE = new StrengthStyle(false, false, "Dialog", 12);
  private static final String LONG =
      "The estimate is based on the AI analysis of every move. Let the AI analyze the whole game"
          + " first; the result appears here automatically.";

  @Test
  void newTextOrFontIsMeasuredAgain() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthParagraph paragraph =
              new StrengthParagraph(
                  LONG, STYLE.font(Font.PLAIN, 0), STYLE.text, StrengthPaint.CENTER, 20, STYLE);
          int tall = paragraph.heightFor(200);
          assertEquals(tall, paragraph.heightFor(200), "the same width gives the same lines");
          paragraph.setText("Short.");
          int line = paragraph.heightFor(200);
          assertTrue(line < tall, "one line now: " + line + " < " + tall);
          paragraph.setFont(STYLE.font(Font.PLAIN, 12));
          assertTrue(paragraph.heightFor(200) > line, "a larger font makes the line taller");
          paragraph.setText(LONG);
          assertTrue(paragraph.heightFor(800) < paragraph.heightFor(200), "wider is shorter");
        });
  }
}
