package featurecat.lizzie.rules;

import featurecat.lizzie.util.Utils;
import java.util.List;

/** Serializes one analysis slot, including named extensions after the six legacy header slots. */
final class SgfAnalysisPayloadWriter {
  private SgfAnalysisPayloadWriter() {}

  static String format(BoardData data, boolean secondary, String winrate, String playouts) {
    String engineName = secondary ? data.engineName2 : data.engineName;
    boolean kataData = secondary ? data.isKataData2 : data.isKataData;
    int headerSlots = secondary ? data.analysisHeaderSlots2 : data.analysisHeaderSlots;
    int rootVisits = secondary ? data.rootVisits2 : data.rootVisits;
    double scoreMean = secondary ? data.scoreMean2 : data.scoreMean;
    double scoreStdev = secondary ? data.scoreStdev2 : data.scoreStdev;
    double pda = secondary ? data.pda2 : data.pda;
    double wrn = secondary ? data.wrn2 : data.wrn;
    boolean wrnKnown = secondary ? data.wrnKnown2 : data.wrnKnown;
    String bestMoves = secondary ? data.bestMovesToString2() : data.bestMovesToString();
    List<Double> ownership = secondary ? data.estimateArray2 : data.estimateArray;
    StringBuilder payload = new StringBuilder();
    payload.append(engineName).append(" ").append(winrate).append(" ").append(playouts);
    if (kataData || rootVisits >= 0 || wrnKnown) {
      boolean explicitSlots = rootVisits < 0 && !wrnKnown && headerSlots > 0;
      boolean includeScoreStdev = !explicitSlots || headerSlots >= 5;
      boolean includePda =
          rootVisits >= 0
              || wrnKnown
              || (explicitSlots ? headerSlots >= 6 : Double.compare(pda, 0) != 0);
      payload.append(" ").append(scoreMean);
      if (includeScoreStdev) payload.append(" ").append(scoreStdev);
      if (includePda) payload.append(" ").append(pda);
    }
    if (rootVisits >= 0) payload.append(" rootVisits=").append(rootVisits);
    if (wrnKnown) payload.append(" wrn=").append(wrn);
    boolean hasMoves = !Utils.isBlank(bestMoves);
    boolean hasOwnership = ownership != null && !ownership.isEmpty();
    if (hasMoves || hasOwnership) {
      payload.append('\n');
      if (hasMoves) payload.append(bestMoves);
      if (hasOwnership) {
        payload.append(" ownership");
        for (Double value : ownership) payload.append(" ").append(value);
      }
    }
    return payload.toString();
  }
}
