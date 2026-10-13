package featurecat.lizzie.fox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure protocol state. Placement numbering never substitutes for MOVE/PASS history. */
public final class FoxGameState {
  public record Placement(int number, int x, int y, char color) {
    public String vertex(int size) {
      return "ABCDEFGHJKLMNOPQRSTUVWXYZ".charAt(x) + Integer.toString(size - y);
    }
  }
  public record Rules(int size, int mainSeconds, int byoSeconds, int periods,
      double komi, List<Placement> setup) {}
  public record Clock(int mainSeconds, int byoSeconds, int periods, long receivedNanos) {}
  public record Action(Placement placement, char color, int afterPlacement) {
    public boolean isPass() { return placement == null; }
    public String vertex(int size) { return isPass() ? "pass" : placement.vertex(size); }
  }
  public record Snapshot(long connection, long game, long revision, Rules rules,
      Clock clock, char ai, Character toPlay, List<Placement> placements,
      List<Action> actions, boolean initialized, boolean complete, boolean active,
      String statusKey) {
    public boolean searchable() { return initialized && complete && active; }
  }

  private long connection;
  private long game;
  private long revision;
  private Rules rules;
  private Clock clock;
  private char ai;
  private Character toPlay;
  private List<Placement> placements = List.of();
  private final List<Action> actions = new ArrayList<>();
  private boolean statusSeen;
  private boolean complete;
  private boolean active;
  private boolean disconnectedGame;
  private String statusKey = "Fox.waiting";

  public void connected() {
    connection++;
    reset();
    statusKey = "Fox.syncing";
  }

  public void disconnected() {
    disconnectedGame |= active;
    revision++;
    active = false;
    statusKey = "Fox.disconnected";
  }

  private void reset() {
    revision++;
    rules = null;
    clock = null;
    ai = 0;
    toPlay = null;
    placements = List.of();
    actions.clear();
    statusSeen = complete = active = false;
  }

  public Snapshot snapshot() {
    return new Snapshot(connection, game, revision, rules, clock, ai, toPlay,
        placements, List.copyOf(actions), rules != null && statusSeen && clock != null
        && toPlay != null, complete, active, statusKey);
  }

  public void reject() {
    complete = false;
    revision++;
    statusKey = "Fox.protocolError";
  }

  /** Each message validates its complete payload before mutating state. */
  public void receive(String body, long now) {
    String[] f = body.split(",", -1);
    switch (f[0]) {
      case "FARULE" -> {
        require(f.length >= 6);
        int size = integer(f[1], 2, 25);
        int main = integer(f[2], 0, Integer.MAX_VALUE);
        int byo = integer(f[3], 0, Integer.MAX_VALUE);
        int periods = integer(f[4], 0, Integer.MAX_VALUE);
        int komi = integer(f[5], -100000, 100000);
        List<Placement> setup = new ArrayList<>();
        Set<Integer> occupied = new HashSet<>();
        for (int i = 6; i < f.length; i++) {
          Placement p = placement(f[i], size, true);
          require(p.color == 'B' && occupied.add(p.y * size + p.x));
          setup.add(p);
        }
        reset();
        game++;
        rules = new Rules(size, main, byo, periods, komi / 100.0, List.copyOf(setup));
        statusKey = "Fox.syncing";
      }
      case "FASTATUS" -> {
        if (f.length == 2 && f[1].equals("0")) {
          revision++;
          active = false;
          disconnectedGame = false;
          statusKey = "Fox.terminal";
          return;
        }
        require(rules != null && f.length >= 3 && f[1].equals("1"));
        char color = color(f[2]);
        require(ai == 0 || ai == color);
        List<Placement> incoming = new ArrayList<>();
        for (int i = 3; i < f.length; i++) {
          Placement p = placement(f[i], rules.size, false);
          if (p == null) { require(f.length == 4); break; }
          require(p.number == incoming.size() + 1);
          incoming.add(p);
        }
        if (!statusSeen) complete = incoming.isEmpty() && !disconnectedGame;
        if (statusSeen && !incoming.equals(placements)) gap();
        if (!incoming.equals(placements)) revision++;
        placements = List.copyOf(incoming);
        ai = color;
        statusSeen = active = true;
        if (!complete) statusKey = "Fox.incomplete";
      }
      case "FAMOVE", "FASKIP" -> {
        boolean pass = f[0].equals("FASKIP");
        require(rules != null && statusSeen && active && f.length == (pass ? 3 : 4));
        require(f[1].equals("0") || f[1].equals("1"));
        require(color(f[2]) == ai);
        char next = f[1].equals("1") ? ai : opposite(ai);
        if (pass) {
          char passer = opposite(next);
          Action action = new Action(null, passer, placements.size());
          if (toPlay != null && toPlay == next && !actions.isEmpty()
              && actions.get(actions.size() - 1).equals(action)) return;
          require(toPlay != null && toPlay != next);
          actions.add(action);
          revision++;
        } else {
          Placement move = placement(f[3], rules.size, false);
          if (move == null) {
            require(placements.isEmpty());
            if (toPlay != null && toPlay != next) gap();
            if (actions.isEmpty() && next != (rules.setup.isEmpty() ? 'B' : 'W')) gap();
          } else if (!placements.isEmpty() && move.equals(placements.get(placements.size() - 1))) {
            if (toPlay != null && toPlay != next) gap();
          } else {
            require(move.number == placements.size() + 1);
            if (toPlay == null || toPlay != move.color || next == move.color) gap();
            List<Placement> updated = new ArrayList<>(placements);
            updated.add(move);
            placements = List.copyOf(updated);
            actions.add(new Action(move, move.color, move.number));
            revision++;
          }
        }
        if (toPlay == null || toPlay != next) revision++;
        toPlay = next;
        if (complete) statusKey = next == ai ? "Fox.ourTurn" : "Fox.opponentTurn";
      }
      case "FATIMELEFT" -> {
        require(f.length == 4 && rules != null);
        Clock next = new Clock(integer(f[1], 0, Integer.MAX_VALUE),
            integer(f[2], 0, Integer.MAX_VALUE), integer(f[3], 0, Integer.MAX_VALUE), now);
        require(next.byoSeconds <= rules.byoSeconds && next.periods <= rules.periods);
        clock = next;
      }
      case "FARESULT" -> {
        require(f.length == 2 && !f[1].isEmpty());
        revision++;
        active = false;
        disconnectedGame = false;
        statusKey = "Fox.terminal";
      }
      case "FASCORE" -> {
        revision++;
        complete = false;
        statusKey = "Fox.officialAction";
      }
      default -> throw new IllegalArgumentException("Unsupported Fox message");
    }
  }

  private void gap() { complete = false; revision++; statusKey = "Fox.incomplete"; }
  private static char opposite(char color) { return color == 'B' ? 'W' : 'B'; }
  private static char color(String value) {
    require(value.equals("B") || value.equals("W"));
    return value.charAt(0);
  }
  private static int integer(String value, int min, int max) {
    require(value.matches("-?[0-9]+"));
    int result = Integer.parseInt(value);
    require(result >= min && result <= max);
    return result;
  }
  private static Placement placement(String value, int size, boolean setup) {
    if (!setup && value.equals("0^0^0^N")) return null;
    String[] p = value.split("\\^", -1);
    require(p.length == 4);
    return new Placement(integer(p[0], setup ? 0 : 1, Integer.MAX_VALUE),
        integer(p[1], 0, size - 1), integer(p[2], 0, size - 1), color(p[3]));
  }
  private static void require(boolean condition) {
    if (!condition) throw new IllegalArgumentException("Invalid Fox message parameters");
  }
}
