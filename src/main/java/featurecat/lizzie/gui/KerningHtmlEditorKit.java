package featurecat.lizzie.gui;

import java.awt.Font;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

/**
 * An HTML editor kit whose documents give every font {@link java.awt.font.TextAttribute#KERNING}.
 *
 * <p>Swing draws HTML text with {@code drawString}. For a font without layout attributes that is
 * the JDK's fast path, which on macOS draws the Latin glyphs of a text run blank when the run also
 * needs a fallback font for Chinese, Japanese or Korean, and keeps them cached blank for that size
 * (see {@link FittedFont}). Comments lost their digits and colons that way ("不确定度: 1 .0"), and in a
 * size no other text had used yet, all their Latin text. A font with a layout attribute is drawn
 * with TextLayout, which draws every glyph.
 */
public class KerningHtmlEditorKit extends HTMLEditorKit {
  @Override
  public Document createDefaultDocument() {
    // As in HTMLEditorKit, except for the document's own style sheet, which resolves the fonts.
    StyleSheet styles = new KerningStyleSheet();
    styles.addStyleSheet(getStyleSheet());
    HTMLDocument document = new HTMLDocument(styles);
    document.setParser(getParser());
    document.setAsynchronousLoadPriority(4);
    document.setTokenThreshold(100);
    return document;
  }

  /** A style sheet whose fonts keep kerning. */
  static final class KerningStyleSheet extends StyleSheet {
    private final Map<Font, Font> kerned = new ConcurrentHashMap<>();

    @Override
    public Font getFont(AttributeSet attributes) {
      return kerned.computeIfAbsent(super.getFont(attributes), FittedFont::withKerning);
    }
  }
}
