package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.GlyphView;
import javax.swing.text.Position;
import javax.swing.text.View;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import org.junit.jupiter.api.Test;

class KerningHtmlEditorKitTest {
  // A comment line: Chinese mixed with digits and punctuation.
  private static final String COMMENT = "领先: -6.2 不确定度: 13.0 (KataGo / 78 计算量)";

  @Test
  void commentFontsAreThePlainFontsWithKerning() throws Exception {
    LizzieFrame.HtmlKit kit = new LizzieFrame.HtmlKit();
    LizzieFrame.configureCommentHtmlStyle(kit.getStyleSheet(), Color.WHITE, Font.DIALOG, 15);
    HTMLEditorKit plainKit =
        new HTMLEditorKit() {
          @Override
          public StyleSheet getStyleSheet() {
            return kit.getStyleSheet();
          }
        };
    String html = "<html><body>" + COMMENT + "</body></html>";

    Font font = drawnFont(kit, html);
    Font plain = drawnFont(plainKit, html);

    assertFalse(plain.hasLayoutAttributes(), "the fast path, which drops glyphs on macOS");
    assertTrue(font.hasLayoutAttributes(), "drawn with TextLayout instead");
    assertEquals(FittedFont.withKerning(plain), font);
    assertEquals(Font.DIALOG, font.getFamily());
  }

  @Test
  void documentsOnTheDefaultStyleSheetKeepKerningAndItsStyles() throws Exception {
    Font font =
        drawnFont(new KerningHtmlEditorKit(), "<html><body><h2>" + COMMENT + "</h2></body></html>");

    assertTrue(font.hasLayoutAttributes());
    assertTrue(font.isBold(), "headings are still bold, so the default style sheet still applies");
  }

  @Test
  void commentPaneDrawsEveryLatinGlyphOfMixedTextOnMacOs() throws Exception {
    assumeTrue(System.getProperty("os.name", "").startsWith("Mac"));
    AtomicReference<String> blank = new AtomicReference<>();

    SwingUtilities.invokeAndWait(
        () -> {
          LizzieFrame.HtmlKit kit = new LizzieFrame.HtmlKit();
          // An unusual size, so no earlier test in this JVM has cached Dialog glyphs at it.
          LizzieFrame.configureCommentHtmlStyle(kit.getStyleSheet(), Color.WHITE, Font.DIALOG, 31);
          JEditorPane pane = new JEditorPane();
          pane.setOpaque(false);
          pane.setEditorKit(kit);
          pane.setDocument(kit.createDefaultDocument());
          pane.setText("<html><body>" + COMMENT + "</body></html>");
          pane.setSize(1200, 80);
          BufferedImage image = new BufferedImage(1200, 80, BufferedImage.TYPE_INT_ARGB);
          Graphics2D g = image.createGraphics();
          pane.paint(g);
          g.dispose();
          blank.set(blankLatinCharacters(pane, image));
        });

    assertEquals("", blank.get(), "characters drawn blank");
  }

  /** The font of the view that draws {@link #COMMENT} in an editor pane showing {@code html}. */
  private static Font drawnFont(HTMLEditorKit kit, String html) throws Exception {
    AtomicReference<Font> font = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          JEditorPane pane = new JEditorPane();
          pane.setEditorKit(kit);
          pane.setText(html);
          try {
            int offset =
                pane.getDocument().getText(0, pane.getDocument().getLength()).indexOf(COMMENT);
            pane.setSize(1200, 200);
            pane.modelToView2D(offset); // lays the text out into rows of glyph views
            View view = pane.getUI().getRootView(pane);
            while (view.getViewCount() > 0) {
              view = view.getView(view.getViewIndex(offset, Position.Bias.Forward));
            }
            font.set(((GlyphView) view).getFont());
          } catch (BadLocationException e) {
            throw new IllegalStateException(e);
          }
        });
    return font.get();
  }

  /** The Latin characters of {@link #COMMENT} that left no ink where the pane placed them. */
  private static String blankLatinCharacters(JEditorPane pane, BufferedImage image) {
    try {
      int first = pane.getDocument().getText(0, pane.getDocument().getLength()).indexOf(COMMENT);
      StringBuilder blank = new StringBuilder();
      for (int i = 0; i < COMMENT.length(); i++) {
        char c = COMMENT.charAt(i);
        if (c > 0x7f || c == ' ') continue;
        Rectangle2D start = pane.modelToView2D(first + i);
        Rectangle2D end = pane.modelToView2D(first + i + 1);
        if (!hasInk(image, start.getX(), end.getX(), start.getY(), start.getMaxY())) {
          blank.append(c);
        }
      }
      return blank.toString();
    } catch (BadLocationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static boolean hasInk(BufferedImage image, double x0, double x1, double y0, double y1) {
    for (int y = Math.max(0, (int) y0); y < Math.min(image.getHeight(), Math.ceil(y1)); y++) {
      for (int x = Math.max(0, (int) x0); x < Math.min(image.getWidth(), Math.ceil(x1)); x++) {
        if ((image.getRGB(x, y) >>> 24) != 0) {
          return true;
        }
      }
    }
    return false;
  }
}
