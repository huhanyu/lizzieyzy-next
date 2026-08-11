package featurecat.lizzie.teacher;

import featurecat.lizzie.teacher.analysis.TeacherPersona;
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
      TeacherEvidence.Position position, Locale locale, TeacherSettings.Snapshot snapshot) {
    return List.of(
        new TeacherLlmClient.Message("system", systemPrompt(locale, snapshot)),
        new TeacherLlmClient.Message(
            "user",
            modeInstruction(Mode.NEXT_MOVE)
                + "\n\n【KataGo evidence】\n"
                + formatPosition(position)));
  }

  static List<TeacherLlmClient.Message> forRange(
      TeacherEvidence.Range range, Mode mode, Locale locale, TeacherSettings.Snapshot snapshot) {
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
        new TeacherLlmClient.Message("system", systemPrompt(locale, snapshot)),
        new TeacherLlmClient.Message(
            "user", modeInstruction(mode) + "\n\n【KataGo evidence】\n" + evidence));
  }

  static List<TeacherLlmClient.Message> forFollowUp(
      List<TeacherLlmClient.Message> evidenceContext,
      String previousAnswer,
      String question,
      Locale locale,
      TeacherSettings.Snapshot snapshot) {
    ArrayList<TeacherLlmClient.Message> messages = new ArrayList<>();
    if (evidenceContext != null) {
      messages.addAll(evidenceContext);
    }
    if (messages.isEmpty()) {
      messages.add(new TeacherLlmClient.Message("system", systemPrompt(locale, snapshot)));
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

  private static String modeInstruction(Mode mode) {
    switch (mode) {
      case RANGE:
        return "回顾选定手数范围。重点关注最重要的转折点，"
            + "比较实战手与 KataGo 候选，仅使用提供的 PV 变化，"
            + "最后给出三条可执行的改进建议。";
      case WHOLE_GAME:
        return "从选定的关键局面回顾整局。给出简短总览，"
            + "按时间顺序列出决定性转折点，以及三条可执行的改进建议。"
            + "不要假设被省略的局面已被分析。";
      case FOLLOW_UP:
        return "基于相同证据回答追问。如果问题需要的信息不在现有数据中，"
            + "说明需要补充哪些 KataGo 分析。";
      case NEXT_MOVE:
      default:
        return "讲解实战下一手（如有），并与 KataGo 前三个候选比较。"
            + "逐一跟踪每个提供的 PV，然后给出一个实用原则。"
            + "如果没有实战下一手，仅讲解候选。";
    }
  }

  static String formatPosition(TeacherEvidence.Position position) {
    StringBuilder text = new StringBuilder();
    text.append(TeacherStrings.format("Teacher.prompt.positionAfter", "Position after move {0}", position.moveNumber)).append('\n');
    text.append(TeacherStrings.format("Teacher.prompt.sideToPlay", "Side to play: {0}", position.toPlay)).append('\n');
    text.append(TeacherStrings.format("Teacher.prompt.rootVisits", "Root visits: {0}", position.playouts)).append('\n');
    text.append(TeacherStrings.format(
            "Teacher.prompt.actualMove",
            "Actual next move: {0}",
            position.actualMove.isEmpty()
                ? TeacherStrings.get("Teacher.prompt.notAvailable", "not available")
                : position.actualMove))
        .append('\n');
    if (position.actualWinrateLoss.isPresent()) {
      text.append(TeacherStrings.format(
              "Teacher.prompt.winrateLoss",
              "Actual move winrate loss versus top candidate: {0} percentage points",
              format(position.actualWinrateLoss.getAsDouble())))
          .append("\n");
    }
    if (!position.playedContinuation.isEmpty()) {
      text.append(TeacherStrings.format(
              "Teacher.prompt.playedContinuation",
              "Played continuation: {0}",
              String.join(" ", position.playedContinuation)))
          .append('\n');
    }
    for (TeacherEvidence.Candidate candidate : position.candidates) {
      text.append(TeacherStrings.format("Teacher.prompt.candidateRank", "Candidate #{0}", candidate.rank))
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
        text.append(", pv=");
        boolean black = "B".equals(position.toPlay);
        String blackLabel = TeacherStrings.get("Teacher.prompt.black", "(B)");
        String whiteLabel = TeacherStrings.get("Teacher.prompt.white", "(W)");
        for (int index = 0; index < candidate.variation.size(); index++) {
          String variationMove = candidate.variation.get(index);
          if (index > 0) {
            text.append(' ');
          }
          text.append(variationMove).append(black ? blackLabel : whiteLabel);
          black = !black;
        }
      }
      text.append('\n');
    }
    if (!position.actualMove.isEmpty()
        && position.candidates.stream()
            .noneMatch(
                candidate ->
                    candidate.coordinate != null
                        && candidate.coordinate.equalsIgnoreCase(position.actualMove))) {
      text.append(TeacherStrings.format(
              "Teacher.prompt.notInCandidates",
              "Note: the played move {0} is NOT among the top candidates above; compare it against the list.",
              position.actualMove))
          .append("\n");
    }
    return text.toString();
  }

  private static String systemPrompt(Locale locale, TeacherSettings.Snapshot snapshot) {
    StringBuilder prompt = new StringBuilder();
    prompt.append("你是一个围棋 AI 讲棋老师。\n");
    prompt.append("教学对象：").append(snapshot != null ? snapshot.rankMode : "k")
        .append(snapshot != null ? snapshot.rankNum : 5).append("。\n");
    if (snapshot != null) {
      prompt.append(buildChinesePersona(snapshot)).append("\n");
    }
    prompt.append("请基于给出的 KataGo 分析数据（胜率、目差、AI 首选、损失、知识匹配等）进行讲解，")
        .append("指出关键手、问题手与最佳应对，语言通俗易懂、结合具体坐标。\n");
    prompt.append("讲解格式要求：\n");
    prompt.append("1) 先用通俗语言讲解这一手的好坏与原因；\n");
    prompt.append("2) 末尾用以下固定标记补充结构化内容（无则省略该段）：\n");
    prompt.append("### 正确思路\n（给出比实战更好的下法及其变化图/结果，1-3 条）\n");
    prompt.append("### 练习建议\n（给出 1-2 个针对性练习，标明类型：死活/手筋/思路）\n");
    prompt.append("若数据不足以判断，坦诚说明。\n");
    prompt.append("讲解正文严禁出现\"围棋老师\"、\"讲棋老师\"、\"教练\"等称呼。\n");
    prompt.append("不要在回答中提及用户的段位。\n");
    prompt.append("输出语言：请全程使用 ").append(outputLanguage(locale))
        .append(" 输出解说（包括标题、正文、对比表、训练建议），不要混用其他语言。\n");
    prompt.append("视角说明：胜率和目差已换算为当前行棋方视角（落子方胜率，黑正目差），")
        .append("直接使用，不要再换算。\n");
    prompt.append("禁止编造坐标、变化、胜率、目差或比赛结果。若数据缺失，直接说明。\n");
    return prompt.toString();
  }

  /** 用 TeacherPersona 生成完整的中文 persona 指令 */
  private static String buildChinesePersona(TeacherSettings.Snapshot s) {
    TeacherPersona.TeacherPersonaInput pin = new TeacherPersona.TeacherPersonaInput();
    pin.level = snapshotToLevel(s);
    pin.rank = snapshotToRank(s);
    pin.exactAge = null;
    pin.ageRange = TeacherPersona.AgeRange.UNKNOWN;
    pin.style = snapshotToStyle(s);
    pin.terminologyDensity = snapshotToDensity(s);
    pin.explanationPace = snapshotToPace(s);
    pin.variationDetail = snapshotToVariation(s);
    return TeacherPersona.buildTeacherPersonaInstruction(pin);
  }

  private static TeacherPersona.Level snapshotToLevel(TeacherSettings.Snapshot s) {
    if ("d".equals(s.rankMode)) {
      return s.rankNum >= 5 ? TeacherPersona.Level.DAN : TeacherPersona.Level.ADVANCED;
    }
    return s.rankNum >= 10 ? TeacherPersona.Level.BEGINNER : TeacherPersona.Level.INTERMEDIATE;
  }

  private static TeacherPersona.Rank snapshotToRank(TeacherSettings.Snapshot s) {
    if ("d".equals(s.rankMode)) {
      int d = Math.max(1, Math.min(9, s.rankNum));
      return TeacherPersona.Rank.values()[d];
    }
    return TeacherPersona.Rank.SUB1D;
  }

  private static TeacherPersona.Style snapshotToStyle(TeacherSettings.Snapshot s) {
    return switch (Math.max(0, Math.min(4, s.styleIndex))) {
      case 1 -> TeacherPersona.Style.RIGOROUS;
      case 2 -> TeacherPersona.Style.GENTLE;
      case 3 -> TeacherPersona.Style.STRICT;
      case 4 -> TeacherPersona.Style.HUMOROUS;
      default -> TeacherPersona.Style.BALANCED;
    };
  }

  private static TeacherPersona.TerminologyDensity snapshotToDensity(TeacherSettings.Snapshot s) {
    return switch (Math.max(0, Math.min(2, s.densityIndex))) {
      case 0 -> TeacherPersona.TerminologyDensity.LOW;
      case 2 -> TeacherPersona.TerminologyDensity.HIGH;
      default -> TeacherPersona.TerminologyDensity.MEDIUM;
    };
  }

  private static TeacherPersona.ExplanationPace snapshotToPace(TeacherSettings.Snapshot s) {
    return switch (Math.max(0, Math.min(2, s.paceIndex))) {
      case 0 -> TeacherPersona.ExplanationPace.BRIEF;
      case 2 -> TeacherPersona.ExplanationPace.DETAILED;
      default -> TeacherPersona.ExplanationPace.STANDARD;
    };
  }

  private static TeacherPersona.VariationDetail snapshotToVariation(TeacherSettings.Snapshot s) {
    return switch (Math.max(0, Math.min(2, s.variationIndex))) {
      case 0 -> TeacherPersona.VariationDetail.FEW;
      case 2 -> TeacherPersona.VariationDetail.MANY;
      default -> TeacherPersona.VariationDetail.MODERATE;
    };
  }

  private static String outputLanguage(Locale locale) {
    String language = locale == null ? "zh" : locale.getLanguage();
    String country = locale == null ? "" : locale.getCountry();
    if ("zh".equals(language)) {
      return "TW".equalsIgnoreCase(country) || "HK".equalsIgnoreCase(country)
          ? "繁體中文"
          : "简体中文";
    }
    if ("ja".equals(language)) {
      return "日本語";
    }
    if ("ko".equals(language)) {
      return "한국어";
    }
    if ("th".equals(language)) {
      return "ไทย";
    }
    return "English";
  }

  private static String format(double value) {
    synchronized (ONE_DECIMAL) {
      return ONE_DECIMAL.format(value);
    }
  }
}
