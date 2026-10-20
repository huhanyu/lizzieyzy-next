package featurecat.lizzie.gui;

import featurecat.lizzie.analysis.PlayerStrengthEstimator;
import java.awt.FontMetrics;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Text of the player-strength dialog: ranks, rates, model names and the rank scale. */
final class StrengthText {
  private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");
  private static final String TOP_PRO_BAND = "\u4e00\u7ebf\u804c\u4e1a";
  private static final String PRO_BAND = "\u804c\u4e1a";

  private StrengthText() {}

  static String percent(double value) {
    return String.format(Locale.US, "%.0f%%", value * 100.0);
  }

  static String precisePercent(double value) {
    return String.format(Locale.US, "%.1f%%", value * 100.0);
  }

  /** The predicted rank of {@code report}, or "-" when it has no analysed moves. */
  static String rank(PlayerStrengthEstimator.SideReport report, ResourceBundle bundle) {
    return report == null || !report.hasSamples()
        ? "-"
        : PlayerStrengthRankFormatter.format(report.rankValue, bundle);
  }

  /**
   * Splits a rank such as "amateur 9.0 dan" into the text before the number, the number and the
   * text after it, so that the number can be set larger than its unit. Text without a number is all
   * number.
   */
  static String[] splitNumber(String text) {
    String value = text == null ? "" : text;
    Matcher matcher = NUMBER.matcher(value);
    if (!matcher.find()) {
      return new String[] {"", value, ""};
    }
    return new String[] {
      value.substring(0, matcher.start()).trim(),
      matcher.group(),
      value.substring(matcher.end()).trim()
    };
  }

  /** The localised name of an estimator strength band such as "3-4d" or "12d AI". */
  static String band(String strengthBand, ResourceBundle bundle) {
    if (strengthBand == null || strengthBand.isBlank() || "-".equals(strengthBand.trim())) {
      return "-";
    }
    String band = strengthBand.trim();
    if ("Beginner".equalsIgnoreCase(band)) {
      return bundle.getString("PlayerStrengthEstimate.rank.beginner");
    }
    if (band.contains("AI")) {
      return bundle.getString("PlayerStrengthEstimate.rank.ai");
    }
    if (band.contains(TOP_PRO_BAND)) {
      return bundle.getString("PlayerStrengthEstimate.rank.topPro");
    }
    if (band.contains(PRO_BAND)) {
      return bundle.getString("PlayerStrengthEstimate.rank.pro");
    }
    int kyu = band.indexOf('k');
    if (kyu > 0) {
      return rankRange(band.substring(0, kyu), false, bundle);
    }
    int dan = band.indexOf('d');
    if (dan > 0) {
      return rankRange(band.substring(0, dan), true, bundle);
    }
    return band;
  }

  private static String rankRange(String raw, boolean dan, ResourceBundle bundle) {
    String value = raw.trim();
    String[] parts = value.split("-");
    String unit = dan ? "dan" : "kyu";
    if (parts.length == 2) {
      return String.format(
          bundle.getString("PlayerStrengthEstimate.rank." + unit + "Range"),
          parts[0].trim(),
          parts[1].trim());
    }
    return String.format(bundle.getString("PlayerStrengthEstimate.rank." + unit + "Single"), value);
  }

  static String confidence(PlayerStrengthEstimator.Confidence confidence, ResourceBundle bundle) {
    switch (confidence) {
      case HIGH:
        return bundle.getString("PlayerStrengthEstimate.confidence.high");
      case MEDIUM:
        return bundle.getString("PlayerStrengthEstimate.confidence.medium");
      default:
        return bundle.getString("PlayerStrengthEstimate.confidence.low");
    }
  }

  static String modelName(PlayerStrengthEstimator.StrengthModel model) {
    if (model == null) {
      return "-";
    }
    String name = model.displayName();
    if ("xgboost20tun".equalsIgnoreCase(name)) {
      return "XGBoost 20TUN";
    }
    return name == null || name.isBlank() ? "-" : name.trim();
  }

  /** The everyday name of a model: "Standard" and "Human style" for the bundled ones. */
  static String modelTitle(PlayerStrengthEstimator.StrengthModel model, ResourceBundle bundle) {
    if (PlayerStrengthEstimator.StrengthModel.XGBOOST20TUN.equals(model)) {
      return bundle.getString("PlayerStrengthEstimate.model.standard");
    }
    if (model != null && model.isHumanSl40()) {
      return bundle.getString("PlayerStrengthEstimate.model.humanStyle");
    }
    return modelName(model);
  }

  /** One sentence on what a model looks at and what it costs. */
  static String modelDescription(
      PlayerStrengthEstimator.StrengthModel model, ResourceBundle bundle) {
    if (PlayerStrengthEstimator.StrengthModel.XGBOOST20TUN.equals(model)) {
      return bundle.getString("PlayerStrengthEstimate.model.standard.description");
    }
    if (model != null && model.isHumanSl40()) {
      return bundle.getString("PlayerStrengthEstimate.model.humanStyle.description");
    }
    return bundle.getString("PlayerStrengthEstimate.model.imported.description");
  }

  /** "Recommended" for the standard model, "Experimental" for human style, else empty. */
  static String modelBadge(PlayerStrengthEstimator.StrengthModel model, ResourceBundle bundle) {
    if (PlayerStrengthEstimator.StrengthModel.XGBOOST20TUN.equals(model)) {
      return bundle.getString("PlayerStrengthEstimate.model.recommended");
    }
    if (model != null && model.isHumanSl40()) {
      return bundle.getString("PlayerStrengthEstimate.model.experimental");
    }
    return "";
  }

  /** The rank-scale tier of {@code rankValue}: 0 amateur, 1 professional, 2 top pro, 3 AI. */
  static int tier(double rankValue) {
    if (!Double.isFinite(rankValue) || rankValue < 10.0) {
      return 0;
    }
    if (rankValue < 11.0) {
      return 1;
    }
    return rankValue < 12.0 ? 2 : 3;
  }

  /** Threshold and name of each rank-scale tier, from amateur to AI. */
  static String[][] scale(ResourceBundle bundle) {
    return new String[][] {
      {"<10", bundle.getString("PlayerStrengthEstimate.rank.scale.fox")},
      {"10+", bundle.getString("PlayerStrengthEstimate.rank.scale.pro")},
      {"11+", bundle.getString("PlayerStrengthEstimate.rank.scale.topPro")},
      {"12+", bundle.getString("PlayerStrengthEstimate.rank.scale.ai")}
    };
  }

  /** The rank of {@code report} followed by the whole rank scale, for screen readers. */
  static String scaleDescription(PlayerStrengthEstimator.SideReport report, ResourceBundle bundle) {
    StringBuilder text =
        new StringBuilder(bundle.getString("PlayerStrengthEstimate.rank.scale"))
            .append(": ")
            .append(rank(report, bundle));
    String[][] scale = scale(bundle);
    for (int i = scale.length - 1; i >= 0; i--) {
      text.append("; ").append(scale[i][0]).append(' ').append(scale[i][1]);
    }
    return text.toString();
  }

  /**
   * What the estimated rank means, then the rank of {@code report} and the whole rank scale, one
   * tier per line.
   */
  static String scaleTooltip(
      PlayerStrengthEstimator.SideReport report,
      ResourceBundle bundle,
      FontMetrics metrics,
      int width) {
    StringBuilder html =
        new StringBuilder("<html>")
            .append(wrapped(bundle.getString("PlayerStrengthEstimate.help.rank"), metrics, width))
            .append("<br><br>")
            .append(escape(bundle.getString("PlayerStrengthEstimate.rank.scale")))
            .append(": ")
            .append(escape(rank(report, bundle)));
    String[][] scale = scale(bundle);
    for (int i = scale.length - 1; i >= 0; i--) {
      html.append("<br>").append(escape(scale[i][0])).append(' ').append(escape(scale[i][1]));
    }
    return html.append("</html>").toString();
  }

  static String escape(String value) {
    return value == null
        ? ""
        : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  /**
   * A tooltip that shows {@code text} broken into lines about {@code width} wide, so that a long
   * explanation does not become one very wide line.
   */
  static String wrappedTooltip(String text, FontMetrics metrics, int width) {
    return "<html>" + wrapped(text, metrics, width) + "</html>";
  }

  /** {@code text} in lines no wider than {@code width}, escaped and joined by line breaks. */
  private static String wrapped(String text, FontMetrics metrics, int width) {
    StringBuilder html = new StringBuilder();
    List<String> lines = StrengthPaint.wrap(text, metrics, width, 6);
    for (int i = 0; i < lines.size(); i++) {
      html.append(i > 0 ? "<br>" : "").append(escape(lines.get(i)));
    }
    return html.toString();
  }
}
