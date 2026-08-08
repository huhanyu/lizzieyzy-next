package featurecat.lizzie.teacher;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds bounded, evidence-only prompts from immutable KataGo snapshots. */
final class TeacherPromptBuilder {
  enum Mode {
    NEXT_MOVE,
    RANGE,
    WHOLE_GAME,
    FOLLOW_UP
  }

  private static final DecimalFormat ONE_DECIMAL =
      new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.ROOT));

  private TeacherPromptBuilder() {}

  static List<TeacherLlmClient.Message> forPosition(
      TeacherEvidence.Position position, Locale locale) {
    return List.of(
        new TeacherLlmClient.Message("system", systemPrompt(locale)),
        new TeacherLlmClient.Message(
            "user",
            modeInstruction(Mode.NEXT_MOVE)
                + "\n\n【KataGo evidence】\n"
                + formatPosition(position)));
  }

  static List<TeacherLlmClient.Message> forRange(
      TeacherEvidence.Range range, Mode mode, Locale locale) {
    StringBuilder evidence = new StringBuilder();
    evidence
        .append("Analyzed positions: ")
        .append(range.analyzedPositions)
        .append("; selected key positions: ")
        .append(range.positions.size());
    if (range.omittedPositions > 0) {
      evidence.append("; omitted lower-priority positions: ").append(range.omittedPositions);
    }
    evidence.append('\n');
    for (TeacherEvidence.Position position : range.positions) {
      evidence.append('\n').append(formatPosition(position));
    }
    return List.of(
        new TeacherLlmClient.Message("system", systemPrompt(locale)),
        new TeacherLlmClient.Message(
            "user", modeInstruction(mode) + "\n\n【KataGo evidence】\n" + evidence));
  }

  static List<TeacherLlmClient.Message> forFollowUp(
      List<TeacherLlmClient.Message> evidenceContext,
      String previousAnswer,
      String question,
      Locale locale) {
    ArrayList<TeacherLlmClient.Message> messages = new ArrayList<>();
    if (evidenceContext != null) {
      messages.addAll(evidenceContext);
    }
    if (messages.isEmpty()) {
      messages.add(new TeacherLlmClient.Message("system", systemPrompt(locale)));
    }
    String boundedAnswer = previousAnswer == null ? "" : previousAnswer.trim();
    if (boundedAnswer.length() > 12_000) {
      boundedAnswer = boundedAnswer.substring(boundedAnswer.length() - 12_000);
    }
    if (!boundedAnswer.isEmpty()) {
      messages.add(new TeacherLlmClient.Message("assistant", boundedAnswer));
    }
    messages.add(
        new TeacherLlmClient.Message(
            "user", modeInstruction(Mode.FOLLOW_UP) + "\n\n" + question.trim()));
    return List.copyOf(messages);
  }

  static String formatPosition(TeacherEvidence.Position position) {
    StringBuilder text = new StringBuilder();
    text.append("Position after move ").append(position.moveNumber).append('\n');
    text.append("Side to play: ").append(position.toPlay).append('\n');
    text.append("Root visits: ").append(position.playouts).append('\n');
    text.append("Actual next move: ")
        .append(position.actualMove.isEmpty() ? "not available" : position.actualMove)
        .append('\n');
    if (position.actualWinrateLoss.isPresent()) {
      text.append("Actual move winrate loss versus top candidate: ")
          .append(format(position.actualWinrateLoss.getAsDouble()))
          .append(" percentage points\n");
    }
    for (TeacherEvidence.Candidate candidate : position.candidates) {
      text.append("Candidate #")
          .append(candidate.rank)
          .append(": move=")
          .append(candidate.coordinate)
          .append(", visits=")
          .append(candidate.visits);
      if (Double.isFinite(candidate.winrate)) {
        text.append(", winrate=").append(format(candidate.winrate)).append('%');
      }
      if (Double.isFinite(candidate.scoreLead)) {
        text.append(", scoreLead=").append(format(candidate.scoreLead));
      }
      if (!candidate.variation.isEmpty()) {
        text.append(", pv=").append(String.join(" ", candidate.variation));
      }
      text.append('\n');
    }
    return text.toString();
  }

  private static String systemPrompt(Locale locale) {
    return "You are a careful Go review assistant. Reply in "
        + outputLanguage(locale)
        + ". Use only the supplied KataGo evidence. Never invent coordinates, variations, "
        + "winrates, score leads, move intentions, or game results. If evidence is missing, "
        + "say so plainly. Winrate and scoreLead are raw KataGo values for comparison within "
        + "the same position; do not silently flip perspective or infer a black-positive sign. "
        + "Separate facts from teaching interpretation. Do not make cheating accusations or "
        + "claim an official rank. Keep the explanation practical and understandable.";
  }

  private static String modeInstruction(Mode mode) {
    switch (mode) {
      case RANGE:
        return "Review the selected move range. Focus on the most important turning points, "
            + "compare the actual move with KataGo's candidates, follow only the supplied PVs, "
            + "and finish with three actionable lessons.";
      case WHOLE_GAME:
        return "Review the whole game from the selected key positions. Give a short overview, "
            + "the decisive turning points in chronological order, and three actionable lessons. "
            + "Do not pretend that omitted positions were analyzed.";
      case FOLLOW_UP:
        return "Answer the follow-up using the same evidence. If the question needs information "
            + "that is not present, explain what additional KataGo analysis is required.";
      case NEXT_MOVE:
      default:
        return "Explain the actual next move when available and compare it with KataGo's top "
            + "three candidates. Follow each supplied PV move by move, then give one practical "
            + "principle. If there is no actual next move, explain only the candidates.";
    }
  }

  private static String outputLanguage(Locale locale) {
    String language = locale == null ? "zh" : locale.getLanguage();
    String country = locale == null ? "" : locale.getCountry();
    if ("zh".equals(language)) {
      return "TW".equalsIgnoreCase(country) || "HK".equalsIgnoreCase(country)
          ? "Traditional Chinese"
          : "Simplified Chinese";
    }
    if ("ja".equals(language)) {
      return "Japanese";
    }
    if ("ko".equals(language)) {
      return "Korean";
    }
    if ("th".equals(language)) {
      return "Thai";
    }
    return "English";
  }

  private static String format(double value) {
    synchronized (ONE_DECIMAL) {
      return ONE_DECIMAL.format(value);
    }
  }
}
