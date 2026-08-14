package featurecat.lizzie.teacher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CommentDisplayRendererTest {
  @Test
  void rendersOrdinaryCommentAsEscapedPlainText() {
    String html = CommentDisplayRenderer.render("Line 1  aligned\n<script>bad()</script> & text");

    assertTrue(html.contains("Line 1 &nbsp;aligned"));
    assertTrue(html.contains("&lt;script&gt;bad()&lt;/script&gt; &amp; text"));
    assertFalse(html.contains("<script>"));
  }

  @Test
  void hidesStorageMetadataAndSafelyRendersAiMarkdown() {
    String stored =
        TeacherCommentCodec.upsert(
            "User <comment>\n\nSecond line", "# Review\n- **Good** move\n`D4`", "test-model");

    String html = CommentDisplayRenderer.render(stored);

    assertTrue(html.contains("User &lt;comment&gt;"));
    assertTrue(html.contains("<h1>Review</h1>"));
    assertTrue(html.contains("<ul><li><strong>Good</strong> move</li></ul>"));
    assertTrue(html.contains("<code>D4</code>"));
    assertFalse(html.contains("LizzieYzy AI Commentary BEGIN"));
    assertFalse(html.contains("generatedAt="));
    assertFalse(html.contains("model=test-model"));
  }

  @Test
  void preservesEmptyLinesInPlainComments() {
    String html = CommentDisplayRenderer.render("first\n\nlast");

    assertTrue(html.contains("<div>first</div><div>&nbsp;</div><div>last</div>"));
  }

  @Test
  void preservesUserWhitespaceAroundStoredAiCommentary() {
    String stored = TeacherCommentCodec.upsert("User line  \n", "# Review", "test");

    String html = CommentDisplayRenderer.render(stored);

    assertTrue(html.contains("<div>User line &nbsp;</div><div>&nbsp;</div>"));
    assertTrue(html.contains("<h1>Review</h1>"));
  }

  @Test
  void rendersMarkdownTablesWithoutAllowingEmbeddedHtml() {
    String stored =
        TeacherCommentCodec.upsert(
            "",
            "| Move | Review |\n| --- | --- |\n| D4 | **Good** & <img src=x> |",
            "test-model");

    String html = CommentDisplayRenderer.render(stored);

    assertTrue(html.contains("<table><thead><tr><th>Move</th><th>Review</th></tr></thead>"));
    assertTrue(html.contains("<td>D4</td><td><strong>Good</strong> &amp; &lt;img src=x&gt;</td>"));
    assertFalse(html.contains("<img"));
  }
}
