package featurecat.lizzie.teacher;

import java.util.Optional;

/** Builds trusted HTML for displaying user SGF comments and stored AI commentary. */
public final class CommentDisplayRenderer {
  private CommentDisplayRenderer() {}

  public static String render(String rawComment) {
    String userComment = TeacherCommentCodec.removeBlocks(rawComment);
    Optional<String> aiCommentary = TeacherCommentCodec.extract(rawComment);
    StringBuilder body = new StringBuilder("<html><body>");
    if (!userComment.isEmpty()) {
      body.append("<div class='sgf-comment'>")
          .append(SafeMarkdownRenderer.plainTextToBodyHtml(userComment))
          .append("</div>");
    }
    if (aiCommentary.isPresent()) {
      if (!userComment.isEmpty()) {
        body.append("<div class='comment-spacer'>&nbsp;</div>");
      }
      body.append("<div class='ai-commentary'>")
          .append("<div class='ai-commentary-title'><strong>")
          .append(
              SafeMarkdownRenderer.escape(
                  TeacherStrings.get("Teacher.title", "AI Commentary")))
          .append("</strong></div>")
          .append(SafeMarkdownRenderer.toBodyHtml(aiCommentary.get()))
          .append("</div>");
    }
    return body.append("</body></html>").toString();
  }
}
