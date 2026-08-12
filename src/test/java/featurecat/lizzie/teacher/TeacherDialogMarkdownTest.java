package featurecat.lizzie.teacher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TeacherDialogMarkdownTest {
  @Test
  void escapesUntrustedHtmlBeforeRendering() {
    String html = TeacherDialog.markdownToHtml("<script>alert('x')</script> & text");

    assertFalse(html.contains("<script>"));
    assertTrue(html.contains("&lt;script&gt;"));
    assertTrue(html.contains("&amp; text"));
  }

  @Test
  void wrapsBulletAndNumberedItemsInValidLists() {
    String html = TeacherDialog.markdownToHtml("- first\n- second\n\n1. one\n2. two");

    assertTrue(html.contains("<ul><li>first</li><li>second</li></ul>"));
    assertTrue(html.contains("<ol><li>one</li><li>two</li></ol>"));
  }

  @Test
  void rendersHeadingsInlineStylesAndEscapedCodeBlocks() {
    String html =
        TeacherDialog.markdownToHtml(
            "# Review\n**important** and `D4`\n```\nif (a < b) return;\n```");

    assertTrue(html.contains("<h1>Review</h1>"));
    assertTrue(html.contains("<strong>important</strong>"));
    assertTrue(html.contains("<code>D4</code>"));
    assertTrue(html.contains("<pre><code>if (a &lt; b) return;"));
  }
}
