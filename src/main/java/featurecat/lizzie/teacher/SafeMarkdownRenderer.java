package featurecat.lizzie.teacher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Renders the small, HTML-free Markdown subset used by AI commentary. */
final class SafeMarkdownRenderer {
  private SafeMarkdownRenderer() {}

  static String toHtml(String markdown) {
    return "<html><body>" + toBodyHtml(markdown) + "</body></html>";
  }

  static String toBodyHtml(String markdown) {
    if (markdown == null || markdown.isEmpty()) {
      return "";
    }
    String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
    StringBuilder html = new StringBuilder();
    String openList = null;
    boolean inCodeBlock = false;
    String[] lines = normalized.split("\n", -1);
    for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
      String line = lines[lineIndex];
      if (line.startsWith("```")) {
        openList = closeList(html, openList);
        if (inCodeBlock) {
          html.append("</code></pre>");
        } else {
          html.append("<pre><code>");
        }
        inCodeBlock = !inCodeBlock;
        continue;
      }
      if (inCodeBlock) {
        appendEscaped(html, line);
        html.append('\n');
        continue;
      }
      List<String> tableHeader = tableCells(line);
      if (!tableHeader.isEmpty()
          && lineIndex + 1 < lines.length
          && isTableSeparator(lines[lineIndex + 1], tableHeader.size())) {
        openList = closeList(html, openList);
        html.append("<table><thead><tr>");
        for (String cell : tableHeader) {
          html.append("<th>").append(inlineMarkdown(cell.trim())).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        lineIndex++;
        while (lineIndex + 1 < lines.length) {
          List<String> row = tableCells(lines[lineIndex + 1]);
          if (row.size() != tableHeader.size()) {
            break;
          }
          lineIndex++;
          html.append("<tr>");
          for (String cell : row) {
            html.append("<td>").append(inlineMarkdown(cell.trim())).append("</td>");
          }
          html.append("</tr>");
        }
        html.append("</tbody></table>");
        continue;
      }
      boolean unordered = line.startsWith("- ") || line.startsWith("* ");
      boolean ordered = line.matches("\\d+\\.\\s+.*");
      if (unordered || ordered) {
        String listType = unordered ? "ul" : "ol";
        if (!listType.equals(openList)) {
          openList = closeList(html, openList);
          html.append('<').append(listType).append('>');
          openList = listType;
        }
        int contentStart = unordered ? 2 : line.indexOf('.') + 1;
        while (contentStart < line.length() && Character.isWhitespace(line.charAt(contentStart))) {
          contentStart++;
        }
        html.append("<li>").append(inlineMarkdown(line.substring(contentStart))).append("</li>");
        continue;
      }
      openList = closeList(html, openList);
      if (line.startsWith("### ")) {
        html.append("<h3>").append(inlineMarkdown(line.substring(4))).append("</h3>");
      } else if (line.startsWith("## ")) {
        html.append("<h2>").append(inlineMarkdown(line.substring(3))).append("</h2>");
      } else if (line.startsWith("# ")) {
        html.append("<h1>").append(inlineMarkdown(line.substring(2))).append("</h1>");
      } else if (line.startsWith("> ")) {
        html.append("<blockquote>")
            .append(inlineMarkdown(line.substring(2)))
            .append("</blockquote>");
      } else if (line.trim().isEmpty()) {
        html.append("<div>&nbsp;</div>");
      } else {
        html.append("<div>").append(inlineMarkdown(line)).append("</div>");
      }
    }
    closeList(html, openList);
    if (inCodeBlock) {
      html.append("</code></pre>");
    }
    return html.toString();
  }

  private static boolean isTableSeparator(String line, int expectedColumns) {
    List<String> cells = tableCells(line);
    if (cells.size() != expectedColumns) {
      return false;
    }
    for (String cell : cells) {
      if (!cell.trim().matches(":?-{3,}:?")) {
        return false;
      }
    }
    return true;
  }

  private static List<String> tableCells(String line) {
    if (line == null || line.indexOf('|') < 0) {
      return Collections.emptyList();
    }
    String trimmed = line.trim();
    String[] rawCells = trimmed.split("\\|", -1);
    int start = rawCells.length > 0 && rawCells[0].trim().isEmpty() ? 1 : 0;
    int end =
        rawCells.length > start && rawCells[rawCells.length - 1].trim().isEmpty()
            ? rawCells.length - 1
            : rawCells.length;
    if (end <= start) {
      return Collections.emptyList();
    }
    List<String> cells = new ArrayList<String>(end - start);
    for (int index = start; index < end; index++) {
      cells.add(rawCells[index]);
    }
    return cells;
  }

  static String plainTextToBodyHtml(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
    StringBuilder html = new StringBuilder();
    for (String line : normalized.split("\n", -1)) {
      html.append("<div>");
      if (line.isEmpty()) {
        html.append("&nbsp;");
      } else {
        appendEscapedPreservingWhitespace(html, line);
      }
      html.append("</div>");
    }
    return html.toString();
  }

  static String escape(String text) {
    StringBuilder html = new StringBuilder();
    appendEscaped(html, text == null ? "" : text);
    return html.toString();
  }

  private static String inlineMarkdown(String text) {
    StringBuilder html = new StringBuilder();
    int index = 0;
    while (index < text.length()) {
      if (text.charAt(index) == '`') {
        int end = text.indexOf('`', index + 1);
        if (end > index + 1) {
          html.append("<code>");
          appendEscaped(html, text.substring(index + 1, end));
          html.append("</code>");
          index = end + 1;
          continue;
        }
      }
      if (text.startsWith("**", index)) {
        int end = text.indexOf("**", index + 2);
        if (end > index + 2) {
          html.append("<strong>");
          appendEscaped(html, text.substring(index + 2, end));
          html.append("</strong>");
          index = end + 2;
          continue;
        }
      }
      if (text.charAt(index) == '*') {
        int end = text.indexOf('*', index + 1);
        if (end > index + 1) {
          html.append("<em>");
          appendEscaped(html, text.substring(index + 1, end));
          html.append("</em>");
          index = end + 1;
          continue;
        }
      }
      appendEscaped(html, text.charAt(index));
      index++;
    }
    return html.toString();
  }

  private static String closeList(StringBuilder html, String openList) {
    if (openList != null) {
      html.append("</").append(openList).append('>');
    }
    return null;
  }

  private static void appendEscapedPreservingWhitespace(StringBuilder html, String text) {
    int consecutiveSpaces = 0;
    for (int index = 0; index < text.length(); index++) {
      char character = text.charAt(index);
      if (character == ' ') {
        consecutiveSpaces++;
        html.append(consecutiveSpaces == 1 ? " " : "&nbsp;");
      } else if (character == '\t') {
        consecutiveSpaces = 0;
        html.append("&nbsp;&nbsp;&nbsp;&nbsp;");
      } else {
        consecutiveSpaces = 0;
        appendEscaped(html, character);
      }
    }
  }

  private static void appendEscaped(StringBuilder html, String text) {
    for (int index = 0; index < text.length(); index++) {
      appendEscaped(html, text.charAt(index));
    }
  }

  private static void appendEscaped(StringBuilder html, char character) {
    switch (character) {
      case '&':
        html.append("&amp;");
        break;
      case '<':
        html.append("&lt;");
        break;
      case '>':
        html.append("&gt;");
        break;
      case '"':
        html.append("&quot;");
        break;
      case '\'':
        html.append("&#39;");
        break;
      default:
        html.append(character);
        break;
    }
  }
}
